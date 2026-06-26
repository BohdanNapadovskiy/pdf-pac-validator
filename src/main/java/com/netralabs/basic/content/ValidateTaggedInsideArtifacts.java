package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.netralabs.basic.content.ContentWalker.walkPage;
import static com.netralabs.domain.PDFUACheckpoint.TAGGED_CONTENT_INSIDE_ARTIFACT;

public class ValidateTaggedInsideArtifacts implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;
            final Deque<String> scope = new ArrayDeque<>();
            final boolean[] flagNextPaint = {false};

            walkPage(pdf, page, new Hook() {
                @Override public void onBeginArtifact()                              { scope.push("A"); }
                @Override public void onBeginOtherMarked(PdfName tag)                { scope.push("O"); }
                @Override public void onEndMarked()                                   { if (!scope.isEmpty()) scope.pop(); }

                @Override
                public void onBeginTaggedMcid(int mcid, PdfDictionary pg) {
                    if (scope.contains("A")) {
                        flagNextPaint[0] = true;
                    } else {
                        out.add(new FindingDTO(Severity.PASSED, TAGGED_CONTENT_INSIDE_ARTIFACT, pageNum, null));
                    }
                    scope.push("T");
                }

                @Override
                public void onPainted(BBoxDTO bbox) {
                    if (flagNextPaint[0]) {
                        out.add(new FindingDTO(Severity.ERROR, TAGGED_CONTENT_INSIDE_ARTIFACT, pageNum, bbox,
                                "Tagged content is nested inside an artifact"));
                        flagNextPaint[0] = false;
                    }
                }
            });
        }
        return out;
    }
}
