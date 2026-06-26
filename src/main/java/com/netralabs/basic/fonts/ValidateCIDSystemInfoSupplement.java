package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.fonts.FontUtils.firstDescendantCidFont;
import static com.netralabs.basic.fonts.FontUtils.forEachUniqueFont;
import static com.netralabs.domain.PDFUACheckpoint.SUPPLEMENT_ENTRIES;

public class ValidateCIDSystemInfoSupplement implements Rule {

    private static final PDFUACheckpoint CHECKPOINT = SUPPLEMENT_ENTRIES;

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) return;
            PdfDictionary cid = firstDescendantCidFont(font);
            if (cid == null) return;
            PdfDictionary csi = cid.getAsDictionary(PdfName.CIDSystemInfo);
            if (csi == null || csi.getAsNumber(PdfName.Supplement) == null) {
                out.add(new FindingDTO(Severity.ERROR, CHECKPOINT, page, null));
            }
        });
        return out;
    }
}
