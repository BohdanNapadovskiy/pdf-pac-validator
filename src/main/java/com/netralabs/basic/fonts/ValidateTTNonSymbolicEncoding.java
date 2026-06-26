package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.fonts.FontUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.ENCODING_ENTRY;

public class ValidateTTNonSymbolicEncoding implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!isTrueTypeSimple(font)) return;
            if (isSymbolic(font)) return;

            PdfObject enc = getEncoding(font);
            if (enc == null) {
                out.add(new FindingDTO(Severity.ERROR, ENCODING_ENTRY, page, null));
                return;
            }
            if (isWinAnsiOrMacRomanName(enc)) return;
            if (enc.isDictionary() && baseIsWinAnsiOrMacRoman((PdfDictionary) enc)) return;
            out.add(new FindingDTO(Severity.ERROR, ENCODING_ENTRY, page, null));
        });
        return out;
    }
}
