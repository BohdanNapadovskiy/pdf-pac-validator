package com.netralabs.basic.fonts;

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
import static com.netralabs.domain.PDFUACheckpoint.ENCODING_SYMBOLIC;

public class ValidateTTSymbolicEncoding implements Rule {
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
                if (!isSymbolic(font)) return;                 // skip (handled by non-symbolic check)

                PdfObject enc = getEncoding(font);
                String label = fontResName(fname, font);

                if (enc == null) {
                    out.add(new FindingDTO(Severity.PASSED, ENCODING_SYMBOLIC, finalPage, null));
                    return;
                }
                if (isIdentityName(enc)) {
                    out.add(new FindingDTO(Severity.PASSED, ENCODING_SYMBOLIC, finalPage,
                            null));
                    return;
                }
                if (enc.isName()) {
                    out.add(new FindingDTO(Severity.ERROR, ENCODING_SYMBOLIC, finalPage, null));
                    return;
                }
                if (enc.isDictionary()) {
                    out.add(new FindingDTO(Severity.ERROR, ENCODING_SYMBOLIC, finalPage,
                            null));
                    return;
                }
            });
        }
        return out;
    }

}
