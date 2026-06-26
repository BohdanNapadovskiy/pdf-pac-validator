package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.fonts.FontUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.ENCODING_SYMBOLIC;

public class ValidateTTSymbolicEncoding implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!isTrueTypeSimple(font)) return;
            if (!isSymbolic(font)) return;

            PdfObject enc = getEncoding(font);
            if (enc == null) return;            // legal: symbolic TT may omit /Encoding
            if (isIdentityName(enc)) return;    // also legal
            // Any other /Encoding on a symbolic TrueType font is a violation.
            out.add(new FindingDTO(Severity.ERROR, ENCODING_SYMBOLIC, page, null));
        });
        return out;
    }
}
