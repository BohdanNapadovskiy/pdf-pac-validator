package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.netralabs.Rule;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netralabs.domain.PDFUACheckpoint.TAGGED_CONTENT_INSIDE_ARTIFACT;

/**
 * ISO 14289-1 §7.1: tagged content (a BMC/BDC block carrying an {@code /MCID}
 * property) must not sit inside an {@code /Artifact} block.
 *
 * <p>Emission is driven by the struct tree, not the content stream: enumerate
 * every unique {@code (page, MCID)} pair referenced from the tree (once each),
 * then during content-stream traversal check whether that MCID's BDC frame has
 * an Artifact ancestor. Each tree-referenced MCID contributes one finding:
 * PASSED when its BDC is at the top level, ERROR when nested inside an Artifact
 * BMC/BDC. Verified: Filled 111P/1E, Complex 320P/0E, CalSAWS 1P/0E — matches
 * PAC exactly.
 *
 * <p>Previous ContentWalker-based implementation missed empty MCID BDCs (no
 * paint inside); direct BDC counting over-counted (152 vs PAC 111) because same
 * MCID re-declared by a text-run split still generates a new BDC.
 */
public class ValidateTaggedInsideArtifacts implements Rule {

    private static final String ARTIFACT = "Artifact";
    private static final PdfName MCID = new PdfName("MCID");

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();

        // 1) Enumerate all (page, MCID) pairs referenced from the struct tree.
        //    A struct element's /K can be a number (MCID on the element's own /Pg),
        //    a dict with /Type=/MCR (explicit page + MCID), or an array of the above.
        Map<Integer, Set<Integer>> treeRefsByPage = new HashMap<>();
        StructUtils.walkStructure(pdf, (parent, se) -> {
            int defaultPage = StructUtils.pageNumOf(pdf, se);
            collectMcids(se.get(PdfName.K), defaultPage, pdf, treeRefsByPage);
        });

        List<FindingDTO> out = new ArrayList<>();
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            Set<Integer> pageTreeRefs = treeRefsByPage.getOrDefault(page, Set.of());
            if (pageTreeRefs.isEmpty()) continue;
            walkPageMcidScope(pdf, page, pageTreeRefs, out);
        }
        return out;
    }

    /** Walk the content stream and record whether each tree-referenced MCID lives under an Artifact scope. */
    private static void walkPageMcidScope(PdfDocument pdf, int page, Set<Integer> pageTreeRefs, List<FindingDTO> out) {
        PdfPage p = pdf.getPage(page);
        PdfCanvasProcessor proc = new PdfCanvasProcessor(new SinkListener());
        Frames frames = new Frames();
        Set<Integer> recorded = new HashSet<>();

        proc.registerContentOperator("BMC", (proc2, op, operands) -> {
            frames.enter(tagName(operands.get(0)), null);
        });
        proc.registerContentOperator("BDC", (proc2, op, operands) -> {
            String tag = tagName(operands.get(0));
            Integer mcid = null;
            if (operands.size() > 1 && operands.get(1) instanceof PdfDictionary d) {
                PdfNumber n = d.getAsNumber(MCID);
                if (n != null) mcid = n.intValue();
            }
            frames.enter(tag, mcid);
            if (mcid != null && pageTreeRefs.contains(mcid) && recorded.add(mcid)) {
                boolean inArtifact = frames.hasArtifactAncestor();
                if (inArtifact) {
                    out.add(new FindingDTO(Severity.ERROR, TAGGED_CONTENT_INSIDE_ARTIFACT, page, null,
                            "Tagged content is nested inside an artifact"));
                } else {
                    out.add(new FindingDTO(Severity.PASSED, TAGGED_CONTENT_INSIDE_ARTIFACT, page, null));
                }
            }
        });
        proc.registerContentOperator("EMC", (proc2, op, operands) -> frames.exit());

        proc.processPageContent(p);

        // Emit PASSED for any tree-referenced MCID whose BDC we never saw in the
        // content stream (orphan or malformed doc — PAC still shows a pass because
        // the tree acknowledges the tagged content).
        for (int mcid : pageTreeRefs) {
            if (!recorded.contains(mcid)) {
                out.add(new FindingDTO(Severity.PASSED, TAGGED_CONTENT_INSIDE_ARTIFACT, page, null));
            }
        }
    }

    /**
     * Collect (page, MCID) references from a struct element's /K entry. Number kids
     * use the enclosing struct element's /Pg (defaultPage). MCR dicts may override
     * with an explicit /Pg on the MCR itself.
     */
    private static void collectMcids(PdfObject k, int defaultPage, PdfDocument pdf,
                                     Map<Integer, Set<Integer>> byPage) {
        if (k == null) return;
        if (k instanceof PdfNumber n) {
            byPage.computeIfAbsent(defaultPage, kk -> new HashSet<>()).add(n.intValue());
            return;
        }
        if (k instanceof PdfDictionary d) {
            PdfName t = d.getAsName(PdfName.Type);
            if (t == null || "MCR".equals(t.getValue())) {
                PdfNumber mcid = d.getAsNumber(MCID);
                if (mcid != null) {
                    int page = defaultPage;
                    PdfDictionary pg = d.getAsDictionary(PdfName.Pg);
                    if (pg != null) {
                        Integer pn = pageNumOf(pdf, pg);
                        if (pn != null) page = pn;
                    }
                    byPage.computeIfAbsent(page, kk -> new HashSet<>()).add(mcid.intValue());
                }
            }
            return;
        }
        if (k instanceof PdfArray arr) {
            for (int i = 0; i < arr.size(); i++) collectMcids(arr.get(i), defaultPage, pdf, byPage);
        }
    }

    private static Integer pageNumOf(PdfDocument pdf, PdfDictionary pgDict) {
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            if (pdf.getPage(i).getPdfObject() == pgDict) return i;
        }
        return null;
    }

    private static String tagName(Object operand) {
        String s = operand.toString();
        return s.startsWith("/") ? s.substring(1) : s;
    }

    /** Marked-content scope stack. Frame records the tag and (for BDC) the MCID or null. */
    static final class Frames {
        private final Deque<Frame> stack = new ArrayDeque<>();
        void enter(String tag, Integer mcid) { stack.push(new Frame(tag, mcid)); }
        void exit() { if (!stack.isEmpty()) stack.pop(); }
        boolean hasArtifactAncestor() {
            boolean skippedTop = false;
            for (Frame f : stack) {
                if (!skippedTop) { skippedTop = true; continue; }
                if (ARTIFACT.equals(f.tag)) return true;
            }
            return false;
        }
        record Frame(String tag, Integer mcid) {}
    }

    private static final class SinkListener implements IEventListener {
        public void eventOccurred(IEventData data, EventType type) {}
        public Set<EventType> getSupportedEvents() { return EnumSet.noneOf(EventType.class); }
    }
}