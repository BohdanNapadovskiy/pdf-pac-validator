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

import static com.netralabs.basic.fonts.FontUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.CID_GID_MAPPING;

public class ValidateCidToGidMapForType2 implements Rule {

    private static final PDFUACheckpoint CHECKPOINT = CID_GID_MAPPING;

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) return;
            PdfDictionary cid = firstDescendantCidFont(font);
            if (cid == null) return;
            if (!PdfName.CIDFontType2.equals(cid.getAsName(PdfName.Subtype))) return; // not Type 2 CID

            if (hasValidCidToGidMap(cid)) {
                out.add(new FindingDTO(Severity.PASSED, CHECKPOINT, page, null));
            } else {
                out.add(new FindingDTO(Severity.ERROR, CHECKPOINT, page, null));
            }
        });
        return out;
    }
}
