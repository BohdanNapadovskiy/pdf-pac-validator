package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.basic.fonts.FontUtils.firstDescendantCidFont;
import static com.netralabs.basic.fonts.FontUtils.forEachFontOnPage;
import static com.netralabs.domain.PDFUACheckpoint.REGISTRY_ENTRIES;

public class ValidateCIDSystemInfoRegistry implements Rule {


    private static final PDFUACheckpoint CHECKPOINT = REGISTRY_ENTRIES;

    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            int finalPage = page;
            forEachFontOnPage(pdf, page, (fname, font) -> {
                if (!PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) return;
                PdfDictionary cid = firstDescendantCidFont(font);
                if (cid == null) return; // N/A
                PdfDictionary csi = cid.getAsDictionary(PdfName.CIDSystemInfo);
                if (csi == null || csi.getAsString(PdfName.Registry) == null) {
                    out.add(new FindingDTO(Severity.ERROR, CHECKPOINT, finalPage, null));
                } else {
                    out.add(new FindingDTO(Severity.PASSED, CHECKPOINT, finalPage, null));
                }
            });
        }
        return out;
    }

}
