package com.netralabs.logicalstructure.structureelements.figures;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.BOUNDED_BOXES;

/**
 * ISO 14289-1 §7.7 / PAC "Bounding boxes": every Figure structure element must
 * carry a Layout attribute owner with a /BBox entry (4-number rectangle).
 */
public class ValidateFigureBoundingBox implements Rule {

    private static final PdfName LAYOUT = new PdfName("Layout");
    private static final PdfName BBOX = new PdfName("BBox");

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<FindingDTO> out = new ArrayList<>();

        StructWalk.walk(pdf, (PdfStructElem elem) -> {
            if (!"Figure".equals(StructWalk.normRole(pdf, elem))) return;
            PdfDictionary dict = elem.getPdfObject();
            int page = StructUtils.pageNumOf(pdf, dict);

            PdfArray bbox = findLayoutBBox(elem.getAttributes(false));
            if (bbox == null) {
                out.add(new FindingDTO(Severity.ERROR, BOUNDED_BOXES, page, null,
                        "Figure structure element has no Layout /BBox attribute"));
            } else if (!isValidRect(bbox)) {
                out.add(new FindingDTO(Severity.ERROR, BOUNDED_BOXES, page, null,
                        "Figure /BBox is not a 4-number rectangle"));
            } else {
                out.add(new FindingDTO(Severity.PASSED, BOUNDED_BOXES, page, null));
            }
        });
        return out;
    }

    private static PdfArray findLayoutBBox(PdfObject attrs) {
        if (attrs == null) return null;
        if (attrs.isDictionary()) {
            return bboxIfLayout((PdfDictionary) attrs);
        }
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

    private static boolean isValidRect(PdfArray rect) {
        if (rect.size() != 4) return false;
        for (int i = 0; i < 4; i++) {
            PdfObject n = rect.get(i);
            if (!(n instanceof PdfNumber)) return false;
        }
        return true;
    }
}
