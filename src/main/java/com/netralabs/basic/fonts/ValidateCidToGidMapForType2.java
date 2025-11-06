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

import static com.netralabs.basic.fonts.FontUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.CID_GID_MAPPING;

public class ValidateCidToGidMapForType2 implements Rule {

    private static final PDFUACheckpoint CHECKPOINT = CID_GID_MAPPING;

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
                if (cid == null) return;

                PdfName cidSubtype = cid.getAsName(PdfName.Subtype);
                if (!PdfName.CIDFontType2.equals(cidSubtype)) return; // N/A for Type0 with CIDFontType0

                boolean ok = hasValidCidToGidMap(cid);
                if (ok) {
                    out.add(new FindingDTO(Severity.PASSED, CHECKPOINT, finalPage, null));
                } else {
                    out.add(new FindingDTO(Severity.ERROR, CHECKPOINT, finalPage, null));
                }
            });
        }
        return out;
    }
}
