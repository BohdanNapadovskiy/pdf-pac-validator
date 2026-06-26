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

import static com.netralabs.domain.PDFUACheckpoint.NESTING_HEADING_LEVEL;

public class ValidateNestingOfHeadingLevels implements Rule {


    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validate(pdf, out);
        return out;
    }

    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        final int[] prev = {-1};

        StructWalk.walk(pdf, (PdfStructElem el) -> {
            int lvl = StructWalk.headingLevel(pdf, el);
            if (lvl == -1) return;
            int page = StructUtils.pageNumOf(pdf, el.getPdfObject());
            // Emit one finding per heading visited so counts mirror PAC's per-element granularity.
            if (prev[0] != -1 && (lvl - prev[0]) > 1) {
                out.add(new FindingDTO(Severity.ERROR, NESTING_HEADING_LEVEL, page, null,
                        "Heading level skipped"));
            } else {
                out.add(new FindingDTO(Severity.PASSED, NESTING_HEADING_LEVEL, page, null));
            }
            prev[0] = lvl;
        });

        if (prev[0] == -1) {
            out.add(new FindingDTO(Severity.IGNORED, NESTING_HEADING_LEVEL, 0, null));
        }
    }
}
