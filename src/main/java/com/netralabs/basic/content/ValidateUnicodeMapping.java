package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.content.ContentWalker.walkPage;
import static com.netralabs.domain.PDFUACheckpoint.MAPPING_OF_CHARACTER_TO_UNICODE;

public class ValidateUnicodeMapping implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;

            walkPage(pdf, page, new Hook() {
                @Override
                public void onShowText(PdfString s, BBoxDTO bbox) {
                    String uni = s.toUnicodeString();
                    boolean unmapped = uni == null || uni.isEmpty();
                    if (!unmapped) {
                        unmapped = true;
                        for (int i = 0; i < uni.length(); i++) {
                            if (uni.charAt(i) != '\uFFFD') { unmapped = false; break; }
                        }
                    }
                    if (unmapped) {
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
