package com.netralabs.wcag.contrast;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceCmyk;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.canvas.CanvasTag;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * WCAG 2.2 SC 1.4.3 Contrast (Minimum): text foreground vs background luminance
 * contrast ratio must be at least 4.5:1 for regular text and 3:1 for large text
 * (>= 18pt, or >= 14pt bold).
 *
 * <p>This first-cut implementation assumes a **white page background** — sufficient
 * for the common case of black-on-white or dark-text-on-white documents but will
 * miss text painted over colored fills or images. Refinement via a per-page
 * paint log (rectangles + images with their fill colors) is a follow-up.
 *
 * <p>Text is skipped from the tally when:
 * <ul>
 *   <li>Rendering mode is 3 (invisible text — Tr 3), or fill is transparent.</li>
 *   <li>Font size can't be determined.</li>
 *   <li>Text sits inside an {@code /Artifact} BDC that carries a {@code /Type}
 *       property (classified decorative artifact — same exclusion as
 *       ValidateUnicodeMapping and ContentListener).</li>
 * </ul>
 *
 * <p>PAC-observed denominators on our corpus:
 * <table>
 *   <caption>PAC 1.4.3 Contrast of text counts</caption>
 *   <tr><th>PDF</th><th>P</th><th>E</th><th>Total events</th></tr>
 *   <tr><td>Filled_Graduate</td><td>2593</td><td>392</td><td>3756</td></tr>
 *   <tr><td>CalSAWS</td><td>11205</td><td>0</td><td>11749</td></tr>
 *   <tr><td>Complex_Presentation_Sample</td><td>182</td><td>146</td><td>330</td></tr>
 * </table>
 * PAC excludes 771/544/2 respectively — likely appearance-stream text or another
 * scope-specific filter to tune later.
 */
public class ValidateContrastOfText implements Rule {

    /** WCAG minimum contrast ratio for regular-size text. */
    private static final double THRESHOLD_REGULAR = 4.5;
    /** WCAG minimum contrast ratio for large text (>= 18pt, or >= 14pt bold). */
    private static final double THRESHOLD_LARGE = 3.0;
    /** Large-text threshold in points. */
    private static final double LARGE_TEXT_POINTS = 18.0;
    /** Bold large-text threshold in points. */
    private static final double BOLD_LARGE_TEXT_POINTS = 14.0;

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            PdfCanvasProcessor proc = new PdfCanvasProcessor(new ContrastListener(page, out));
            proc.processPageContent(pdf.getPage(page));
        }
        return out;
    }

    private static final class ContrastListener implements IEventListener {
        private final int pageNum;
        private final List<FindingDTO> out;

        ContrastListener(int pageNum, List<FindingDTO> out) {
            this.pageNum = pageNum;
            this.out = out;
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return EnumSet.of(EventType.RENDER_TEXT);
        }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type != EventType.RENDER_TEXT) return;
            TextRenderInfo tri = (TextRenderInfo) data;

            // Skip invisible text (rendering mode 3).
            if (tri.getTextRenderMode() == 3) return;
            // Skip text inside a "classified" Artifact BDC — same exclusion as
            // ValidateUnicodeMapping / ContentListener.
            if (isTypedArtifact(tri)) return;

            Color fill = tri.getFillColor();
            if (fill == null) return;
            double[] fillRgb = toRgb(fill);
            if (fillRgb == null) return;

            // Background assumed white for this first-cut implementation.
            double[] bgRgb = {1.0, 1.0, 1.0};

            double ratio = contrastRatio(fillRgb, bgRgb);
            double threshold = isLargeText(tri) ? THRESHOLD_LARGE : THRESHOLD_REGULAR;

            if (ratio >= threshold) {
                out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, null));
            } else {
                out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, null,
                        String.format("Text contrast %.2f:1 is below WCAG minimum %.1f:1", ratio, threshold)));
            }
        }
    }

    /**
     * True iff the innermost marked-content tag is an {@code /Artifact} BDC that
     * carries an explicit {@code /Type} property. Same rule as
     * {@code ValidateUnicodeMapping.isTypedArtifact}.
     */
    private static boolean isTypedArtifact(TextRenderInfo tri) {
        List<CanvasTag> h = tri.getCanvasTagHierarchy();
        if (h == null || h.isEmpty()) return false;
        CanvasTag innermost = h.get(h.size() - 1);
        PdfName role = innermost.getRole();
        if (role == null || !"Artifact".equals(role.getValue())) return false;
        return innermost.getProperties() != null
                && innermost.getProperties().get(PdfName.Type) != null;
    }

    /**
     * Convert an iText {@link Color} to a linear RGB triple in [0, 1]. Handles
     * DeviceGray / DeviceRGB / DeviceCMYK. Returns null for color spaces we can't
     * safely map (Separation, DeviceN, ICCBased with unusual profiles) — those
     * text-shows are simply skipped from the tally.
     */
    private static double[] toRgb(Color c) {
        if (c instanceof DeviceRgb) {
            float[] v = c.getColorValue();
            return new double[]{clamp01(v[0]), clamp01(v[1]), clamp01(v[2])};
        }
        if (c instanceof DeviceGray) {
            float g = c.getColorValue()[0];
            double x = clamp01(g);
            return new double[]{x, x, x};
        }
        if (c instanceof DeviceCmyk) {
            float[] v = c.getColorValue();
            double C = clamp01(v[0]);
            double M = clamp01(v[1]);
            double Y = clamp01(v[2]);
            double K = clamp01(v[3]);
            // Naive CMYK → RGB conversion (assumes uncalibrated CMYK).
            return new double[]{(1 - C) * (1 - K), (1 - M) * (1 - K), (1 - Y) * (1 - K)};
        }
        // Fallback: try ColorConstants matches.
        if (c == ColorConstants.BLACK) return new double[]{0, 0, 0};
        if (c == ColorConstants.WHITE) return new double[]{1, 1, 1};
        return null;
    }

    private static double clamp01(double x) {
        if (x < 0) return 0;
        if (x > 1) return 1;
        return x;
    }

    /** WCAG relative luminance for an sRGB colour in [0, 1]. */
    private static double relativeLuminance(double[] rgb) {
        double r = channelLuminance(rgb[0]);
        double g = channelLuminance(rgb[1]);
        double b = channelLuminance(rgb[2]);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channelLuminance(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** WCAG contrast ratio (L1 + 0.05) / (L2 + 0.05), L1 lighter of the two. */
    private static double contrastRatio(double[] a, double[] b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /**
     * WCAG large-text threshold: >= 18pt, or >= 14pt bold. Detects bold via the
     * font's BaseFont name (contains "Bold" or "Heavy"/"Black").
     */
    private static boolean isLargeText(TextRenderInfo tri) {
        float pts = tri.getFontSize();
        if (pts <= 0) return false;
        if (pts >= LARGE_TEXT_POINTS) return true;
        if (pts < BOLD_LARGE_TEXT_POINTS) return false;
        if (tri.getFont() == null || tri.getFont().getPdfObject() == null) return false;
        PdfName base = tri.getFont().getPdfObject().getAsName(com.itextpdf.kernel.pdf.PdfName.BaseFont);
        if (base == null) return false;
        String name = base.getValue().toLowerCase(java.util.Locale.ROOT);
        return name.contains("bold") || name.contains("heavy") || name.contains("black");
    }
}