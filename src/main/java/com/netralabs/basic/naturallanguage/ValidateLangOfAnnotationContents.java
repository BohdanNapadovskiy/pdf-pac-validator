package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Set<Integer> parentTreeKeys = collectParentTreeKeys(pdf);

        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfPage page = pdf.getPage(i);
            String pageLang = pageLangOf(page.getPdfObject());

            for (PdfAnnotation a : page.getAnnotations()) {
                PdfString contents = a.getContents();
                if (contents == null || contents.getValue().isBlank())
                    continue;
                // Skip untagged annotations: PAC excludes annotations whose
                // /StructParent key doesn't resolve into /StructTreeRoot/ParentTree/Nums
                // from the Contents-lang tally (they're inaccessible to AT, so the
                // language of their Contents is not observable).
                if (isUntagged(a, parentTreeKeys)) continue;
                String annotLang = LangUtils.pdfStringValue(a.getPdfObject().getAsString(PdfName.Lang));
                String effective = firstNonBlank(annotLang, pageLang, docLang);
                addLangFinding(out, effective, NATURAL_LANGUAGE_CONTENTS, i);
            }
        }
    }

    private static boolean isUntagged(PdfAnnotation a, Set<Integer> parentTreeKeys) {
        PdfNumber sp = a.getPdfObject().getAsNumber(new PdfName("StructParent"));
        return sp != null && !parentTreeKeys.contains(sp.intValue());
    }

    private static Set<Integer> collectParentTreeKeys(PdfDocument pdf) {
        Set<Integer> out = new HashSet<>();
        PdfDictionary str = pdf.getCatalog().getPdfObject()
                .getAsDictionary(new PdfName("StructTreeRoot"));
        if (str == null) return out;
        PdfDictionary parentTree = str.getAsDictionary(new PdfName("ParentTree"));
        if (parentTree == null) return out;
        collectNumsKeys(parentTree, out);
        return out;
    }

    private static void collectNumsKeys(PdfDictionary node, Set<Integer> out) {
        PdfArray nums = node.getAsArray(new PdfName("Nums"));
        if (nums != null) {
            for (int i = 0; i + 1 < nums.size(); i += 2) {
                PdfNumber key = nums.getAsNumber(i);
                if (key != null) out.add(key.intValue());
            }
        }
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                PdfDictionary kid = kids.getAsDictionary(i);
                if (kid != null) collectNumsKeys(kid, out);
            }
        }
    }
}
