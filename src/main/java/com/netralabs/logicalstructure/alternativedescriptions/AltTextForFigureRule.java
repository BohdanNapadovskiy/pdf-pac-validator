package com.netralabs.logicalstructure.alternativedescriptions;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits PASSED for every Figure structure element with a non-empty /Alt or /ActualText.
 * Errors are produced by veraPDF rule 7.3-1; this rule only contributes the PASSED counts
 * so the checkpoint reports the same shape PAC does (e.g. 3 passes + 53 errors).
 */
public class AltTextForFigureRule implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<FindingDTO> out = new ArrayList<>();

        StructWalk.walk(pdf, elem -> {
            String role = StructWalk.normRole(pdf, elem);
            if (!"Figure".equals(role)) return;

            var obj = elem.getPdfObject();
            PdfString alt = obj.getAsString(PdfName.Alt);
            PdfString actual = obj.getAsString(PdfName.ActualText);
            boolean hasAlt = alt != null && !alt.getValue().isEmpty();
            boolean hasActual = actual != null && !actual.getValue().isEmpty();

            if (hasAlt || hasActual) {
                int page = StructUtils.pageNumOf(pdf, obj);
                out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.ALTERNATIVE_TEXT_FOR_FIGURE, page, null));
            }
        });
        return out;
    }
}
