package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.fonts.FontUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.GLYPH_NAMES;

public class ValidateTTNonSymbolicGlyphNames implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!isTrueTypeSimple(font)) return;
            if (isSymbolic(font)) return;

            // ToUnicode CMap provides robust text mapping irrespective of glyph names.
            if (font.get(PdfName.ToUnicode) != null) return;

            PdfObject enc = getEncoding(font);
            if (isWinAnsiOrMacRomanName(enc)) return;
            if (enc != null && enc.isDictionary() && baseIsWinAnsiOrMacRoman((PdfDictionary) enc)) return;

            // No /ToUnicode AND no standard encoding → cannot infer AGL-compliant glyph names.
            out.add(new FindingDTO(Severity.ERROR, GLYPH_NAMES, page, null));
        });
        return out;
    }
}
