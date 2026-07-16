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
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.BOUNDED_BOXES;

/**
 * ISO 14289-1 §7.7 / PAC "Bounding boxes" — syntactic check only. Every Figure
 * structure element must carry a Layout attribute owner with a 4-number
 * {@code /BBox} rectangle. PAC does not perform geometric containment on this
 * row (verified against Complex_Presentation_Sample: 28 P / 0 E). Deeper geometric
 * checks live in veraPDF's ISO 32005 profile (clause 8.2.5.28.2) and are routed
 * to this checkpoint via {@code VeraRuleMapping.CLAUSE_OVERRIDES}.
 *
 * <p>PAC-parity nuance: if <em>no</em> Figure in the document declares a Layout
 * {@code /BBox}, PAC leaves the "Bounding boxes" row as N/A rather than flagging
 * every Figure as missing (verified against OP_AoD sample: 29 Figures, 0 with
 * BBox → PAC N/A). Only when at least one Figure declares a Layout BBox does
 * PAC surface per-Figure pass/fail counts on this row.
 */
public class ValidateFigureBoundingBox implements Rule {

    private static final PdfName LAYOUT = new PdfName("Layout");
    private static final PdfName BBOX = new PdfName("BBox");

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<PdfStructElem> figures = new ArrayList<>();
        StructWalk.walk(pdf, (PdfStructElem elem) -> {
            if ("Figure".equals(StructWalk.normRole(pdf, elem))) figures.add(elem);
        });

        boolean anyDeclaresBBox = figures.stream()
                .anyMatch(f -> findLayoutBBox(f.getAttributes(false)) != null);
        if (!anyDeclaresBBox) return List.of();

        List<FindingDTO> out = new ArrayList<>();
        for (PdfStructElem elem : figures) {
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
                out.add(new FindingDTO(Severity.PASSED, BOUNDED_BOXES, page, rectAsBBoxDto(bbox)));
            }
        }
        return out;
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

    private static boolean isValidRect(PdfArray rect) {
        if (rect.size() != 4) return false;
        for (int i = 0; i < 4; i++) {
            if (!(rect.get(i) instanceof PdfNumber)) return false;
        }
        return true;
    }

    private static BBoxDTO rectAsBBoxDto(PdfArray rect) {
        float llx = rect.getAsNumber(0).floatValue();
        float lly = rect.getAsNumber(1).floatValue();
        float urx = rect.getAsNumber(2).floatValue();
        float ury = rect.getAsNumber(3).floatValue();
        if (llx > urx) { float t = llx; llx = urx; urx = t; }
        if (lly > ury) { float t = lly; lly = ury; ury = t; }
        return new BBoxDTO(ury, llx, ury - lly, urx - llx);
    }
}
