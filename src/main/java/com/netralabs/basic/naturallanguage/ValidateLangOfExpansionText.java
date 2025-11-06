package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.basic.naturallanguage.ActualTextHelper.*;
import static com.netralabs.basic.naturallanguage.LangUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_EXPANSION_TEXT;

public class ValidateLangOfExpansionText implements Rule {

    @Override
    public EnumSet<Phase> phases(){ return EnumSet.of(Phase.DOCUMENT); }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        validateExpansionText(pdf, out);
        return out;
    }

    private void validateExpansionText(PdfDocument pdf, List<FindingDTO> out) {
        String docLang = getStr(pdf.getCatalog().getLang());   // return null if missing
        PdfStructTreeRoot root = pdf.getStructTreeRoot();
        if (root == null) return;
        for (IStructureNode kid : root.getKids()) {
            walkForE(pdf, kid, null, docLang, out);
        }
    }

    private void walkForE(PdfDocument pdf, IStructureNode node, String inheritedLang,
                          String docLang, List<FindingDTO> out) {
        if (!(node instanceof PdfStructElem elem)) return;
        String elemLang = firstNonBlank(getStr(elem.getLang()), inheritedLang);
        PdfString expansion = elem.getPdfObject().getAsString(new PdfName("E"));
        if (expansion != null) {
            int pageNum = pageOf(elem, pdf);
            String pageLang = pageLangOf(elem);
            String effective = firstNonBlank(elemLang, pageLang, docLang);
            addLangFinding(out, effective, PDFUACheckpoint.NATURAL_LANGUAGE_EXPANSION_TEXT, pageNum);
        }
        List<IStructureNode> kids = elem.getKids();
        if (kids != null) {
            String inheritDown = firstNonBlank(getStr(elem.getLang()), inheritedLang);
            for (IStructureNode k : kids) walkForE(pdf, k, inheritDown, docLang, out);
        }
    }


}
