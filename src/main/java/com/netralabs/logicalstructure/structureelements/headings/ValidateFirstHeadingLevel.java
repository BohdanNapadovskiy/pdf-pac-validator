package com.netralabs.logicalstructure.structureelements.headings;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.FIRST_HEADING_LEVEL;

public class ValidateFirstHeadingLevel implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validate(pdf, out);
        return out;
    }

    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        PdfStructTreeRoot root = pdf.getStructTreeRoot();
        if (root == null || root.getPdfObject() == null) {
            out.add(new FindingDTO(Severity.IGNORED, FIRST_HEADING_LEVEL, 0, null));
            return;
        }
        int[] firstLevel = { -1 };
        int[] firstPage = { 0 };
        StructWalk.walk(pdf, (PdfStructElem el) -> {
            if (firstLevel[0] != -1) return;
            int lvl = StructWalk.headingLevel(pdf, el);
            if (lvl == -1) return;
            firstLevel[0] = lvl;
            firstPage[0] = StructUtils.pageNumOf(pdf, el.getPdfObject());
        });
        if (firstLevel[0] == -1) {
            out.add(new FindingDTO(Severity.IGNORED, FIRST_HEADING_LEVEL, 0, null));
            return;
        }
        // Document-level check (one fact: is the very first heading H1?) — one finding per document.
        if (firstLevel[0] == 1) {
            out.add(new FindingDTO(Severity.PASSED, FIRST_HEADING_LEVEL, firstPage[0], null));
        } else {
            out.add(new FindingDTO(Severity.ERROR, FIRST_HEADING_LEVEL, firstPage[0], null,
                    "First heading level is not H1"));
        }
    }

}
