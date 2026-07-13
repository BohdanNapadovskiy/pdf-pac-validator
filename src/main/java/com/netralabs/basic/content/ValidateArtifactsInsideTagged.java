package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.netralabs.domain.PDFUACheckpoint.ARTIFACT_INSIDE_TAGGED_CONTENT;

/**
 * ISO 14289-1 §7.1: an {@code /Artifact} marked-content block must not sit inside a
 * tagged (MCID-bearing) block. Emits one PASSED per Artifact BMC boundary encountered
 * at the top level (no tagged ancestor) and one ERROR per nested one.
 *
 * <p>Uses direct BMC/BDC/EMC operator interception instead of driving off render
 * events — the previous ContentWalker-based implementation missed empty Artifact
 * blocks (BMC/EMC with no paint in between) because ContentWalker synthesizes
 * scope pulses only when a render event fires.
 */
public class ValidateArtifactsInsideTagged implements Rule {

    private static final String ARTIFACT = "Artifact";

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;
            PdfPage p = pdf.getPage(page);
            PdfCanvasProcessor proc = new PdfCanvasProcessor(new SinkListener());
            Frames frames = new Frames();

            proc.registerContentOperator("BMC", (proc2, op, operands) -> {
                String tag = tagName(operands.get(0));
                frames.enter(tag, false);
                if (ARTIFACT.equals(tag)) recordEnter(frames, out, pageNum);
            });
            proc.registerContentOperator("BDC", (proc2, op, operands) -> {
                String tag = tagName(operands.get(0));
                boolean taggedMcid = operands.size() > 1
                        && operands.get(1) instanceof PdfDictionary d
                        && d.getAsNumber(new com.itextpdf.kernel.pdf.PdfName("MCID")) != null;
                frames.enter(tag, taggedMcid);
                if (ARTIFACT.equals(tag)) recordEnter(frames, out, pageNum);
            });
            proc.registerContentOperator("EMC", (proc2, op, operands) -> frames.exit());

            proc.processPageContent(p);
        }
        return out;
    }

    private static String tagName(Object operand) {
        return operand.toString().startsWith("/") ? operand.toString().substring(1) : operand.toString();
    }

    private static void recordEnter(Frames frames, List<FindingDTO> out, int page) {
        if (frames.hasTaggedAncestor()) {
            out.add(new FindingDTO(Severity.ERROR, ARTIFACT_INSIDE_TAGGED_CONTENT, page, null,
                    "Artifact is nested inside tagged content"));
        } else {
            out.add(new FindingDTO(Severity.PASSED, ARTIFACT_INSIDE_TAGGED_CONTENT, page, null));
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