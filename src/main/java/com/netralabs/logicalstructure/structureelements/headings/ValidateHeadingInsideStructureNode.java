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
        // PAC "H structure elements within a structure node" applies only to the
        // generic /H tag (not H1..H6): H1..H6 have explicit level semantics and are
        // covered by other heading checkpoints. On docs with no generic /H, PAC
        // reports NA — mirror that by emitting nothing here.
        StructWalk.walk(pdf, (PdfStructElem el) -> {
            if (!"H".equals(StructWalk.normRole(pdf, el))) return;
            int page = StructUtils.pageNumOf(pdf, el.getPdfObject());
            if (!StructWalk.parentIsStructureNode(pdf, el)) {
                out.add(new FindingDTO(Severity.ERROR, H_STRUCTURE_ELEMENTS_WITHIN, page, null,
                        "Heading is not inside a Document/Part/Art/Sect/Div structure node"));
            } else {
                out.add(new FindingDTO(Severity.PASSED, H_STRUCTURE_ELEMENTS_WITHIN, page, null));
            }
        });
    }
}
