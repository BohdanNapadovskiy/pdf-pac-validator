package com.netralabs.basic.naturallanguage;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.naturallanguage.ActualTextHelper.*;
import static com.netralabs.basic.naturallanguage.LangUtils.docLang;
import static com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATE_NAMES_FORM_FIELD;

public class ValidateLangOfFormFieldAltNames implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        validateLanguageOfAlternateNames(ctx.pdf(), out);
        return out;
    }



    public void validateLanguageOfAlternateNames(PdfDocument pdf, List<FindingDTO> out) {
        final String docLang = docLang(pdf);
        PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
        if (acro == null) return;

        for (PdfFormField field : acro.getAllFormFields().values()) {
            PdfString tu = field.getAlternativeName();
            if (tu == null || tu.getValue().isBlank())
                continue;
            String fieldLang = LangUtils.pdfStringValue(field.getPdfObject().getAsString(PdfName.Lang));
            String pageLang = null;
            int pageNum = 0;
            List<PdfWidgetAnnotation> widgets = field.getWidgets();
            if (widgets != null && !widgets.isEmpty()) {
                PdfWidgetAnnotation w = widgets.get(0);
                PdfDictionary pgDict = w.getPageObject();
                if (pgDict != null) {
                    PdfPage page = pdf.getPage(pgDict);
                    if (page != null) {
                        pageNum  = pdf.getPageNumber(page);
                        pageLang = pageLangOf(page.getPdfObject());
                    }
                }
            }

            // PAC-parity: only emit when the form field carries an explicit /Lang
            // override on its own dict. Fields inheriting the doc /Lang aren't tallied
            // on this row — they're implicitly covered by "Natural language of text
            // objects". Verified: Filled_Graduate has 27 form fields all inheriting
            // doc EN-US and PAC's row is dashed; emitting for inheritors added a
            // spurious +27 passes.
            if (fieldLang == null || fieldLang.isBlank()) continue;
            addLangFinding(out, fieldLang, NATURAL_LANGUAGE_ALTERNATE_NAMES_FORM_FIELD, pageNum);
        }
    }
}
