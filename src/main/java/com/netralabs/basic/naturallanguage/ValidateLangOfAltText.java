package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.basic.naturallanguage.LangUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATIVE_TEXT;

public class ValidateLangOfAltText implements Rule {

    private static final PdfName LAYOUT = new PdfName("Layout");
    private static final PdfName BBOX = new PdfName("BBox");

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
            BBoxDTO bbox = bboxFromLayoutAttrs(elem.getAttributes(false));
            // PDF/UA-1 §7.2: /Lang is inherited up the structure tree and falls back to doc /Lang.
            String effective = resolveStructElemLang(dict, rootDict, docLang);
            if (effective == null || effective.isBlank()) {
                out.add(new FindingDTO(Severity.ERROR, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, page, bbox,
                        "Alternative text has no /Lang"));
            } else if (isValidLang(effective)) {
                out.add(new FindingDTO(Severity.PASSED, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, page, bbox));
            } else {
                out.add(new FindingDTO(Severity.ERROR, NATURAL_LANGUAGE_ALTERNATIVE_TEXT, page, bbox,
                        "Alternative text /Lang value is invalid"));
            }
        }
        List<IStructureNode> kids = elem.getKids();
        if (kids != null)
            for (IStructureNode kid : kids)
                walk(pdf, kid, rootDict, docLang, out);
    }

    /**
     * Look for a Layout attribute owner with a /BBox entry (llx, lly, urx, ury) and
     * convert it to a {@link BBoxDTO} in PDF user-space (top = ury, since our coord
     * system has origin bottom-left). Same lookup as {@code ValidateFigureBoundingBox}.
     */
    private static BBoxDTO bboxFromLayoutAttrs(PdfObject attrs) {
        PdfArray rect = findLayoutBBox(attrs);
        if (rect == null || rect.size() != 4) return null;
        for (int i = 0; i < 4; i++) {
            if (!(rect.get(i) instanceof PdfNumber)) return null;
        }
        float llx = rect.getAsNumber(0).floatValue();
        float lly = rect.getAsNumber(1).floatValue();
        float urx = rect.getAsNumber(2).floatValue();
        float ury = rect.getAsNumber(3).floatValue();
        return new BBoxDTO(ury, llx, ury - lly, urx - llx);
    }

    private static PdfArray findLayoutBBox(PdfObject attrs) {
        if (attrs == null) return null;
        if (attrs.isDictionary()) return bboxIfLayout((PdfDictionary) attrs);
        if (attrs.isArray()) {
            PdfArray arr = (PdfArray) attrs;
            for (int i = 0; i < arr.size(); i++) {
                PdfObject item = arr.get(i);
                if (item != null && item.isDictionary()) {
                    PdfArray b = bboxIfLayout((PdfDictionary) item);
                    if (b != null) return b;
                }
            }
        }
        return null;
    }

    private static PdfArray bboxIfLayout(PdfDictionary attr) {
        PdfName owner = attr.getAsName(PdfName.O);
        if (!LAYOUT.equals(owner)) return null;
        return attr.getAsArray(BBOX);
    }
}
