package com.netralabs.wcag.contrast;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceCmyk;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Point;
import com.itextpdf.kernel.geom.Subpath;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.canvas.CanvasTag;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.PathRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
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
 * <p>Background detection is a simple painters-model approximation: the rule
 * maintains a per-page paint log of filled rectangles (in draw order) with their
 * fill colors. For each text-show event, the topmost previously-painted rectangle
 * that <em>contains</em> the text bbox is used as the background. If no covering
 * rectangle is found the background defaults to white (page paper). Images are
 * not currently mined for pixel colours; text over images gets the last covering
 * filled rect (or white).
 *
 * <p>Text is skipped from the tally when:
 * <ul>
 *   <li>Rendering mode is 3 (invisible text — Tr 3), or fill can't be resolved.</li>
 *   <li>Text sits inside an {@code /Artifact} BDC that carries a {@code /Type}
 *       property (classified decorative artifact — same exclusion as
 *       ValidateUnicodeMapping and ContentListener).</li>
 * </ul>
 *
 * <p>PAC-observed denominators on our corpus:
 * <table>
 *   <caption>PAC 1.4.3 Contrast of text counts</caption>
 *   <tr><th>PDF</th><th>P</th><th>E</th><th>Total</th></tr>
 *   <tr><td>Filled_Graduate</td><td>2593</td><td>392</td><td>3756 total text</td></tr>
 *   <tr><td>CalSAWS</td><td>11205</td><td>0</td><td>11749</td></tr>
 *   <tr><td>Complex_Presentation_Sample</td><td>182</td><td>146</td><td>330</td></tr>
 * </table>
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

    /** Paint record for the background painter model. Either a filled rectangle
     * (constant colour, {@code img} null) or an image (pixel-sampled per lookup). */
    private static final class Paint {
        final double minX, minY, maxX, maxY;
        /** Non-null for filled rectangles; the whole area shares this colour. */
        final double[] rgb;
        /** Non-null for images; the width/height in pixels for coordinate mapping. */
        final BufferedImage img;
        final Matrix ctm;

        Paint(double minX, double minY, double maxX, double maxY, double[] rgb) {
            this(minX, minY, maxX, maxY, rgb, null, null);
        }

        Paint(double minX, double minY, double maxX, double maxY, BufferedImage img, Matrix ctm) {
            this(minX, minY, maxX, maxY, null, img, ctm);
        }

        private Paint(double minX, double minY, double maxX, double maxY,
                      double[] rgb, BufferedImage img, Matrix ctm) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
            this.rgb = rgb; this.img = img; this.ctm = ctm;
        }

        boolean covers(double x0, double y0, double x1, double y1) {
            return minX <= x0 && minY <= y0 && maxX >= x1 && maxY >= y1;
        }

        /** Sample the paint's colour at the text bbox centre. */
        double[] sample(double cx, double cy) {
            if (rgb != null) return rgb;
            // Map (cx, cy) from user-space to image pixel coords using inverse CTM.
            // Image occupies unit square [0,1]x[0,1] transformed by CTM.
            // Instead of full inverse, approximate via normalized position in the
            // image's bounding rect — valid for axis-aligned images (no rotation).
            double u = (cx - minX) / (maxX - minX);
            double v = (cy - minY) / (maxY - minY);
            u = clamp01(u);
            v = clamp01(v);
            int px = (int) Math.round(u * (img.getWidth() - 1));
            // Image origin is top-left; PDF origin bottom-left. Flip v.
            int py = (int) Math.round((1.0 - v) * (img.getHeight() - 1));
            int argb;
            try {
                argb = img.getRGB(px, py);
            } catch (Exception e) {
                return new double[]{1, 1, 1};
            }
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            return new double[]{r / 255.0, g / 255.0, b / 255.0};
        }
    }

    private static final class ContrastListener implements IEventListener {
        private final int pageNum;
        private final List<FindingDTO> out;
        private final List<Paint> paintLog = new ArrayList<>();
        /** Cache of decoded BufferedImages keyed by their PdfImageXObject identity — an
         *  image referenced N times decodes once. */
        private final Map<PdfImageXObject, BufferedImage> imageCache = new HashMap<>();

        ContrastListener(int pageNum, List<FindingDTO> out) {
            this.pageNum = pageNum;
            this.out = out;
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return EnumSet.of(EventType.RENDER_TEXT, EventType.RENDER_PATH, EventType.RENDER_IMAGE);
        }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_PATH) {
                onPath((PathRenderInfo) data);
                return;
            }
            if (type == EventType.RENDER_IMAGE) {
                onImage((ImageRenderInfo) data);
                return;
            }
            if (type == EventType.RENDER_TEXT) {
                onText((TextRenderInfo) data);
            }
        }

        private void onPath(PathRenderInfo pi) {
            // Only record filled paths (F/f/B/b operations include FILL bit).
            if ((pi.getOperation() & PathRenderInfo.FILL) == 0) return;
            double[] bbox = pathBbox(pi);
            if (bbox == null) return;
            double[] rgb = toRgb(pi.getFillColor());
            if (rgb == null) return;
            paintLog.add(new Paint(bbox[0], bbox[1], bbox[2], bbox[3], rgb));
        }

        private void onImage(ImageRenderInfo ii) {
            Matrix ctm = ii.getImageCtm();
            if (ctm == null) return;
            double[] bbox = imageBbox(ctm);
            if (bbox == null) return;
            PdfImageXObject img = ii.getImage();
            if (img == null) return;
            BufferedImage bi = imageCache.computeIfAbsent(img, k -> {
                try { return k.getBufferedImage(); } catch (Exception e) { return null; }
            });
            if (bi == null) return;
            paintLog.add(new Paint(bbox[0], bbox[1], bbox[2], bbox[3], bi, ctm));
        }

        private void onText(TextRenderInfo tri) {
            if (tri.getTextRenderMode() == 3) return;
            if (isTypedArtifact(tri)) return;

            Color fill = tri.getFillColor();
            if (fill == null) return;
            double[] fillRgb = toRgb(fill);
            if (fillRgb == null) return;

            double[] textBox = textBbox(tri);
            if (textBox == null) return;
            double[] bgRgb = backgroundAt(textBox);

            double ratio = contrastRatio(fillRgb, bgRgb);
            double threshold = isLargeText(tri) ? THRESHOLD_LARGE : THRESHOLD_REGULAR;

            if (ratio >= threshold) {
                out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, null));
            } else {
                out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, null,
                        String.format("Text contrast %.2f:1 is below WCAG minimum %.1f:1", ratio, threshold)));
            }
        }

        /** Walk the paint log newest-to-oldest and return the topmost covering fill; white if none. */
        private double[] backgroundAt(double[] textBox) {
            double cx = (textBox[0] + textBox[2]) / 2.0;
            double cy = (textBox[1] + textBox[3]) / 2.0;
            for (int i = paintLog.size() - 1; i >= 0; i--) {
                Paint p = paintLog.get(i);
                if (p.covers(textBox[0], textBox[1], textBox[2], textBox[3])) return p.sample(cx, cy);
            }
            return new double[]{1.0, 1.0, 1.0};
        }
    }

    /** Bbox of an image transformed by its CTM. The image occupies the unit square in
     * user-space via the transformation [[a b 0][c d 0][e f 1]]. */
    private static double[] imageBbox(Matrix ctm) {
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        double[] xs = new double[4];
        double[] ys = new double[4];
        for (int i = 0; i < 4; i++) {
            Vector v = new Vector(corners[i][0], corners[i][1], 1).cross(ctm);
            xs[i] = v.get(0);
            ys[i] = v.get(1);
        }
        return bounds(xs, ys);
    }

    /** Text bbox from the render info's baseline/ascent/descent segments. */
    private static double[] textBbox(TextRenderInfo tri) {
        LineSegment baseline = tri.getBaseline();
        LineSegment ascent = tri.getAscentLine();
        LineSegment descent = tri.getDescentLine();
        double[] xs = {
                baseline.getStartPoint().get(0), baseline.getEndPoint().get(0),
                ascent.getStartPoint().get(0), ascent.getEndPoint().get(0),
                descent.getStartPoint().get(0), descent.getEndPoint().get(0)
        };
        double[] ys = {
                baseline.getStartPoint().get(1), baseline.getEndPoint().get(1),
                ascent.getStartPoint().get(1), ascent.getEndPoint().get(1),
                descent.getStartPoint().get(1), descent.getEndPoint().get(1)
        };
        return bounds(xs, ys);
    }

    /** Path bbox from its subpaths (piecewise-linear approximation of curves). */
    private static double[] pathBbox(PathRenderInfo pi) {
        if (pi.getPath() == null) return null;
        List<Subpath> subs = pi.getPath().getSubpaths();
        if (subs == null || subs.isEmpty()) return null;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (Subpath sp : subs) {
            try {
                for (Point p : sp.getPiecewiseLinearApproximation()) {
                    double x = p.getX();
                    double y = p.getY();
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    any = true;
                }
            } catch (Exception ignored) {}
        }
        if (!any) return null;
        return new double[]{minX, minY, maxX, maxY};
    }

    private static double[] bounds(double[] xs, double[] ys) {
        double minX = xs[0], maxX = xs[0], minY = ys[0], maxY = ys[0];
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] < minX) minX = xs[i];
            if (xs[i] > maxX) maxX = xs[i];
            if (ys[i] < minY) minY = ys[i];
            if (ys[i] > maxY) maxY = ys[i];
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * True iff the innermost marked-content tag is an {@code /Artifact} BDC that
     * carries an explicit {@code /Type} property.
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
     * safely map (Separation, DeviceN, ICCBased with unusual profiles).
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
            // Naive CMYK -> RGB conversion (assumes uncalibrated CMYK).
            return new double[]{(1 - C) * (1 - K), (1 - M) * (1 - K), (1 - Y) * (1 - K)};
        }
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

    /** WCAG contrast ratio (L1 + 0.05) / (L2 + 0.05), L1 the lighter of the two. */
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
        PdfName base = tri.getFont().getPdfObject().getAsName(PdfName.BaseFont);
        if (base == null) return false;
        String name = base.getValue().toLowerCase(java.util.Locale.ROOT);
        return name.contains("bold") || name.contains("heavy") || name.contains("black");
    }
}