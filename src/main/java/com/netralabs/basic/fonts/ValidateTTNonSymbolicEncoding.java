package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.basic.fonts.FontUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.ENCODING_ENTRY;

public class ValidateTTNonSymbolicEncoding implements Rule {

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
                if (!isTrueTypeSimple(font)) return;           // skip
                if (isSymbolic(font)) return;                  // skip (handled by symbolic check)

                PdfObject enc = getEncoding(font);
                String label = fontResName(fname, font);

                if (enc == null) {
                    out.add(new FindingDTO(Severity.ERROR, ENCODING_ENTRY, finalPage, null));
                    return;
                }
                if (isWinAnsiOrMacRomanName(enc)) {
                    out.add(new FindingDTO(Severity.PASSED, ENCODING_ENTRY, finalPage,
                            null));
                    return;
                }
                if (enc.isDictionary()) {
                    PdfDictionary encDict = (PdfDictionary) enc;
                    if (baseIsWinAnsiOrMacRoman(encDict)) {
                        out.add(new FindingDTO(Severity.PASSED, ENCODING_ENTRY, finalPage, null));
                    } else {
                        out.add(new FindingDTO(Severity.ERROR, ENCODING_ENTRY, finalPage,
                                null));
                    }
                    return;
                }
                out.add(new FindingDTO(Severity.ERROR, ENCODING_ENTRY, finalPage,
                        null));
            });
        }
        return out;
    }
}
