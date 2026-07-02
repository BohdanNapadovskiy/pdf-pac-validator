package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.netralabs.basic.content.ContentWalker.walkPage;
import static com.netralabs.domain.PDFUACheckpoint.MAPPING_OF_CHARACTER_TO_UNICODE;

public class ValidateUnicodeMapping implements Rule {

    private enum Scope { ARTIFACT, TAGGED_MCID, OTHER_MARKED }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;

            walkPage(pdf, page, new Hook() {
                private final Deque<Scope> stack = new ArrayDeque<>();

                @Override public void onBeginArtifact()                              { stack.push(Scope.ARTIFACT); }
                @Override public void onBeginTaggedMcid(int mcid, PdfDictionary pg)  { stack.push(Scope.TAGGED_MCID); }
                @Override public void onBeginOtherMarked(PdfName tag)                { stack.push(Scope.OTHER_MARKED); }
                @Override public void onEndMarked()                                   { if (!stack.isEmpty()) stack.pop(); }

                @Override
                public void onShowText(PdfString s, BBoxDTO bbox) {
                    // Only count text-showing operators whose immediately-enclosing scope is
                    // a tagged MCID (matches PAC's per-text-object granularity, which drops
                    // text inside nested BMC / OtherMarked / Artifact scopes).
                    if (stack.peek() != Scope.TAGGED_MCID) return;
                    String uni = s.toUnicodeString();
                    if (uni == null || uni.isEmpty() || uni.indexOf('\uFFFD') >= 0) {
                        out.add(new FindingDTO(Severity.ERROR, MAPPING_OF_CHARACTER_TO_UNICODE, pageNum, bbox,
                                "Character has no Unicode mapping"));
                    } else {
                        out.add(new FindingDTO(Severity.PASSED, MAPPING_OF_CHARACTER_TO_UNICODE, pageNum, null));
                    }
                }
            });
        }
        return out;
    }
}
