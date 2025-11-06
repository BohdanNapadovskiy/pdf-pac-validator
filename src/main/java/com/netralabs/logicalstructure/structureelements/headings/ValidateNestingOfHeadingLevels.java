package com.netralabs.logicalstructure.structureelements.headings;

import com.itextpdf.kernel.pdf.PdfDocument;
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

import static com.netralabs.domain.PDFUACheckpoint.NESTING_HEADING_LEVEL;

public class ValidateNestingOfHeadingLevels implements Rule {


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
        final int[] prev = {-1};
        final boolean[] sawAnyHeading = {false};
        final boolean[] hasError = {false};

        StructWalk.walk(pdf, el -> {
            int lvl = StructWalk.headingLevel(pdf, el);
            if (lvl == -1) return;
            sawAnyHeading[0] = true;

            if (prev[0] != -1 && (lvl - prev[0]) > 1) {
                hasError[0] = true;
                out.add(new FindingDTO(
                        Severity.ERROR,
                        NESTING_HEADING_LEVEL,
                        0,
                        null));
            }
            prev[0] = lvl;
        });

        if (!sawAnyHeading[0]) {
            out.add(new FindingDTO(Severity.IGNORED, NESTING_HEADING_LEVEL, 0, null));
        } else if (!hasError[0]) {
            out.add(new FindingDTO(Severity.PASSED, NESTING_HEADING_LEVEL, 0, null));
        }
    }
}
