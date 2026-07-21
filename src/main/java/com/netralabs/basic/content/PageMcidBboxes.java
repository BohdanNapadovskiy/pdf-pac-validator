package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.report.BBoxDTO;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a per-page {@code MCID → union-bbox} map by walking the page's
 * content stream once through {@link ContentWalker}. Consumers can then
 * look up the on-page rectangle for any tagged MCID and use it as a bbox
 * hint (e.g. to attribute a struct-tree finding to a page location).
 *
 * <p>The map lazily accumulates: each paint event that fires inside a
 * tagged MCID scope grows the MCID's bbox to cover the new event.
 */
public final class PageMcidBboxes {

    private PageMcidBboxes() {}

    public static Map<Integer, BBoxDTO> forPage(PdfDocument pdf, int pageNo) {
        Collector c = new Collector();
        ContentWalker.walkPage(pdf, pageNo, c);
        return c.result;
    }

    private static final class Collector implements Hook {
        // Sentinel for scopes that do not carry an MCID (Artifact, generic BDC).
        // Kept on the stack so nested pops don't shadow an enclosing MCID.
        private static final int NO_MCID = Integer.MIN_VALUE;

        final Map<Integer, BBoxDTO> result = new HashMap<>();
        private final Deque<Integer> scopeStack = new ArrayDeque<>();

        @Override
        public void onBeginTaggedMcid(int mcid, PdfDictionary pgDict) {
            scopeStack.push(mcid);
        }

        @Override
        public void onBeginArtifact() {
            scopeStack.push(NO_MCID);
        }

        @Override
        public void onBeginOtherMarked(PdfName tag) {
            // A non-MCID BDC scope (e.g. Reversed) doesn't itself carry a tag id;
            // paints inside it should still attribute to the enclosing MCID, so
            // push the current top rather than NO_MCID.
            scopeStack.push(scopeStack.isEmpty() ? NO_MCID : scopeStack.peek());
        }

        @Override
        public void onEndMarked() {
            if (!scopeStack.isEmpty()) scopeStack.pop();
        }

        @Override
        public void onPainted(BBoxDTO bbox) {
            if (bbox == null || scopeStack.isEmpty()) return;
            int mcid = scopeStack.peek();
            if (mcid == NO_MCID) return;
            result.merge(mcid, bbox, PageMcidBboxes::union);
        }
    }

    /** Union of two axis-aligned bboxes in PDF user-space (top-left origin of the DTO). */
    private static BBoxDTO union(BBoxDTO a, BBoxDTO b) {
        float aTop = a.getTop();
        float aBottom = aTop - a.getHeight();
        float aLeft = a.getLeft();
        float aRight = aLeft + a.getWidth();

        float bTop = b.getTop();
        float bBottom = bTop - b.getHeight();
        float bLeft = b.getLeft();
        float bRight = bLeft + b.getWidth();

        float top = Math.max(aTop, bTop);
        float bottom = Math.min(aBottom, bBottom);
        float left = Math.min(aLeft, bLeft);
        float right = Math.max(aRight, bRight);
        return new BBoxDTO(top, left, top - bottom, right - left);
    }
}
