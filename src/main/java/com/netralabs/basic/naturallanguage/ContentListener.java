package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;
import java.util.function.BiConsumer;

import static com.itextpdf.kernel.pdf.canvas.parser.EventType.BEGIN_TEXT;
import static com.netralabs.basic.naturallanguage.LangUtils.isValidBCP47;

public class ContentListener  implements IEventListener {
    private final List<FindingDTO> out;
    private final int pageNum;
    private final String docLang;

    private final Deque<String> langStack = new ArrayDeque<>();
    private final Deque<Boolean> langPushedStack = new ArrayDeque<>();
    private final Set<Integer> seenMcids = new HashSet<>();

    private int markedDepth = 0;
    private boolean inArtifact = false;
    private Integer currentMcid = null;

    // For untagged content counting
    private boolean blockCounted = false;  // inside a non-artifact BMC without MCID
    private boolean inBareRun = false;     // outside any BMC

    ContentListener(List<FindingDTO> out, int pageNum, String docLang) {
        this.out = out; this.pageNum = pageNum; this.docLang = docLang;
    }
    void beginMarked(String tag, PdfDictionary props) {
        markedDepth++;
        inArtifact = "Artifact".equals(tag);
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
            PdfString l = props.getAsString(PdfName.Lang);
            if (l != null) { langStack.push(l.getValue()); pushed = true; }
        }
        langPushedStack.push(pushed);
    }

    void endMarked() {
        if (!langPushedStack.isEmpty() && Boolean.TRUE.equals(langPushedStack.pop())) {
            if (!langStack.isEmpty()) langStack.pop();
        }
        markedDepth = Math.max(0, markedDepth - 1);
        if (markedDepth == 0) {
            inArtifact = false;
            currentMcid = null;
            blockCounted = false;
        }
        inBareRun = false; // boundary between bare runs
    }

    @Override
    public void eventOccurred(IEventData data, EventType type) {
        switch (type) {
            case RENDER_TEXT: {
                // PAC's "Natural language of text objects" only counts tagged real content:
                //  - artifacts are excluded (no lang requirement per PDF/UA)
                //  - untagged bare text is reported by a different checkpoint
                if (inArtifact) break;
                if (currentMcid == null) break;
                emitFinding(resolveLang());
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

    private void emitFinding(String lang) {
        if (lang == null || lang.isBlank() || !isValidBCP47(lang)) {
            out.add(new FindingDTO(Severity.ERROR,
                    com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_TEXT_OBJECT, pageNum, null));
        } else {
            out.add(new FindingDTO(Severity.PASSED,
                    com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_TEXT_OBJECT, pageNum, null));
        }
    }
}
