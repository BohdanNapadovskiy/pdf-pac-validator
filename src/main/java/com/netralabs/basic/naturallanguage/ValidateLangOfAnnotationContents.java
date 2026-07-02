package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.naturallanguage.ActualTextHelper.*;
import static com.netralabs.basic.naturallanguage.LangUtils.docLang;
import static com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_CONTENTS;

public class ValidateLangOfAnnotationContents implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        validateLanguageOfAnnotationContents(ctx.pdf(), out);
        return out;
    }

    public void validateLanguageOfAnnotationContents(PdfDocument pdf, List<FindingDTO> out) {
        final String docLang = docLang(pdf);

        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfPage page = pdf.getPage(i);
            String pageLang = pageLangOf(page.getPdfObject());

            for (PdfAnnotation a : page.getAnnotations()) {
                PdfString contents = a.getContents();
                if (contents == null || contents.getValue().isBlank())
                    continue;
                String annotLang = LangUtils.pdfStringValue(a.getPdfObject().getAsString(PdfName.Lang));
                String effective = firstNonBlank(annotLang, pageLang, docLang);
                addLangFinding(out, effective, NATURAL_LANGUAGE_CONTENTS, i);
            }
        }
    }
}
