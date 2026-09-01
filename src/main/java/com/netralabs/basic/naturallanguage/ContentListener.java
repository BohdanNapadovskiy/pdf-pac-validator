package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.*;

import static com.netralabs.basic.naturallanguage.LangUtils.isValidBCP47;

public class ContentListener  implements IEventListener {
    private final List<FindingDTO> out;
    private final int pageNum;
    private final String docLang;
    private final boolean taggedPdf;

    private final Deque<String> langStack = new ArrayDeque<>();
    private final Deque<Boolean> langPushedStack = new ArrayDeque<>();
    private final Deque<Boolean> artifactPushedStack = new ArrayDeque<>();
    /** Per-frame flag: true when the BMC/BDC is a {@code /Artifact} carrying an explicit
     * {@code /Type} property (Pagination, Page, Layout, Background). Used to skip
     * text-show events whose innermost frame is such a "classified" artifact — PAC
     * excludes them from text-count rows. */
    private final Deque<Boolean> typedArtifactPushedStack = new ArrayDeque<>();
    private final Set<Integer> seenMcids = new HashSet<>();

    private int markedDepth = 0;
    private int artifactDepth = 0;
    private Integer currentMcid = null;

    // For untagged content counting
    private boolean blockCounted = false;  // inside a non-artifact BMC without MCID
    private boolean inBareRun = false;     // outside any BMC

    ContentListener(List<FindingDTO> out, int pageNum, String docLang) {
        this(out, pageNum, docLang, true);
    }

    ContentListener(List<FindingDTO> out, int pageNum, String docLang, boolean taggedPdf) {
        this.out = out; this.pageNum = pageNum; this.docLang = docLang; this.taggedPdf = taggedPdf;
    }
    void beginMarked(String tag, PdfDictionary props) {
        markedDepth++;
        if ("Artifact".equals(tag)) artifactDepth++;
        currentMcid = null;
        blockCounted = false; // fresh block
        if (props != null) {
            PdfNumber n = props.getAsNumber(PdfName.MCID);
            if (n != null) {
                currentMcid = n.intValue();
                seenMcids.add(currentMcid);
            }
        }
        boolean pushed = false;
        if (props != null) {
            String l = LangUtils.pdfStringValue(props.getAsString(PdfName.Lang));
            if (l != null && !l.isBlank()) { langStack.push(l); pushed = true; }
        }
        langPushedStack.push(pushed);
        // Record whether this BMC/BDC frame is an /Artifact so endMarked can decrement.
        artifactPushedStack.push("Artifact".equals(tag));
        // Classified artifact: /Artifact BDC with an explicit /Type property.
        typedArtifactPushedStack.push("Artifact".equals(tag)
                && props != null && props.get(PdfName.Type) != null);
    }

    void endMarked() {
        if (!langPushedStack.isEmpty() && Boolean.TRUE.equals(langPushedStack.pop())) {
            if (!langStack.isEmpty()) langStack.pop();
        }
        if (!artifactPushedStack.isEmpty() && Boolean.TRUE.equals(artifactPushedStack.pop())) {
            artifactDepth = Math.max(0, artifactDepth - 1);
        }
        if (!typedArtifactPushedStack.isEmpty()) typedArtifactPushedStack.pop();
        markedDepth = Math.max(0, markedDepth - 1);
        if (markedDepth == 0) {
            currentMcid = null;
            blockCounted = false;
        }
        inBareRun = false; // boundary between bare runs
    }

    @Override
    public void eventOccurred(IEventData data, EventType type) {
        switch (type) {
            case RENDER_TEXT: {
                // PAC counts every text-showing operator on the page, including
                // /Artifact-scoped text — verified against Filled_Graduate where
                // PAC's 3756 count matches raw processPageContent event count and
                // our previous artifact-excluded 3676 was 80 events short.
                //
                // Exception: skip when the innermost frame is a "classified"
                // Artifact BDC (an /Artifact with an explicit /Type property —
                // Pagination / Page / Layout / Background). Complex has one such
                // event on page 2 (page-number "4"); PAC excludes it.
                // On untagged docs, artifact-typing is meaningless — PAC counts every
                // text-show uniformly. See ValidateUnicodeMapping for the same guard.
                if (taggedPdf
                        && !typedArtifactPushedStack.isEmpty()
                        && Boolean.TRUE.equals(typedArtifactPushedStack.peek())) {
                    break;
                }
                emitFinding(resolveLang(), (TextRenderInfo) data);
                break;
            }
            case RENDER_PATH:
            case CLIP_PATH_CHANGED:
            case BEGIN_TEXT:
            case END_TEXT:
                inBareRun = false;
                break;

            case RENDER_IMAGE:
                inBareRun = false;
                break;
        }
    }

    @Override
    public Set<EventType> getSupportedEvents() {
        return EnumSet.allOf(EventType.class);
    }

    private String resolveLang() {
        if (!langStack.isEmpty()) return langStack.peek();
        return docLang;
    }

    private void emitFinding(String lang, TextRenderInfo ti) {
        BBoxDTO bbox = bboxOf(ti);
        Severity sev = (lang == null || lang.isBlank() || !isValidBCP47(lang))
                ? Severity.ERROR : Severity.PASSED;
        out.add(new FindingDTO(sev,
                com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_TEXT_OBJECT, pageNum, bbox));
    }

    /**
     * Text bbox from the baseline / ascent / descent segments. Same computation as
     * {@code ContentWalker.bboxOf(TextRenderInfo)}; duplicated locally so this listener
     * stays self-contained (it isn't driven by ContentWalker).
     */
    private static BBoxDTO bboxOf(TextRenderInfo ti) {
        LineSegment baseline = ti.getBaseline();
        LineSegment ascent = ti.getAscentLine();
        LineSegment descent = ti.getDescentLine();
        float minX = min6(
                baseline.getStartPoint().get(0), baseline.getEndPoint().get(0),
                ascent.getStartPoint().get(0),   ascent.getEndPoint().get(0),
                descent.getStartPoint().get(0),  descent.getEndPoint().get(0));
        float maxX = max6(
                baseline.getStartPoint().get(0), baseline.getEndPoint().get(0),
                ascent.getStartPoint().get(0),   ascent.getEndPoint().get(0),
                descent.getStartPoint().get(0),  descent.getEndPoint().get(0));
        float minY = min6(
                baseline.getStartPoint().get(1), baseline.getEndPoint().get(1),
                ascent.getStartPoint().get(1),   ascent.getEndPoint().get(1),
                descent.getStartPoint().get(1),  descent.getEndPoint().get(1));
        float maxY = max6(
                baseline.getStartPoint().get(1), baseline.getEndPoint().get(1),
                ascent.getStartPoint().get(1),   ascent.getEndPoint().get(1),
                descent.getStartPoint().get(1),  descent.getEndPoint().get(1));
        return new BBoxDTO(maxY, minX, maxY - minY, maxX - minX);
    }

    private static float min6(float a, float b, float c, float d, float e, float f) {
        return Math.min(Math.min(Math.min(a, b), Math.min(c, d)), Math.min(e, f));
    }

    private static float max6(float a, float b, float c, float d, float e, float f) {
        return Math.max(Math.max(Math.max(a, b), Math.max(c, d)), Math.max(e, f));
    }
}
