package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
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
import static com.netralabs.domain.PDFUACheckpoint.GLYPH_NAMES;

public class ValidateTTNonSymbolicGlyphNames implements Rule {

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
                if (isSymbolic(font)) return;                  // skip

                String label = fontResName(fname, font);

                // Prefer /ToUnicode presence for robust text mapping
                if (font.get(PdfName.ToUnicode) != null) {
                    out.add(new FindingDTO(Severity.PASSED, GLYPH_NAMES, finalPage,
                            null));
                    return;
                }

                // Fallback: standard encodings imply standard glyph names
                PdfObject enc = getEncoding(font);
                if (isWinAnsiOrMacRomanName(enc)) {
                    out.add(new FindingDTO(Severity.PASSED, GLYPH_NAMES, finalPage,
                            null));
                    return;
                }
                if (enc != null && enc.isDictionary() && baseIsWinAnsiOrMacRoman((PdfDictionary) enc)) {
                    out.add(new FindingDTO(Severity.PASSED, GLYPH_NAMES, finalPage, null));
                    return;
                }

                // Otherwise we cannot infer AGL-compliant names → require ToUnicode
                out.add(new FindingDTO(Severity.ERROR, GLYPH_NAMES, finalPage, null));
            });
        }
        return out;
    }
}
