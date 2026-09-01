package com.netralabs.logicalstructure.structureelements.annotations;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.ValidateStructuralParentTree;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.netralabs.domain.PDFUACheckpoint.ALTERNATIVE_DESCRIPTION_FOR_ANNOT;

/**
 * ISO 14289-1 §7.18.1: annotations that convey information visible to sighted
 * users need an alternative description so assistive technology can convey the
 * same to visually impaired users. The description lives in the annotation's
 * {@code /Contents} entry.
 *
 * <p>Emission policy — one finding per eligible annotation, matches axesPDF PAC
 * 2026's {@code AnnotationHasAltText} row (verified against OP_AoD: 54 links
 * with valid /Contents → 54 PASSED; AoD Benchmark: 54 links + other annotations
 * → 68 PASSED at the Alt Descriptions level):
 * <ul>
 *   <li>PASSED — {@code /Contents} present and contains at least one non-whitespace char.</li>
 *   <li>ERROR (issue {@code AnnotationHasAltText-ContentsIsMissing}) —
 *       {@code /Contents} key is absent from the annotation dictionary.</li>
 *   <li>WARNING (issue {@code AnnotationHasAltText-ContentsIsWhiteSpace}) —
 *       {@code /Contents} present but the string is empty or whitespace-only.</li>
 * </ul>
 *
 * <p>Applies to annotation subtypes that require accessibility text: Link and any
 * markup-style annotation (Text, Highlight, Underline, Squiggly, StrikeOut, FreeText,
 * Popup-parent annotations, etc.). Widget annotations are excluded — they're covered
 * by {@link ValidateFormFieldAltNames} via the form-field {@code /TU} key.
 * Popup annotations are excluded — they're a UI attachment for another annotation,
 * not a standalone visible element.
 */
public class ValidateAnnotationAltText implements Rule {

    /** Annotation subtypes that need alt text. Explicitly excludes Widget (form
     *  fields cover it) and Popup (UI attachment for a parent annotation). */
    private static final Set<String> COVERED_SUBTYPES = Set.of(
            "Link", "Text", "FreeText", "Line", "Square", "Circle", "Polygon",
            "PolyLine", "Highlight", "Underline", "Squiggly", "StrikeOut",
            "Stamp", "Caret", "Ink", "FileAttachment", "Sound", "Movie",
            "Screen", "Redact", "Projection", "RichMedia", "3D"
    );

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<FindingDTO> out = new ArrayList<>();
        int pages = pdf.getNumberOfPages();
        for (int i = 1; i <= pages; i++) {
            PdfDictionary page = pdf.getPage(i).getPdfObject();
            PdfArray annots = page.getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int j = 0; j < annots.size(); j++) {
                PdfObject o = annots.get(j);
                if (!(o instanceof PdfDictionary annot)) continue;
                PdfName subtype = annot.getAsName(PdfName.Subtype);
                if (subtype == null || !COVERED_SUBTYPES.contains(subtype.getValue())) continue;
                BBoxDTO bbox = ValidateStructuralParentTree.rectToBBox(annot.getAsArray(PdfName.Rect));
                if (!annot.containsKey(PdfName.Contents)) {
                    out.add(new FindingDTO(Severity.ERROR, ALTERNATIVE_DESCRIPTION_FOR_ANNOT, i, bbox,
                            "Annotation is missing /Contents"));
                    continue;
                }
                PdfString contents = annot.getAsString(PdfName.Contents);
                String v = contents != null ? contents.getValue() : null;
                if (isWhitespace(v)) {
                    out.add(new FindingDTO(Severity.WARNING, ALTERNATIVE_DESCRIPTION_FOR_ANNOT, i, bbox,
                            "Annotation contents is white space"));
                } else {
                    out.add(new FindingDTO(Severity.PASSED, ALTERNATIVE_DESCRIPTION_FOR_ANNOT, i, null));
                }
            }
        }
        return out;
    }

    private static boolean isWhitespace(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }
}
