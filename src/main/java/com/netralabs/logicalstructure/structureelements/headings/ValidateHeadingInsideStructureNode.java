package com.netralabs.logicalstructure.structureelements.headings;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.H_STRUCTURE_ELEMENTS_WITHIN;

public class ValidateHeadingInsideStructureNode implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validate(pdf, out);
        return out;

    }

    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        final boolean[] sawAnyHeading = {false};

        StructWalk.walk(pdf, (PdfStructElem el) -> {
            int lvl = StructWalk.headingLevel(pdf, el);
            if (lvl == -1) return; // not a heading
            sawAnyHeading[0] = true;
            int page = StructUtils.pageNumOf(pdf, el.getPdfObject());
            // Emit one finding per heading visited so counts mirror PAC's per-element granularity.
            if (!StructWalk.parentIsStructureNode(pdf, el)) {
                out.add(new FindingDTO(Severity.ERROR, H_STRUCTURE_ELEMENTS_WITHIN, page, null,
                        "Heading is not inside a Document/Part/Art/Sect/Div structure node"));
            } else {
                out.add(new FindingDTO(Severity.PASSED, H_STRUCTURE_ELEMENTS_WITHIN, page, null));
            }
        });

        if (!sawAnyHeading[0]) {
            out.add(new FindingDTO(Severity.IGNORED, H_STRUCTURE_ELEMENTS_WITHIN, 0, null));
        }
    }
}
