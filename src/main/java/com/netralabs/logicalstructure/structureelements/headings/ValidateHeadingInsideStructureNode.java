package com.netralabs.logicalstructure.structureelements.headings;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.H_STRUCTURE_ELEMENTS_WITHIN;
import static com.netralabs.domain.PDFUACheckpoint.NESTING_HEADING_LEVEL;

public class ValidateHeadingInsideStructureNode implements Rule {
    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validate(pdf, out);
        return out;

    }

    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        final boolean[] sawAnyHeading = {false};
        final boolean[] hasError = {false};

        StructWalk.walk(pdf, el -> {
            int lvl = StructWalk.headingLevel(pdf, el);
            if (lvl == -1) return; // not a heading
            sawAnyHeading[0] = true;

            if (!StructWalk.parentIsStructureNode(pdf, el)) {
                hasError[0] = true;
                out.add(new FindingDTO(
                        Severity.ERROR,
                        H_STRUCTURE_ELEMENTS_WITHIN,
                        0,
                        null));
            }
        });

        if (!sawAnyHeading[0]) {
            out.add(new FindingDTO(Severity.IGNORED, H_STRUCTURE_ELEMENTS_WITHIN, 0, null));
        } else if (!hasError[0]) {
            out.add(new FindingDTO(Severity.PASSED, H_STRUCTURE_ELEMENTS_WITHIN, 0, null));
        }
    }
}
