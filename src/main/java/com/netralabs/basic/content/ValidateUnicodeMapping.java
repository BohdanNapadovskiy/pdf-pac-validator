package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.basic.content.ContentWalker.walkPage;
import static com.netralabs.domain.PDFUACheckpoint.MAPPING_OF_CHARACTER_TO_UNICODE;

public class ValidateUnicodeMapping implements Rule {
    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final long[] mapped = {0};
            final long[] unmappedChunks = {0};

            Hook hook = new Hook() {
                @Override
                public void onShowText(PdfString s) {
                    count(s);
                }

                @Override
                public void onShowTextArray(PdfArray arr) {
                    for (int i = 0; i < arr.size(); i++) if (arr.get(i).isString()) count((PdfString) arr.get(i));
                }

                private void count(PdfString s) {
                    String uni = s.toUnicodeString(); // iText maps using ToUnicode / encoding; unknown → U+FFFD
                    if (uni == null || uni.isEmpty()) {
                        unmappedChunks[0]++;
                        return;
                    }
                    boolean hadMapped = false;
                    for (int i = 0; i < uni.length(); i++) {
                        char c = uni.charAt(i);
                        if (c != '\uFFFD') {
                            mapped[0]++;
                            hadMapped = true;
                        }
                    }
                    if (!hadMapped) unmappedChunks[0]++;
                }
            };

            walkPage(pdf, page, hook);

            if (mapped[0] > 0) {
                out.add(new FindingDTO(Severity.PASSED, MAPPING_OF_CHARACTER_TO_UNICODE, page, null));
            }
            if (unmappedChunks[0] > 0) {
                out.add(new FindingDTO(Severity.ERROR, MAPPING_OF_CHARACTER_TO_UNICODE, page, null));
            }
        }
        return out;
    }
}
