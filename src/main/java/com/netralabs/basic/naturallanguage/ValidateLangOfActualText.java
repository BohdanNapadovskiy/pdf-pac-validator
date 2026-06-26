package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;
import static com.netralabs.basic.naturallanguage.ActualTextHelper.*;

public class ValidateLangOfActualText implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validateActualText_Struct(pdf, out);
        return out;
    }

    private void validateActualText_Struct(PdfDocument pdf, List<FindingDTO> out) {
        String docLang = getStr(pdf.getCatalog().getLang());
        PdfStructTreeRoot root = pdf.getStructTreeRoot();
        if (root == null) return;

        for (IStructureNode kid : root.getKids()) {
            walkForActualText(pdf, kid,  null, docLang, out);
        }
    }

    private void walkForActualText(PdfDocument pdf, IStructureNode node, String inheritedLang,
                                   String docLang, List<FindingDTO> out) {
        if (!(node instanceof PdfStructElem elem))
            return;

        String elemLang = firstNonBlank(getStr(elem.getLang()), inheritedLang);

        PdfString actual = elem.getPdfObject().getAsString(PdfName.ActualText);
        if (actual != null) {
            int pageNum = pageOf(elem, pdf);
            String pageLang = pageLangOf(elem);
            String effective = firstNonBlank(elemLang, pageLang, docLang);

            addLangFinding(out, effective, PDFUACheckpoint.NATURAL_LANGUAGE_ACTUAL_TEXT, pageNum);
        }

        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            String inheritDown = firstNonBlank(getStr(elem.getLang()), inheritedLang);
            for (IStructureNode k : kids)
                walkForActualText(pdf, k, inheritDown, docLang, out);
        }
    }
}
