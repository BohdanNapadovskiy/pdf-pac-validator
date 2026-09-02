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

import static com.netralabs.domain.PDFUACheckpoint.ARTIFACT_INSIDE_TAGGED_CONTENT;

/**
 * ISO 14289-1 §7.1: an {@code /Artifact} marked-content block must not sit inside a
 * tagged (MCID-bearing) block. Emits one PASSED per Artifact BMC boundary encountered
 * at the top level and one ERROR for each nested case.
 *
 * <p>Also flags the "structural hybrid" case observed on PAC-parity docs
 * (Filled_Graduate p1 MCID 122): a {@code /Artifact} BDC that carries an
 * {@code /MCID} property whose value is referenced by the struct tree. PAC treats
 * the shared MCID as both "tagged content inside artifact" (see
 * {@link ValidateTaggedInsideArtifacts}) and "artifact inside tagged content" —
 * because the MCID declares a tagged intent that the {@code /Artifact} tag
 * simultaneously contradicts.
 *
 * <p>Uses direct BMC/BDC/EMC operator interception instead of driving off render
 * events — the previous ContentWalker-based implementation missed empty Artifact
 * blocks (BMC/EMC with no paint in between) because ContentWalker synthesizes
 * scope pulses only when a render event fires.
 */
public class ValidateArtifactsInsideTagged implements Rule {

    private static final String ARTIFACT = "Artifact";
    private static final PdfName MCID = new PdfName("MCID");

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        Map<Integer, Set<Integer>> treeRefsByPage = collectTreeMcids(pdf);

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;
            final Set<Integer> pageTreeRefs = treeRefsByPage.getOrDefault(page, Set.of());
            PdfPage p = pdf.getPage(page);
            PdfCanvasProcessor proc = new PdfCanvasProcessor(new SinkListener());
            Frames frames = new Frames();

            proc.registerContentOperator("BMC", (proc2, op, operands) -> {
                String tag = tagName(operands.get(0));
                frames.enter(tag, false);
                if (ARTIFACT.equals(tag)) recordEnter(frames, out, pageNum, false);
            });
            proc.registerContentOperator("BDC", (proc2, op, operands) -> {
                String tag = tagName(operands.get(0));
                Integer mcid = null;
                if (operands.size() > 1 && operands.get(1) instanceof PdfDictionary d) {
                    PdfNumber n = d.getAsNumber(MCID);
                    if (n != null) mcid = n.intValue();
                }
                boolean taggedMcid = mcid != null;
                frames.enter(tag, taggedMcid);
                if (ARTIFACT.equals(tag)) {
                    // Artifact BDC whose MCID is referenced by the struct tree is a
                    // structural hybrid: the tagged intent (MCID → tree entry) sits
                    // inside an Artifact declaration. PAC flags this on this row too.
                    boolean selfHybridWithTreeRef = mcid != null && pageTreeRefs.contains(mcid);
                    recordEnter(frames, out, pageNum, selfHybridWithTreeRef);
                }
            });
            proc.registerContentOperator("EMC", (proc2, op, operands) -> frames.exit());

            proc.processPageContent(p);
        }
        return out;
    }

    private static String tagName(Object operand) {
        return operand.toString().startsWith("/") ? operand.toString().substring(1) : operand.toString();
    }

    private static void recordEnter(Frames frames, List<FindingDTO> out, int page, boolean forceError) {
        if (forceError || frames.hasTaggedAncestor()) {
            out.add(new FindingDTO(Severity.ERROR, ARTIFACT_INSIDE_TAGGED_CONTENT, page, null,
                    "Artifact is nested inside tagged content"));
        } else {
            out.add(new FindingDTO(Severity.PASSED, ARTIFACT_INSIDE_TAGGED_CONTENT, page, null));
        }
    }

    /** Enumerate all (page, MCID) pairs referenced from the struct tree. */
    private static Map<Integer, Set<Integer>> collectTreeMcids(PdfDocument pdf) {
        Map<Integer, Set<Integer>> byPage = new HashMap<>();
        StructUtils.walkStructure(pdf, (parent, se) -> {
            int defaultPage = StructUtils.pageNumOf(pdf, se);
            collectMcids(se.get(PdfName.K), defaultPage, pdf, byPage);
        });
        return byPage;
    }

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
                        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                            if (pdf.getPage(i).getPdfObject() == pg) { page = i; break; }
                        }
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

    /**
     * Tracks the marked-content stack. Each frame records the tag name and whether
     * the frame introduces tagged (MCID-bearing) content.
     */
    static final class Frames {
        private final Deque<Frame> stack = new ArrayDeque<>();
        void enter(String tag, boolean taggedMcid) { stack.push(new Frame(tag, taggedMcid)); }
        void exit() { if (!stack.isEmpty()) stack.pop(); }
        /** True iff any frame BELOW the top of stack is a tagged (MCID) frame. */
        boolean hasTaggedAncestor() {
            boolean skippedTop = false;
            for (Frame f : stack) {
                if (!skippedTop) { skippedTop = true; continue; }
                if (f.taggedMcid) return true;
            }
            return false;
        }
        record Frame(String tag, boolean taggedMcid) {}
    }

    /** Absorbs render events (we drive off BMC/BDC/EMC operators, not paint events). */
    private static final class SinkListener implements IEventListener {
        public void eventOccurred(IEventData data, EventType type) {}
        public Set<EventType> getSupportedEvents() { return EnumSet.noneOf(EventType.class); }
    }
}
