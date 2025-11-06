package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.basic.naturallanguage.LangUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATIVE_TEXT;

public class ValidateLangOfAltText implements Rule {
    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        PdfStructTreeRoot root = pdf.getStructTreeRoot();
        if (root == null) {
            out.add(new FindingDTO(Severity.IGNORED, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, 0, null));
            return out;
        }
        for (IStructureNode node : root.getKids()) {
            walk(node, out);
        }
        return out;
    }

    private void walk(IStructureNode node, List<FindingDTO> out) {
        if (!(node instanceof PdfStructElem elem))
            return;

        PdfString alt = elem.getAlt();
        if (alt != null) {
            PdfDictionary dict = elem.getPdfObject();
            PdfIndirectReference ref = dict.getIndirectReference();
            PdfString lang = dict.getAsString(PdfName.Lang);
            if (lang == null) {
                out.add(new FindingDTO(Severity.ERROR, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, 0, null));
                return;
            }
            if (isValidLang(lang.getValue())) {
                out.add(new FindingDTO(Severity.PASSED, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, 0, null));
            } else {
                out.add(new FindingDTO(Severity.ERROR, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, 0, null));
            }

        }
        List<IStructureNode> kids = elem.getKids();
        if (kids != null)
            for (IStructureNode kid : kids)
                walk(kid, out);
    }

}
