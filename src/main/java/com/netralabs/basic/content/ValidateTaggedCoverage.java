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
import static com.netralabs.domain.PDFUACheckpoint.TAGGED_CONTENT_ARTIFACTS;

public class ValidateTaggedCoverage implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;
            final Deque<String> scope = new ArrayDeque<>();

            walkPage(pdf, page, new Hook() {
                @Override public void onBeginArtifact()                              { scope.push("A"); }
                @Override public void onBeginTaggedMcid(int mcid, PdfDictionary pg)  { scope.push("T"); }
                @Override public void onBeginOtherMarked(PdfName tag)                { scope.push("O"); }
                @Override public void onEndMarked()                                   { if (!scope.isEmpty()) scope.pop(); }

                @Override
                public void onPainted(BBoxDTO bbox) {
                    boolean inTagged = scope.contains("T");
                    boolean inArtifact = scope.contains("A");
                    if (inTagged || inArtifact) {
                        out.add(new FindingDTO(Severity.PASSED, TAGGED_CONTENT_ARTIFACTS, pageNum, null));
                    } else {
                        out.add(new FindingDTO(Severity.ERROR, TAGGED_CONTENT_ARTIFACTS, pageNum, bbox,
                                "Object is not tagged"));
                    }
                }
            });
        }
        return out;
    }
}
