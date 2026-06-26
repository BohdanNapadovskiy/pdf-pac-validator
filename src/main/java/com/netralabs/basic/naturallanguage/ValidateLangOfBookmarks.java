package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;

import static com.netralabs.basic.naturallanguage.ActualTextHelper.*;
import static com.netralabs.basic.naturallanguage.LangUtils.docLang;

public class ValidateLangOfBookmarks implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validateLanguageOfBookmarks(pdf, out);
        return out;
    }

    public void validateLanguageOfBookmarks(PdfDocument pdf, List<FindingDTO> out) {

        PdfOutline root = pdf.getOutlines(false);
        if (root == null) {
            out.add(new FindingDTO(Severity.IGNORED, PDFUACheckpoint.NATURAL_LANGUAGE_BOOKMARK, 0, null));
            return;
        }

        final String docLang = docLang(pdf);
        Deque<PdfOutline> stack = new ArrayDeque<>(root.getAllChildren());
        while (!stack.isEmpty()) {
            PdfOutline ol = stack.pop();
            // Skip if no /Title at all
            String title = ol.getTitle();
            if (title != null && !title.isBlank()) {
                String effective = firstNonBlank(outlineLang(ol), docLang);
                addLangFinding(out, effective, PDFUACheckpoint.NATURAL_LANGUAGE_BOOKMARK, 0);
            }

            List<PdfOutline> kids = ol.getAllChildren();
            if (kids != null) for (int i = kids.size() - 1; i >= 0; i--) stack.push(kids.get(i));
        }
    }

    private static String outlineLang(PdfOutline ol) {
        PdfDictionary dict = ol.getContent();
        if (dict == null) return null;
        PdfString s = dict.getAsString(PdfName.Lang);
        return s != null ? s.getValue() : null;
    }
}
