package com.netralabs.logicalstructure.structureelements.headings;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.USE_OF_EITHER;

public class ValidateUseOfEitherHOrHn implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validate(pdf, out);
        return out;
    }


    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        boolean[] usedH = {false};
        boolean[] usedHn = {false};

        StructWalk.walk(pdf, (PdfStructElem el) -> {
            String role = StructWalk.normRole(pdf, el);
            if (role == null) return;
            if ("H".equals(role)) usedH[0] = true;
            else if (role.length() == 2 && role.charAt(0) == 'H' && Character.isDigit(role.charAt(1))) {
                usedHn[0] = true;
            }
        });

        // Document-level check (mixing H and Hn is one global fact) — one finding per document.
        // Only emit when the doc actually uses at least one heading role — PAC shows this
        // row as N/A (dashed) when no headings exist.
        if (!usedH[0] && !usedHn[0]) return;
        if (usedH[0] && usedHn[0]) {
            out.add(new FindingDTO(Severity.ERROR, USE_OF_EITHER, 0, null,
                    "Document mixes H and Hn heading roles"));
        } else {
            out.add(new FindingDTO(Severity.PASSED, USE_OF_EITHER, 0, null));
        }
    }
}
