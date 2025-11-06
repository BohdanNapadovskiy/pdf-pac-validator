package com.netralabs.logicalstructure.structureelements.headings;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagutils.TagStructureContext;
import com.itextpdf.kernel.pdf.tagutils.TagTreeIterator;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.FIRST_HEADING_LEVEL;

public class ValidateFirstHeadingLevel implements Rule {

    @Override
    public EnumSet<Phase> phases() {
        return null;
    }

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
        boolean[] found = { false };
        StructWalk.walk(pdf, (PdfStructElem el) -> {
            if (found[0])
                return;
            int lvl = StructWalk.headingLevel(pdf, el);
            if (lvl != -1) {
                found[0] = true;
                if (lvl != 1) {
                    out.add(new FindingDTO(Severity.ERROR, FIRST_HEADING_LEVEL, 0, null));
                }
            } else {
                out.add(new FindingDTO(Severity.IGNORED, FIRST_HEADING_LEVEL, 0, null));
            }
        });
        if (!found[0]) {
            out.add(new FindingDTO(Severity.PASSED, FIRST_HEADING_LEVEL, 0, null));
        }
    }

}
