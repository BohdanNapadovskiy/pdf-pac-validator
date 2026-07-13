package com.netralabs.logicalstructure.structureelements.headings;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
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
        // Untagged documents (no /StructTreeRoot at all) get N/A on this row per PAC:
        // there's no logical structure to check for heading-role mixing. A doc with
        // /StructTreeRoot but /MarkInfo /Marked=false (e.g. CalSAWS) still emits 1P —
        // vacuously the (empty of headings) tree doesn't mix H with Hn.
        if (StructUtils.structTreeRoot(pdf) == null) return;

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

        // Document-level check (mixing H and Hn is one global fact) — one finding per
        // document. A tagged doc with no headings at all still gets one PASSED —
        // vacuously the doc doesn't mix H and Hn. PAC's CalSAWS shows 1P here even
        // though it has no H/Hn elements.
        if (usedH[0] && usedHn[0]) {
            out.add(new FindingDTO(Severity.ERROR, USE_OF_EITHER, 0, null,
                    "Document mixes H and Hn heading roles"));
        } else {
            out.add(new FindingDTO(Severity.PASSED, USE_OF_EITHER, 0, null));
        }
    }
}
