package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.naturallanguage.LangUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATIVE_TEXT;

public class ValidateLangOfAltText implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        PdfStructTreeRoot root = pdf.getStructTreeRoot();
        if (root == null) {
            out.add(new FindingDTO(Severity.IGNORED, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, 0, null));
            return out;
        }
        String docLang = docLang(pdf);
        PdfDictionary rootDict = root.getPdfObject();
        for (IStructureNode node : root.getKids()) {
            walk(pdf, node, rootDict, docLang, out);
        }
        return out;
    }

    private void walk(PdfDocument pdf, IStructureNode node, PdfDictionary rootDict,
                      String docLang, List<FindingDTO> out) {
        if (!(node instanceof PdfStructElem elem))
            return;

        PdfString alt = elem.getAlt();
        if (alt != null) {
            PdfDictionary dict = elem.getPdfObject();
            int page = StructUtils.pageNumOf(pdf, dict);
            // PDF/UA-1 §7.2: /Lang is inherited up the structure tree and falls back to doc /Lang.
            String effective = resolveStructElemLang(dict, rootDict, docLang);
            if (effective == null || effective.isBlank()) {
                out.add(new FindingDTO(Severity.ERROR, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, page, null,
                        "Alternative text has no /Lang"));
            } else if (isValidLang(effective)) {
                out.add(new FindingDTO(Severity.PASSED, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, page, null));
            } else {
                out.add(new FindingDTO(Severity.ERROR, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, page, null,
                        "Alternative text /Lang value is invalid"));
            }
        }
        List<IStructureNode> kids = elem.getKids();
        if (kids != null)
            for (IStructureNode kid : kids)
                walk(pdf, kid, rootDict, docLang, out);
    }

}
