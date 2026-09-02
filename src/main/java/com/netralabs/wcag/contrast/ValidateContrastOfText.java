package com.netralabs.wcag.contrast;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceCmyk;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.colors.IccBased;
import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Point;
import com.itextpdf.kernel.geom.Subpath;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.canvas.CanvasTag;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.IContentOperator;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.PathRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.itextpdf.kernel.pdf.colorspace.PdfCieBasedCs;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;

import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
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

    /** Content-stream text-showing operators that produce visible glyphs. */
    private static final String[] TEXT_SHOWING_OPS = {"Tj", "TJ", "'", "\""};

    /**
     * Threshold for the emission-granularity heuristic. Tj/TJ invocations that
     * fire at most this many qualifying RENDER_TEXT events emit one finding per
     * glyph; longer ones fall back to a single union-bbox finding.
     *
     * <p>Default {@code Integer.MAX_VALUE} — always emit per-glyph. Verified against
     * PAC on the current corpus: fisheries matches exactly (7974 P, 0 E), Metro
     * moves from 6513 → 15361 P (vs PAC 14596), CalSAWS moves from 9097 → 11553 P
     * (vs PAC 11059), 375 moves from 519 → 1135 P (vs PAC 1233). Sum of absolute
     * deltas across the corpus: ~16.6k → ~3.8k.
     *
     * <p>Overridable via {@code -Dpac.contrast.perGlyphMax=<int>} — lower values
     * batch longer text-shows into a single union-bbox finding, which historically
     * matched PAC on typewriter-style PDFs (OP_AoD) where whole words came through
     * as a single Tj. Retain the knob in case a future corpus needs it.
     */
    private static final int PER_GLYPH_MAX_BATCH =
            Integer.getInteger("pac.contrast.perGlyphMax", Integer.MAX_VALUE);

    /**
     * Background-overlap A/B knob. Controls how {@code backgroundAt} selects a
     * covering paint from the per-page paint log:
     * <ul>
     *   <li>{@code covers} (default) — strict {@code Paint.covers()} bbox
     *       containment. A paint qualifies only if it fully contains the text
     *       bbox. Misses banner rectangles that stop at the text's ascent line.</li>
     *   <li>{@code intersects} — {@code Paint.intersects()} with dominant-area
     *       tiebreak. Iterate newest-to-oldest; among paints whose intersection
     *       with the text bbox exceeds {@link #INTERSECT_DOMINANCE} of the text
     *       area, return the first one encountered (most-recently drawn wins,
     *       matching painters-model layering).</li>
     * </ul>
     * Set via {@code -Dpac.contrast.bgOverlap=intersects}. Under investigation
     * against Metro (+2288 E) / CalSAWS (-145 E) bg-detection direction; expected
     * to swing per-file — not a universal fix.
     */
    private static final String BG_OVERLAP =
            System.getProperty("pac.contrast.bgOverlap", "covers");

    /** Minimum intersection-area-over-text-area ratio for a paint to qualify as
     *  the dominant background under {@code intersects} mode. 0.5 = paint must
     *  cover at least half the text bbox. Chosen to filter out grazing rectangles
     *  that clip only the descender/ascender of a glyph. */
    private static final double INTERSECT_DOMINANCE = 0.5;

    /**
     * Emit an ERROR finding for text whose fill colour is in a colour space we
     * can't convert to RGB (Separation, DeviceN, Pattern, Lab, Indexed). PAC's
     * observed behaviour on CalSAWS: the "CalSAWS Consortium" header sits in
     * DeviceGray + a Separation-tinted overlay, and PAC flags all 146 text
     * events at that location as "Text with insufficient contrast" — verified
     * against screenshot 145243.png. The colour is not measurable, and PAC
     * defaults unmeasurable-colour to fail. We now match that behaviour.
     *
     * <p>Widget-scoped and artifact-scoped events are still dropped via the
     * usual filters. Set {@code -Dpac.contrast.unresolvedColorAsError=false}
     * to restore the previous silent-skip behaviour.
     */
    private static final boolean UNRESOLVED_COLOR_AS_ERROR = Boolean.parseBoolean(
            System.getProperty("pac.contrast.unresolvedColorAsError", "true"));

    /**
     * Skip text whose innermost marked-content tag is any {@code /Artifact} BDC
     * (typed or untyped). PAC treats all Artifact-scoped text as decorative for
     * 1.4.3 — the user isn't expected to read it, so contrast doesn't apply.
     * Discovered 2026-09-02 via corpus diagnostic: on OP_AoD the current
     * typed-only filter counts 8190 bare {@code Artifact BMC ... EMC} events;
     * dropping them lands total = 1247 vs PAC 1246 (Δ=1). Sum-of-|Δ| across
     * the 8-file corpus drops ~9637 → ~1500.
     *
     * <p>Disable via {@code -Dpac.contrast.skipUntypedArtifacts=false} to
     * restore the previous typed-only behaviour. Default on.
     */
    private static final boolean SKIP_UNTYPED_ARTIFACTS = Boolean.parseBoolean(
            System.getProperty("pac.contrast.skipUntypedArtifacts", "true"));

    /**
     * PAC compatibility mode. When {@code true}, background detection is disabled
     * and every text-show is measured against pure white ({@code rgb(1,1,1)}).
     * <p>
     * Motivation: PAC's 1.4.3 tally on documents with filled-path banner backgrounds
     * (e.g. white heading text on a dark-blue rectangle) reports insufficient
     * contrast because PAC evidently doesn't detect the covering fill and defaults
     * to paper. Our spec-correct detection resolves the true background and passes
     * such text — diverging from PAC. Set this flag to mirror PAC's numbers at the
     * cost of WCAG spec compliance.
     * <p>
     * Enabled via {@code -Dpac.contrast.compat=true}.
     */
    private static final boolean PAC_COMPAT = Boolean.getBoolean("pac.contrast.compat");

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            List<double[]> widgetRects = collectWidgetRects(pdf.getPage(page));
            // In PAC compat mode we gate white-text failures on TH-cell membership.
            // PAC only surfaces contrast fails for text inside a <TH> region (headers
            // drawn as artifacts over coloured banners); it skips the same text when
            // it falls inside <TD> (data cell), which is why our earlier compat pass
            // still over-flagged on tables with white-text status pills.
            List<double[]> thBboxes = PAC_COMPAT
                    ? collectThRegions(pdf, page)
                    : java.util.Collections.emptyList();
            ContrastListener listener = new ContrastListener(page, out, widgetRects, thBboxes);
            PdfCanvasProcessor proc = new PdfCanvasProcessor(listener);
            // Wrap each text-showing operator so the per-glyph RENDER_TEXT events
            // fired by iText inside a single Tj/TJ/'/" invocation are collapsed
            // into one finding — matching PAC's per-operator granularity.
            for (String op : TEXT_SHOWING_OPS) wrapTextOp(proc, op, listener);
            proc.processPageContent(pdf.getPage(page));
        }
        return out;
    }

    /**
     * Register a wrapper that turns {@link ContrastListener} into a "text
     * operator" scope: begin before the previous handler runs, end after.
     * During the scope every RENDER_TEXT event is buffered; on {@code endOp}
     * they are unioned into one finding.
     */
    private static void wrapTextOp(PdfCanvasProcessor proc, String op, ContrastListener listener) {
        IContentOperator[] prev = new IContentOperator[1];
        IContentOperator wrapper = (processor, operator, operands) -> {
            listener.beginOp();
            try {
                if (prev[0] != null) prev[0].invoke(processor, operator, operands);
            } finally {
                listener.endOp();
            }
        };
        prev[0] = proc.registerContentOperator(op, wrapper);
    }

    /** Rectangles of every Widget annotation on the page (from {@code /Rect}). Used to
     *  skip text painted at those locations — form field labels/values are drawn by
     *  the viewer from the widget's appearance stream and PAC excludes them from the
     *  1.4.3 tally to avoid double-counting text that appears twice on-screen. */
    private static List<double[]> collectWidgetRects(com.itextpdf.kernel.pdf.PdfPage page) {
        List<double[]> rects = new ArrayList<>();
        com.itextpdf.kernel.pdf.PdfArray annots = page.getPdfObject()
                .getAsArray(PdfName.Annots);
        if (annots == null) return rects;
        for (int i = 0; i < annots.size(); i++) {
            com.itextpdf.kernel.pdf.PdfObject o = annots.get(i);
            if (!(o instanceof com.itextpdf.kernel.pdf.PdfDictionary d)) continue;
            if (!PdfName.Widget.equals(d.getAsName(PdfName.Subtype))) continue;
            com.itextpdf.kernel.pdf.PdfArray r = d.getAsArray(PdfName.Rect);
            if (r == null || r.size() != 4) continue;
            double x0 = r.getAsNumber(0).doubleValue();
            double y0 = r.getAsNumber(1).doubleValue();
            double x1 = r.getAsNumber(2).doubleValue();
            double y1 = r.getAsNumber(3).doubleValue();
            rects.add(new double[]{Math.min(x0, x1), Math.min(y0, y1),
                    Math.max(x0, x1), Math.max(y0, y1)});
        }
        return rects;
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

        /** True iff any part of this paint overlaps the given bbox. Looser than
         *  {@link #covers(double, double, double, double)} — used by the
         *  {@code intersects} bg-overlap A/B mode to include banner rectangles
         *  that stop short of the text's ascent/descent. */
        boolean intersects(double x0, double y0, double x1, double y1) {
            if (maxX < x0 || minX > x1) return false;
            if (maxY < y0 || minY > y1) return false;
            return true;
        }

        /** Area of the axis-aligned intersection between this paint and the
         *  given bbox. Returns 0 when disjoint. Used to pick the dominant paint
         *  when multiple intersect the text bbox. */
        double intersectionArea(double x0, double y0, double x1, double y1) {
            double w = Math.min(maxX, x1) - Math.max(minX, x0);
            double h = Math.min(maxY, y1) - Math.max(minY, y0);
            if (w <= 0 || h <= 0) return 0.0;
            return w * h;
        }

        /** Sample the paint's colour at a single point. */
        double[] sample(double x, double y) {
            if (rgb != null) return rgb;
            double u = clamp01((x - minX) / (maxX - minX));
            double v = clamp01((y - minY) / (maxY - minY));
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

        /**
         * For solid-colour paints returns the single colour. For images returns the
         * pixel at the text bbox centre — a robust single-sample approximation that
         * beats multi-point worst-case sampling on our corpus (which picked up
         * unrelated dark pixels at bbox edges).
         *
         * <p>The {@code textRgb} argument is accepted for future refinements (e.g.
         * per-glyph sampling using the actual rendered character mask) but is
         * currently unused.
         */
        double[] worstAgainst(double x0, double y0, double x1, double y1, double[] textRgb) {
            if (rgb != null) return rgb;
            double cx = (x0 + x1) / 2.0;
            double cy = (y0 + y1) / 2.0;
            return sample(cx, cy);
        }
    }

    private static final class ContrastListener implements IEventListener {
        private final int pageNum;
        private final List<FindingDTO> out;
        private final List<Paint> paintLog = new ArrayList<>();
        /** Cache of decoded BufferedImages keyed by their PdfImageXObject identity — an
         *  image referenced N times decodes once. */
        private final Map<PdfImageXObject, BufferedImage> imageCache = new HashMap<>();
        private final List<double[]> widgetRects;
        /** TH-cell bounding boxes for this page (PAC compat only). Under compat mode
         *  we skip a candidate contrast failure when its text bbox does not intersect
         *  any of these rects — matching PAC's behaviour of restricting 1.4.3 to
         *  header-role text. Empty outside compat mode. */
        private final List<double[]> thBboxes;

        /** Depth of nested text-showing operator wrappers. iText's default {@code TJ}
         *  handler invokes {@code Tj} internally for each string element in the array,
         *  so an outer TJ wrapper opens depth 1 and each nested Tj bumps it to 2.
         *  Only the outermost begin clears the buffer and the outermost end emits. */
        private int opDepth;
        /** Glyph events accumulated inside the current operator. Extracted at
         *  buffer time because iText invalidates the {@link TextRenderInfo}
         *  graphics state as soon as {@code eventOccurred} returns. */
        private final List<GlyphEvent> opBuffer = new ArrayList<>();

        /** Immutable snapshot of the fields we need from a {@link TextRenderInfo}. */
        private record GlyphEvent(double[] fillRgb, double[] bbox, int renderMode,
                                  float fontSize, String fontName, boolean typedArtifact,
                                  boolean anyArtifact, String text) {}

        ContrastListener(int pageNum, List<FindingDTO> out, List<double[]> widgetRects,
                         List<double[]> thBboxes) {
            this.pageNum = pageNum;
            this.out = out;
            this.widgetRects = widgetRects;
            this.thBboxes = thBboxes;
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
                GlyphEvent g = snapshot((TextRenderInfo) data);
                if (g == null) return;
                if (opDepth > 0) {
                    opBuffer.add(g);
                } else {
                    // Text outside a wrapped operator is rare (unusual content
                    // streams); fall back to per-event evaluation so it isn't lost.
                    emitFinding(List.of(g));
                }
            }
        }

        /** Snapshot the fields we need before iText invalidates the graphics state. */
        private static GlyphEvent snapshot(TextRenderInfo tri) {
            Color fill = tri.getFillColor();
            double[] fillRgb = fill != null ? toRgb(fill) : null;
            double[] bbox = textBbox(tri);
            String fontName = null;
            if (tri.getFont() != null && tri.getFont().getPdfObject() != null) {
                PdfName base = tri.getFont().getPdfObject().getAsName(PdfName.BaseFont);
                if (base != null) fontName = base.getValue();
            }
            return new GlyphEvent(
                    fillRgb, bbox, tri.getTextRenderMode(),
                    tri.getFontSize(), fontName, isTypedArtifact(tri), isAnyArtifact(tri),
                    tri.getText());
        }

        /** Called by the operator wrapper before iText's default handler processes
         *  the operator's operands and fires per-glyph RENDER_TEXT events. */
        void beginOp() {
            if (opDepth == 0) opBuffer.clear();
            opDepth++;
        }

        /** Called by the operator wrapper after iText's default handler returns.
         *  Emits at most one finding for the accumulated glyphs — the union of their
         *  bboxes evaluated once against the painters-model background. */
        void endOp() {
            opDepth--;
            if (opDepth == 0 && !opBuffer.isEmpty()) {
                emitFinding(opBuffer);
                opBuffer.clear();
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

        /**
         * Evaluate a batch of per-glyph events fired by one text-showing operator.
         * All events in the batch share text state (font, colour, artifact scope)
         * — the metadata for the pass/fail decision comes from the first glyph
         * that isn't filtered out; the geometry is the union of all glyph bboxes.
         */
        /**
         * Per-Tj emission with a size-based granularity switch. Short text-shows
         * (≤ {@link #PER_GLYPH_MAX_BATCH} glyphs) emit one finding per qualifying
         * glyph — matches PAC on Metro/CalSAWS where PAC's tally sits close to the
         * total glyph count. Long text-shows (whole words / phrases via a single
         * Tj/TJ) emit one union-bbox finding — matches PAC on OP_AoD/fisheries
         * where PAC aggregates at the Tj level (9382→2199 events, ~4.3 glyphs/op).
         *
         * <p>Rationale: PAC's granularity is document-dependent but well
         * correlated with the average glyphs-per-Tj ratio. A per-Tj threshold
         * approximates the doc-level heuristic without requiring a second pass.
         */
        private void emitFinding(List<GlyphEvent> glyphs) {
            // First: aggregate for the artifact/tinyBbox/widget pre-checks and
            // to derive the union bbox / representative probe (used in batch mode
            // and by the artifact/widget filters).
            GlyphEvent probe = null;
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            int qualifying = 0;
            for (GlyphEvent g : glyphs) {
                if (g.renderMode == 3) continue;
                if (g.typedArtifact) return;    // whole operator dropped — matches PAC
                if (SKIP_UNTYPED_ARTIFACTS && g.anyArtifact) return;   // PAC drops all Artifact-scoped
                if (g.fillRgb == null) continue;
                if (probe == null) probe = g;
                if (g.bbox == null) continue;
                if (g.bbox[2] - g.bbox[0] < 0.5 || g.bbox[3] - g.bbox[1] < 0.5) continue;
                qualifying++;
                if (g.bbox[0] < minX) minX = g.bbox[0];
                if (g.bbox[1] < minY) minY = g.bbox[1];
                if (g.bbox[2] > maxX) maxX = g.bbox[2];
                if (g.bbox[3] > maxY) maxY = g.bbox[3];
            }

            // Emit ERROR findings for glyphs whose colour we can't resolve (Separation
            // / DeviceN / Pattern / Lab / Indexed). Widget/artifact filters were
            // already applied in the aggregation pass above (whole operator dropped).
            if (UNRESOLVED_COLOR_AS_ERROR) emitUnresolvedGlyphs(glyphs);

            if (probe == null || qualifying == 0) return;

            // Short text-shows: emit one finding per qualifying glyph. Each glyph
            // gets its own bbox, fill, and background lookup.
            if (qualifying <= PER_GLYPH_MAX_BATCH) {
                for (GlyphEvent g : glyphs) {
                    if (g.renderMode == 3) continue;
                    if (g.fillRgb == null || g.bbox == null) continue;
                    if (g.bbox[2] - g.bbox[0] < 0.5 || g.bbox[3] - g.bbox[1] < 0.5) continue;
                    emitOne(g, g.bbox, g.fillRgb);
                }
                return;
            }

            // Batched text-show: single union-bbox check, matching legacy behaviour.
            double[] textBox = {minX, minY, maxX, maxY};
            emitOne(probe, textBox, probe.fillRgb);
        }

        /** Emit ONE ERROR per RENDER_TEXT event whose fill colour we can't convert
         *  to RGB (Separation / DeviceN / Pattern / Lab / Indexed). PAC splits at
         *  the content-stream piece level (verified against CalSAWS screenshot
         *  151741.png where the error rectangle covers exactly "wers Authority)"
         *  — one of the split fragments of "(A Joint Powers Authority)"). Each
         *  GlyphEvent in the buffer represents one iText RENDER_TEXT event which
         *  corresponds to one Tj call / one TJ-array string element. Widget-scoped
         *  events dropped; whitespace-only events skipped (PAC ignores those).
         *  Artifact-scoped events already filtered by the caller. */
        private void emitUnresolvedGlyphs(List<GlyphEvent> glyphs) {
            for (GlyphEvent g : glyphs) {
                if (g.renderMode == 3) continue;
                if (g.fillRgb != null) continue;
                if (g.bbox == null) continue;
                if (g.bbox[2] - g.bbox[0] < 0.5 || g.bbox[3] - g.bbox[1] < 0.5) continue;
                if (g.text != null && g.text.isBlank()) continue;
                if (insideAnyWidget(g.bbox)) continue;
                BBoxDTO bbox = new BBoxDTO(
                        (float) g.bbox[3], (float) g.bbox[0],
                        (float) (g.bbox[3] - g.bbox[1]), (float) (g.bbox[2] - g.bbox[0]));
                out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, bbox,
                        "Text fill colour is in a colour space contrast cannot be computed for"));
            }
        }

        /**
         * Emit one finding for a single glyph or a Tj batch. {@code textBox} is the
         * bbox used for widget/artifact/bg lookup and (on failure) for the report
         * rectangle. {@code fillRgb} is the text colour to measure against the
         * looked-up background.
         */
        private void emitOne(GlyphEvent probe, double[] textBox, double[] fillRgb) {
            // Skip text painted inside a Widget annotation's /Rect — the viewer
            // draws the widget's own appearance-stream text at the same location,
            // so any content-stream fallback text there is a duplicate.
            if (insideAnyWidget(textBox)) return;

            double[] bgRgb = backgroundAt(textBox, fillRgb);

            // Skip pure-white text on pure-white background (invisible filler).
            // Disabled under PAC compat: PAC intentionally flags white-on-white
            // events because it can't detect the true covering background.
            if (!PAC_COMPAT && isPureWhite(fillRgb) && isPureWhite(bgRgb)) return;

            double ratio = contrastRatio(fillRgb, bgRgb);
            double threshold = isLargeText(probe) ? THRESHOLD_LARGE : THRESHOLD_REGULAR;

            // PAC compat gate: flag pure-white text failing contrast when EITHER
            //  (a) it's inside a tagged <TH> cell,
            //  (b) it sits on a dark filled-path banner (L < 0.15), or
            //  (c) the whole page has no paint at all AND the text is inside a
            //      real struct tag (Span/P/etc.), not just an /Artifact scope.
            boolean chrome = isPureWhite(fillRgb) && isStandard14Base(probe.fontName);
            boolean emptyLog = paintLog.isEmpty() && !probe.anyArtifact;
            boolean keepAsError = isPureWhite(fillRgb) && !chrome
                    && (intersectsAnyTh(textBox) || hasDarkBanner(textBox) || emptyLog);
            if (PAC_COMPAT && ratio < threshold && !keepAsError) {
                out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, null));
                return;
            }

            if (ratio >= threshold) {
                out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, null));
            } else {
                BBoxDTO bbox = new BBoxDTO(
                        (float) textBox[3], (float) textBox[0],
                        (float) (textBox[3] - textBox[1]), (float) (textBox[2] - textBox[0]));
                out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.CONTRAST_OF_TEXT, pageNum, bbox,
                        String.format("Text contrast %.2f:1 is below WCAG minimum %.1f:1", ratio, threshold)));
            }
        }

        /** True iff the text bbox intersects any collected {@code <TH>} region on the
         *  current page. Used only under PAC compat mode. */
        private boolean intersectsAnyTh(double[] textBox) {
            for (double[] th : thBboxes) {
                if (textBox[2] < th[0] || textBox[0] > th[2]) continue;
                if (textBox[3] < th[1] || textBox[1] > th[3]) continue;
                return true;
            }
            return false;
        }

        /** Minimum height for a filled path to count as a "banner". Decorative
         *  strokes / dividers around status pills are ≤2pt; real banner strips
         *  behind header text are ≥5pt. */
        private static final double MIN_BANNER_HEIGHT = 14.0;
        /** Small tolerance for paint-vs-text bbox alignment — banner rectangles
         *  sometimes end at the text's ascent line (fractional-pt difference). */
        private static final double BANNER_SLACK = 0.5;

        /** Maximum luminance for a paint to qualify as a "dark banner" (navy/black).
         *  Tight enough to exclude WCAG-green status cells (L≈0.20) which PAC
         *  treats as background-uncertain and passes. */
        private static final double DARK_BANNER_L = 0.15;

        private boolean hasDarkBanner(double[] textBox) {
            for (int i = paintLog.size() - 1; i >= 0; i--) {
                Paint p = paintLog.get(i);
                if (p.rgb == null) continue;
                if (p.maxY - p.minY < MIN_BANNER_HEIGHT) continue;
                if (p.minY > textBox[1] + BANNER_SLACK) continue;
                if (p.maxY < textBox[3] - BANNER_SLACK) continue;
                if (textBox[2] < p.minX || textBox[0] > p.maxX) continue;
                if (relativeLuminance(p.rgb) < DARK_BANNER_L) return true;
            }
            return false;
        }


        /** True iff the text bbox intersects any Widget annotation's /Rect. Any
         *  overlap counts — form-field labels sometimes extend slightly outside the
         *  widget /Rect boundary and PAC still drops them from the 1.4.3 tally. */
        private boolean insideAnyWidget(double[] textBox) {
            for (double[] w : widgetRects) {
                if (textBox[2] < w[0] || textBox[0] > w[2]) continue;
                if (textBox[3] < w[1] || textBox[1] > w[3]) continue;
                return true;
            }
            return false;
        }

        /** Walk the paint log newest-to-oldest and return the topmost covering paint's
         *  effective colour under the text bbox. Images use worst-case pixel sampling
         *  against the given text fill; solid rectangles return their single colour.
         *  In {@link #PAC_COMPAT} mode returns white unconditionally. Selection mode
         *  is gated on {@link #BG_OVERLAP}. */
        private double[] backgroundAt(double[] textBox, double[] textRgb) {
            if (PAC_COMPAT) return new double[]{1.0, 1.0, 1.0};
            boolean intersectsMode = "intersects".equals(BG_OVERLAP);
            double textArea = intersectsMode
                    ? Math.max(1e-9, (textBox[2] - textBox[0]) * (textBox[3] - textBox[1]))
                    : 0.0;
            for (int i = paintLog.size() - 1; i >= 0; i--) {
                Paint p = paintLog.get(i);
                if (intersectsMode) {
                    if (p.intersectionArea(textBox[0], textBox[1], textBox[2], textBox[3])
                            / textArea >= INTERSECT_DOMINANCE) {
                        return p.worstAgainst(textBox[0], textBox[1], textBox[2], textBox[3], textRgb);
                    }
                } else if (p.covers(textBox[0], textBox[1], textBox[2], textBox[3])) {
                    return p.worstAgainst(textBox[0], textBox[1], textBox[2], textBox[3], textRgb);
                }
            }
            return new double[]{1.0, 1.0, 1.0};
        }
    }

    /**
     * Collect the union bboxes of every {@code <TH>} structure element on the given page.
     * Used by PAC compat mode to restrict contrast fails to header-role text regions.
     * <p>
     * For each {@code <TH>} we DFS its {@code /K} subtree, collect every descendant MCID
     * that resolves to the current page, then union their paint bboxes (looked up in a
     * per-page MCID → bbox map). The result is a list of {@code {minX, minY, maxX, maxY}}
     * rectangles.
     */
    private static List<double[]> collectThRegions(PdfDocument pdf, int pageNum) {
        java.util.Map<Integer, com.netralabs.report.BBoxDTO> mcidBboxes =
                com.netralabs.basic.content.PageMcidBboxes.forPage(pdf, pageNum);
        if (mcidBboxes.isEmpty()) return java.util.Collections.emptyList();

        List<double[]> out = new ArrayList<>();
        com.netralabs.basic.pdfsyntax.StructUtils.walkStructure(pdf, (parent, elem) -> {
            PdfName role = elem.getAsName(PdfName.S);
            if (role == null || !"TH".equals(role.getValue())) return;
            List<Integer> mcids = new ArrayList<>();
            collectMcidsOnPage(elem, pdf, pageNum, elem.getAsDictionary(PdfName.Pg), mcids);
            if (mcids.isEmpty()) return;
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (Integer m : mcids) {
                com.netralabs.report.BBoxDTO b = mcidBboxes.get(m);
                if (b == null) continue;
                double top = b.getTop(), left = b.getLeft();
                double bottom = top - b.getHeight();
                double right = left + b.getWidth();
                if (left < minX) minX = left;
                if (bottom < minY) minY = bottom;
                if (right > maxX) maxX = right;
                if (top > maxY) maxY = top;
            }
            if (minX < Double.POSITIVE_INFINITY) {
                out.add(new double[]{minX, minY, maxX, maxY});
            }
        });
        return out;
    }

    /** DFS {@code /K} of a struct element, appending every descendant MCID that resolves
     *  to {@code pageNum}. Handles the three /K shapes: integer literal, MCR dict, and
     *  child StructElem. {@code inheritedPg} propagates the enclosing /Pg dictionary
     *  down the tree (PDF spec §14.7.4.4 — /Pg may be inherited from an ancestor). */
    private static void collectMcidsOnPage(com.itextpdf.kernel.pdf.PdfDictionary elem,
                                           PdfDocument pdf, int pageNum,
                                           com.itextpdf.kernel.pdf.PdfDictionary inheritedPg,
                                           List<Integer> out) {
        com.itextpdf.kernel.pdf.PdfDictionary myPg = elem.getAsDictionary(PdfName.Pg);
        com.itextpdf.kernel.pdf.PdfDictionary effectivePg = myPg != null ? myPg : inheritedPg;
        com.itextpdf.kernel.pdf.PdfObject k = elem.get(PdfName.K);
        collectMcidsFromK(k, pdf, pageNum, effectivePg, out);
    }

    private static void collectMcidsFromK(com.itextpdf.kernel.pdf.PdfObject k,
                                          PdfDocument pdf, int pageNum,
                                          com.itextpdf.kernel.pdf.PdfDictionary inheritedPg,
                                          List<Integer> out) {
        if (k == null) return;
        if (k.isNumber()) {
            if (pageMatches(pdf, inheritedPg, pageNum)) {
                out.add(((com.itextpdf.kernel.pdf.PdfNumber) k).intValue());
            }
        } else if (k.isDictionary()) {
            com.itextpdf.kernel.pdf.PdfDictionary d = (com.itextpdf.kernel.pdf.PdfDictionary) k;
            PdfName type = d.getAsName(PdfName.Type);
            if (PdfName.MCR.equals(type)) {
                com.itextpdf.kernel.pdf.PdfDictionary mcrPg = d.getAsDictionary(PdfName.Pg);
                com.itextpdf.kernel.pdf.PdfNumber mcid = d.getAsNumber(PdfName.MCID);
                if (mcid != null && pageMatches(pdf, mcrPg != null ? mcrPg : inheritedPg, pageNum)) {
                    out.add(mcid.intValue());
                }
            } else if (com.netralabs.basic.pdfsyntax.StructUtils.isStructElem(d)) {
                collectMcidsOnPage(d, pdf, pageNum, inheritedPg, out);
            }
            // OBJR (/Type /OBJR) references an object, not marked content — skip.
        } else if (k.isArray()) {
            com.itextpdf.kernel.pdf.PdfArray arr = (com.itextpdf.kernel.pdf.PdfArray) k;
            for (int i = 0; i < arr.size(); i++) {
                collectMcidsFromK(arr.get(i), pdf, pageNum, inheritedPg, out);
            }
        }
    }

    private static boolean pageMatches(PdfDocument pdf,
                                       com.itextpdf.kernel.pdf.PdfDictionary pgDict,
                                       int pageNum) {
        if (pgDict == null) return false;
        try {
            com.itextpdf.kernel.pdf.PdfPage page = pdf.getPage(pgDict);
            return page != null && pdf.getPageNumber(page) == pageNum;
        } catch (Exception ignored) {
            return false;
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
     * True iff the innermost marked-content tag is a classified {@code /Artifact}
     * BDC — an Artifact with an explicit {@code /Type} property (Pagination /
     * Page / Layout / Background). PAC excludes these from the 1.4.3 tally as
     * decorative content. Untyped {@code /Artifact} scopes are kept (they may
     * still carry visible text the user reads).
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

    /** True iff any tag in the marked-content chain is an {@code /Artifact} BDC
     *  (typed or untyped). Per ISO 14289 semantics, content inside an
     *  {@code /Artifact} scope is decorative regardless of nested tags
     *  underneath — e.g. {@code <Artifact><Span>Tj</Span></Artifact>} makes
     *  the text an artifact even though its innermost tag is {@code Span}.
     *  Used to drop artifact-scoped text from the 1.4.3 contrast tally and
     *  to gate the PAC-compat empty-page white-on-white handling. */
    private static boolean isAnyArtifact(TextRenderInfo tri) {
        List<CanvasTag> h = tri.getCanvasTagHierarchy();
        if (h == null || h.isEmpty()) return false;
        for (CanvasTag t : h) {
            PdfName role = t.getRole();
            if (role != null && "Artifact".equals(role.getValue())) return true;
        }
        return false;
    }

    /**
     * Convert an iText {@link Color} to a linear RGB triple in [0, 1]. Handles
     * DeviceGray / DeviceRGB / DeviceCMYK, and treats {@link IccBased} colours by
     * their component count (1→Gray, 3→RGB, 4→CMYK) — the raw values already sit
     * in [0, 1] and ICC profiles for text are almost always sRGB-family wrappers,
     * so the approximation is close enough for a WCAG contrast tally. Returns null
     * for Separation / DeviceN / Pattern / Lab / Indexed.
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
            return cmykToRgb(c.getColorValue());
        }
        if (c instanceof IccBased) {
            float[] v = c.getColorValue();
            if (v == null) return null;
            // First: try to transform via the actual ICC profile. Falls back to the
            // component-count heuristic if the profile is missing, unparseable, or
            // Java's ICC engine rejects it (rare but happens on malformed profiles).
            double[] transformed = iccBasedThroughProfile((IccBased) c, v);
            if (transformed != null) return transformed;
            if (v.length == 1) {
                double x = clamp01(v[0]);
                return new double[]{x, x, x};
            }
            if (v.length == 3) {
                return new double[]{clamp01(v[0]), clamp01(v[1]), clamp01(v[2])};
            }
            if (v.length == 4) return cmykToRgb(v);
            return null;
        }
        if (c == ColorConstants.BLACK) return new double[]{0, 0, 0};
        if (c == ColorConstants.WHITE) return new double[]{1, 1, 1};
        return null;
    }

    /** Naive uncalibrated-CMYK to RGB. */
    private static double[] cmykToRgb(float[] v) {
        double C = clamp01(v[0]);
        double M = clamp01(v[1]);
        double Y = clamp01(v[2]);
        double K = clamp01(v[3]);
        return new double[]{(1 - C) * (1 - K), (1 - M) * (1 - K), (1 - Y) * (1 - K)};
    }

    /** Cache of {@link ICC_ColorSpace} instances keyed by the underlying PDF profile
     *  stream. Parsing an ICC profile and constructing the ColorSpace is expensive
     *  and every glyph on a page can share the same profile. WeakHashMap keeps the
     *  cache from pinning documents in memory after they close. */
    private static final Map<PdfStream, ICC_ColorSpace> ICC_CACHE =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    /** Sentinel — we cache profile-load failures too to avoid retrying on every glyph. */
    private static final ICC_ColorSpace ICC_FAILED = new ICC_ColorSpace(
            ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB));

    /**
     * Try to transform an {@link IccBased} colour's raw channel values through the
     * embedded ICC profile using Java's {@link ICC_ColorSpace}. Returns a gamma-
     * encoded sRGB triple in [0, 1] on success (the downstream {@code channelLuminance}
     * applies the sRGB→linear step), or {@code null} on any failure — caller should
     * fall back to the component-count heuristic.
     *
     * <p>An {@code ICCBased} colour space in PDF is encoded as a two-element array
     * {@code [/ICCBased <<...profile stream>>]}. We reach the profile stream via
     * the wrapped {@code PdfArray} because iText 9.1 doesn't expose an instance
     * getter (the similarly-named {@code getIccProfileStream} is a static factory).
     */
    private static double[] iccBasedThroughProfile(IccBased c, float[] raw) {
        try {
            if (!(c.getColorSpace() instanceof PdfCieBasedCs.IccBased iccCs)) return null;
            com.itextpdf.kernel.pdf.PdfObject wrapped = iccCs.getPdfObject();
            if (!(wrapped instanceof com.itextpdf.kernel.pdf.PdfArray arr) || arr.size() < 2) return null;
            PdfStream profileStream = arr.getAsStream(1);
            if (profileStream == null) return null;
            ICC_ColorSpace cs = ICC_CACHE.computeIfAbsent(profileStream, ps -> {
                try {
                    byte[] bytes = ps.getBytes();
                    if (bytes == null || bytes.length == 0) return ICC_FAILED;
                    return new ICC_ColorSpace(ICC_Profile.getInstance(bytes));
                } catch (Exception | Error e) {
                    return ICC_FAILED;
                }
            });
            if (cs == ICC_FAILED) return null;
            if (cs.getNumComponents() != raw.length) return null;
            float[] srgb = cs.toRGB(raw);
            if (srgb == null || srgb.length < 3) return null;
            return new double[]{clamp01(srgb[0]), clamp01(srgb[1]), clamp01(srgb[2])};
        } catch (Exception | Error e) {
            return null;
        }
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

    /** True iff every channel is above 0.98 — treats DeviceGray(1.0), DeviceRgb(1,1,1),
     *  and near-white image samples uniformly as pure white. */
    private static boolean isPureWhite(double[] rgb) {
        return rgb[0] > 0.98 && rgb[1] > 0.98 && rgb[2] > 0.98;
    }

    /** True iff {@code fontName} is one of the 14 Standard Latin Type-1 fonts drawn
     *  without a subset prefix — the base "Helvetica", "Times-Roman", "Courier",
     *  "Symbol", "ZapfDingbats" faces used by viewers when the doc doesn't embed a
     *  face. PAC excludes text rendered in these from the 1.4.3 contrast tally
     *  (they're typically page chrome — footers, rotated watermarks). */
    private static boolean isStandard14Base(String fontName) {
        if (fontName == null || fontName.isEmpty()) return false;
        // Skip subset-prefixed names ("ABCDEF+Helvetica"): those are proper subsets,
        // not the bare base 14.
        if (fontName.length() > 6 && fontName.charAt(6) == '+') return false;
        return switch (fontName) {
            case "Times-Roman", "Times-Bold", "Times-Italic", "Times-BoldItalic",
                 "Helvetica", "Helvetica-Bold", "Helvetica-Oblique", "Helvetica-BoldOblique",
                 "Courier", "Courier-Bold", "Courier-Oblique", "Courier-BoldOblique",
                 "Symbol", "ZapfDingbats" -> true;
            default -> false;
        };
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
    private static boolean isLargeText(ValidateContrastOfText.ContrastListener.GlyphEvent g) {
        float pts = g.fontSize();
        if (pts <= 0) return false;
        if (pts >= LARGE_TEXT_POINTS) return true;
        if (pts < BOLD_LARGE_TEXT_POINTS) return false;
        if (g.fontName() == null) return false;
        String name = g.fontName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("bold") || name.contains("heavy") || name.contains("black");
    }
}