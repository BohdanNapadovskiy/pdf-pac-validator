package com.netralabs.logicalstructure.structureelements.annotations;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.netralabs.domain.PDFUACheckpoint.ALTERNATIVE_NAMES_FORM_FIELDS;

/**
 * ISO 14289-1 §7.18.1-3: a form field shall have a {@code /TU} key present OR all
 * its Widget annotations shall carry an alternative description (via {@code /Contents}
 * or an {@code /Alt} on the enclosing structure element).
 *
 * <p>PAC-style emission (verified against Filled_Graduate: 27 P / 4 E for 31 fields):
 * one finding per form field in the AcroForm/Fields tree.
 *
 * <p>Replaces veraPDF's 7.18.1-3 in the mapping: vera's pass-2 assertion cap silently
 * dropped tail-end field PASSED counts on multi-field forms (15/31 captured on
 * Filled_Graduate). The native walk sees every field.
 */
public class ValidateFormFieldAltNames implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<FindingDTO> out = new ArrayList<>();

        PdfDictionary catalog = pdf.getCatalog() != null ? pdf.getCatalog().getPdfObject() : null;
        if (catalog == null) return out;
        PdfDictionary acroForm = catalog.getAsDictionary(PdfName.AcroForm);
        if (acroForm == null) return out;
        PdfArray roots = acroForm.getAsArray(PdfName.Fields);
        if (roots == null || roots.isEmpty()) return out;

        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < roots.size(); i++) {
            PdfObject o = roots.get(i);
            if (o instanceof PdfDictionary d) walk(d, out, visited);
        }
        return out;
    }

    /**
     * Recursively walk field/widget dictionaries. A dict is a "field" when it declares
     * {@code /T} (partial field name) or {@code /FT} (field type); each such dict emits
     * one finding. Non-field descendants (nested Widget-only annotations) are skipped —
     * they're covered by the parent field.
     */
    private static void walk(PdfDictionary node, List<FindingDTO> out, Set<Integer> visited) {
        if (node.getIndirectReference() != null) {
            int id = node.getIndirectReference().getObjNumber();
            if (!visited.add(id)) return;
        }
        boolean isField = node.get(PdfName.T) != null || node.get(PdfName.FT) != null;
        if (isField) {
            out.add(evaluateField(node));
        }
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids == null) return;
        for (int i = 0; i < kids.size(); i++) {
            PdfObject k = kids.get(i);
            if (k instanceof PdfDictionary kd
                    && (kd.get(PdfName.T) != null || kd.get(PdfName.FT) != null)) {
                walk(kd, out, visited);
            }
        }
    }

    private static FindingDTO evaluateField(PdfDictionary field) {
        if (hasNonEmpty(field, PdfName.TU) || allWidgetsDescribed(field)) {
            return new FindingDTO(Severity.PASSED, ALTERNATIVE_NAMES_FORM_FIELDS, null, null);
        }
        return new FindingDTO(Severity.ERROR, ALTERNATIVE_NAMES_FORM_FIELDS, null, null,
                "Form field lacks a TU key and its Widget annotations lack alternative descriptions");
    }

    /**
     * True iff every Widget annotation associated with this field carries
     * {@code /Contents} — the annotation-side fallback for {@code /TU}. A merged
     * field+widget (field dict with {@code /Subtype=/Widget}) is treated as its own
     * single widget.
     */
    private static boolean allWidgetsDescribed(PdfDictionary field) {
        List<PdfDictionary> widgets = new ArrayList<>();
        if (PdfName.Widget.equals(field.getAsName(PdfName.Subtype))) widgets.add(field);
        PdfArray kids = field.getAsArray(PdfName.Kids);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                if (kids.get(i) instanceof PdfDictionary k
                        && PdfName.Widget.equals(k.getAsName(PdfName.Subtype))) widgets.add(k);
            }
        }
        if (widgets.isEmpty()) return false;
        for (PdfDictionary w : widgets) if (!hasNonEmpty(w, PdfName.Contents)) return false;
        return true;
    }

    private static boolean hasNonEmpty(PdfDictionary d, PdfName key) {
        PdfString s = d.getAsString(key);
        return s != null && !s.getValue().isEmpty();
    }
}
