package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;

import static com.netralabs.basic.content.ContentWalker.walkPage;
import static com.netralabs.domain.PDFUACheckpoint.TAGGED_CONTENT_INSIDE_ARTIFACT;

public class ValidateTaggedInsideArtifacts implements Rule {
    private static final boolean STRICT = false; // set true to treat as error

    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final Deque<String> scope = new ArrayDeque<>();
            final long[] count = {0};

            walkPage(pdf, page, new Hook() {
                @Override
                public void onBeginArtifact() {
                    scope.push("A");
                }

                @Override
                public void onBeginOtherMarked(PdfName tag) {
                    scope.push("O");
                }

                @Override
                public void onEndMarked() {
                    if (!scope.isEmpty()) scope.pop();
                }

                @Override
                public void onBeginTaggedMcid(int mcid, PdfDictionary pg) {
                    boolean inArtifact = scope.contains("A");
                    if (inArtifact) count[0]++;
                    scope.push("T");
                }
            });

            if (count[0] > 0) {
                out.add(new FindingDTO(Severity.PASSED, TAGGED_CONTENT_INSIDE_ARTIFACT, page, null));
                if (STRICT) {
                    out.add(new FindingDTO(Severity.ERROR, TAGGED_CONTENT_INSIDE_ARTIFACT, page, null));
                }
            }
        }
        return out;
    }
}
