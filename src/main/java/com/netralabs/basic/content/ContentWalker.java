package com.netralabs.basic.content;

import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Point;
import com.itextpdf.kernel.geom.Subpath;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.CanvasTag;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.PathRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.netralabs.report.BBoxDTO;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Drives an iText {@link PdfCanvasProcessor} to deliver scope-aware, geometry-aware
 * events to a {@link Hook}. Marked-content BMC/EMC pulses are synthesized from
 * each render event's canvas-tag hierarchy: when the hierarchy grows, begin pulses
 * fire for the new tags; when it shrinks, end pulses fire for the dropped tags.
 *
 * <p>Bboxes are emitted in PDF user-space (origin bottom-left, y increases upward).
 */
public final class ContentWalker {

    private static final PdfName ARTIFACT = new PdfName("Artifact");

    private ContentWalker() {}

    public static void walkPage(PdfDocument pdf, int pageNo, Hook hook) {
        PdfPage page = pdf.getPage(pageNo);
        if (page == null) return;
        PdfCanvasProcessor proc = new PdfCanvasProcessor(new ScopeAwareListener(hook));
        proc.processPageContent(page);
    }

    private static final class ScopeAwareListener implements IEventListener {
        private final Hook hook;
        private List<CanvasTag> lastHierarchy = Collections.emptyList();

        ScopeAwareListener(Hook hook) { this.hook = hook; }

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            switch (type) {
                case RENDER_TEXT -> {
                    TextRenderInfo ti = (TextRenderInfo) data;
                    syncScope(ti.getCanvasTagHierarchy());
                    BBoxDTO bbox = bboxOf(ti);
                    hook.onPainted(bbox);
                    hook.onShowText(ti.getPdfString(), bbox);
                    hook.onShowText(ti, bbox);
                }
                case RENDER_IMAGE -> {
                    ImageRenderInfo ii = (ImageRenderInfo) data;
                    syncScope(ii.getCanvasTagHierarchy());
                    hook.onPainted(bboxOf(ii));
                }
                case RENDER_PATH -> {
                    PathRenderInfo pi = (PathRenderInfo) data;
                    if (pi.getOperation() == PathRenderInfo.NO_OP) return;
                    syncScope(pi.getCanvasTagHierarchy());
                    BBoxDTO bbox = bboxOf(pi);
                    if (bbox != null) hook.onPainted(bbox);
                }
                default -> {}
            }
        }

        @Override
        public Set<EventType> getSupportedEvents() {
            return EnumSet.of(EventType.RENDER_TEXT, EventType.RENDER_IMAGE, EventType.RENDER_PATH);
        }

        private void syncScope(List<CanvasTag> hierarchy) {
            if (hierarchy == null) hierarchy = Collections.emptyList();
            int common = 0;
            int max = Math.min(lastHierarchy.size(), hierarchy.size());
            while (common < max && lastHierarchy.get(common) == hierarchy.get(common)) common++;

            for (int i = lastHierarchy.size(); i > common; i--) hook.onEndMarked();

            for (int i = common; i < hierarchy.size(); i++) {
                CanvasTag tag = hierarchy.get(i);
                PdfName role = tag.getRole();
                if (ARTIFACT.equals(role)) {
                    hook.onBeginArtifact();
                } else if (tag.hasMcid()) {
                    hook.onBeginTaggedMcid(tag.getMcid(), null);
                } else {
                    hook.onBeginOtherMarked(role);
                }
            }
            lastHierarchy = hierarchy;
        }
    }

    // ---------------- bbox helpers ----------------

    private static BBoxDTO bboxOf(TextRenderInfo ti) {
        LineSegment baseline = ti.getBaseline();
        LineSegment ascent = ti.getAscentLine();
        LineSegment descent = ti.getDescentLine();
        float[] xs = {
                baseline.getStartPoint().get(0), baseline.getEndPoint().get(0),
                ascent.getStartPoint().get(0),   ascent.getEndPoint().get(0),
                descent.getStartPoint().get(0),  descent.getEndPoint().get(0)
        };
        float[] ys = {
                baseline.getStartPoint().get(1), baseline.getEndPoint().get(1),
                ascent.getStartPoint().get(1),   ascent.getEndPoint().get(1),
                descent.getStartPoint().get(1),  descent.getEndPoint().get(1)
        };
        return bboxOfPoints(xs, ys);
    }

    private static BBoxDTO bboxOf(ImageRenderInfo ii) {
        Matrix ctm = ii.getImageCtm();
        if (ctm == null) return null;
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        float[] xs = new float[4];
        float[] ys = new float[4];
        for (int i = 0; i < 4; i++) {
            Vector v = new Vector(corners[i][0], corners[i][1], 1).cross(ctm);
            xs[i] = v.get(0);
            ys[i] = v.get(1);
        }
        return bboxOfPoints(xs, ys);
    }

    private static BBoxDTO bboxOf(PathRenderInfo pi) {
        if (pi.getPath() == null || pi.getPath().getSubpaths().isEmpty()) return null;
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        boolean any = false;
        for (Subpath sp : pi.getPath().getSubpaths()) {
            try {
                for (Point p : sp.getPiecewiseLinearApproximation()) {
                    float x = (float) p.getX();
                    float y = (float) p.getY();
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    any = true;
                }
            } catch (Exception ignored) {}
        }
        if (!any) return null;
        // Path coordinates are already in user-space (CTM pre-applied by the processor).
        return new BBoxDTO(maxY, minX, maxY - minY, maxX - minX);
    }

    private static BBoxDTO bboxOfPoints(float[] xs, float[] ys) {
        float minX = xs[0], maxX = xs[0], minY = ys[0], maxY = ys[0];
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] < minX) minX = xs[i];
            if (xs[i] > maxX) maxX = xs[i];
            if (ys[i] < minY) minY = ys[i];
            if (ys[i] > maxY) maxY = ys[i];
        }
        return new BBoxDTO(maxY, minX, maxY - minY, maxX - minX);
    }
}
