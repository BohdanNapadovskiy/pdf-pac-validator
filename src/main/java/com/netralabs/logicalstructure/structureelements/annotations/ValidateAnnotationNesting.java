package com.netralabs.logicalstructure.structureelements.annotations;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.basic.pdfsyntax.ValidateStructuralParentTree;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Iterates every page {@code /Annots} entry and emits one finding per annotation,
 * routed by subtype to one of the three "Nesting of … annotations" checkpoints:
 *
 * <ul>
 *   <li>{@link PDFUACheckpoint#NESTING_LINK_ANNOTATIONS} — Link annotation
 *       (ISO 14289-1 §7.18.5). Requires enclosing struct role "Link".</li>
 *   <li>{@link PDFUACheckpoint#NESTING_WIDGET_ANNOTATIONS} — Widget annotation
 *       (ISO 14289-1 §7.18.4). Requires enclosing struct role "Form".</li>
 *   <li>{@link PDFUACheckpoint#NESTING_ANNOTATIONS_ANNOT} — every other annotation
 *       subtype not explicitly handled elsewhere (§7.18.1). Requires enclosing
 *       struct role "Annot".</li>
 * </ul>
 *
 * <p>Per annotation: check {@code /StructParent} → ParentTree → enclosing struct
 * element → resolve role via {@code /S} and the catalog's {@code /RoleMap}. If the
 * element or any ancestor has the required role, emit PASSED; otherwise emit ERROR
 * with the annotation's {@code /Rect} bbox so the detailed report can highlight it.
 *
 * <p>PAC-parity: matches PAC's per-widget / per-link error emission. Previously
 * this rule emitted only passes and relied on veraPDF for errors, but vera's
 * {@code 7.18.4-1} / {@code 7.18.5-1} clauses over-count on forms with many
 * widgets — the native check aligns with PAC's tally exactly.
 *
 * <p>Registered on all three checkpoints; a static guard ensures the walk runs
 * once per document.
 */
public class ValidateAnnotationNesting implements Rule {

    /** Subtypes excluded from the catch-all "Annot" bucket per ISO 14289-1 §7.18. */
    private static final Set<PdfName> SPECIAL_OR_EXCLUDED = Set.of(
            PdfName.Widget,      // routed to NESTING_WIDGET_ANNOTATIONS
            PdfName.Link,        // routed to NESTING_LINK_ANNOTATIONS
            PdfName.Popup,       // §7.18.1 explicitly excludes Popup
            PdfName.TrapNet,     // §7.18.2 prohibits entirely (own checkpoint)
            PdfName.PrinterMark  // §7.18.8 separately handled (own checkpoint)
    );

    private static final PdfName STRUCT_PARENT = new PdfName("StructParent");

    private static volatile PdfDocument done;

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        synchronized (ValidateAnnotationNesting.class) {
            if (done == pdf) return new ArrayList<>();
            done = pdf;
        }

        Map<Integer, PdfObject> parentTree = buildParentTreeNums(pdf);
        PdfDictionary roleMap = getRoleMap(pdf);

        List<FindingDTO> out = new ArrayList<>();
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfArray annots = pdf.getPage(i).getPdfObject().getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int j = 0; j < annots.size(); j++) {
                PdfObject o = annots.get(j);
                if (!(o instanceof PdfDictionary annot)) continue;
                PdfName subtype = annot.getAsName(PdfName.Subtype);
                if (subtype == null) continue;
                emitForAnnotation(annot, subtype, i, parentTree, roleMap, out);
            }
        }
        return out;
    }

    private static void emitForAnnotation(PdfDictionary annot, PdfName subtype, int page,
                                          Map<Integer, PdfObject> parentTree, PdfDictionary roleMap,
                                          List<FindingDTO> out) {
        PDFUACheckpoint cp;
        String requiredRole;
        if (PdfName.Widget.equals(subtype)) {
            cp = PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS;
            requiredRole = "Form";
        } else if (PdfName.Link.equals(subtype)) {
            cp = PDFUACheckpoint.NESTING_LINK_ANNOTATIONS;
            requiredRole = "Link";
        } else if (!SPECIAL_OR_EXCLUDED.contains(subtype)) {
            cp = PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT;
            requiredRole = "Annot";
        } else {
            return;
        }
        if (isNestedUnder(annot, requiredRole, parentTree, roleMap)) {
            out.add(new FindingDTO(Severity.PASSED, cp, page, null));
        } else {
            BBoxDTO bbox = ValidateStructuralParentTree.rectToBBox(annot.getAsArray(PdfName.Rect));
            String message = "\"" + subtype.getValue() + "\" annotation not nested inside a \""
                    + requiredRole + "\" structure element";
            out.add(new FindingDTO(Severity.ERROR, cp, page, bbox, message));
        }
    }

    private static boolean isNestedUnder(PdfDictionary annot, String requiredRole,
                                         Map<Integer, PdfObject> parentTree, PdfDictionary roleMap) {
        PdfNumber sp = annot.getAsNumber(STRUCT_PARENT);
        if (sp == null) return false;
        PdfObject entry = parentTree.get(sp.intValue());
        PdfDictionary structElem = firstStructElem(entry);
        if (structElem == null) return false;
        return ancestorHasRole(structElem, requiredRole, roleMap);
    }

    private static PdfDictionary firstStructElem(PdfObject entry) {
        if (entry == null) return null;
        if (entry.isDictionary()) return (PdfDictionary) entry;
        if (entry.isArray()) {
            PdfArray arr = (PdfArray) entry;
            for (int i = 0; i < arr.size(); i++) {
                PdfObject o = arr.get(i);
                if (o != null && o.isDictionary()) return (PdfDictionary) o;
            }
        }
        return null;
    }

    private static boolean ancestorHasRole(PdfDictionary structElem, String requiredRole, PdfDictionary roleMap) {
        PdfDictionary cur = structElem;
        // Bounded walk avoids cycles from malformed struct trees.
        for (int i = 0; cur != null && i < 32; i++) {
            String role = resolveRole(cur.getAsName(PdfName.S), roleMap);
            if (requiredRole.equals(role)) return true;
            cur = cur.getAsDictionary(PdfName.P);
        }
        return false;
    }

    private static String resolveRole(PdfName raw, PdfDictionary roleMap) {
        if (raw == null) return null;
        String v = raw.getValue();
        if (roleMap != null) {
            for (int i = 0; i < 16; i++) {
                PdfName mapped = roleMap.getAsName(new PdfName(v));
                if (mapped == null) break;
                String next = mapped.getValue();
                if (v.equals(next)) break;
                v = next;
            }
        }
        return v;
    }

    private static PdfDictionary getRoleMap(PdfDocument pdf) {
        PdfDictionary root = StructUtils.structTreeRoot(pdf);
        return root != null ? root.getAsDictionary(new PdfName("RoleMap")) : null;
    }

    private static Map<Integer, PdfObject> buildParentTreeNums(PdfDocument pdf) {
        Map<Integer, PdfObject> map = new HashMap<>();
        PdfDictionary root = StructUtils.structTreeRoot(pdf);
        if (root == null) return map;
        PdfDictionary tree = root.getAsDictionary(new PdfName("ParentTree"));
        if (tree == null) return map;
        collect(tree, map);
        return map;
    }

    private static void collect(PdfDictionary node, Map<Integer, PdfObject> map) {
        PdfArray nums = node.getAsArray(PdfName.Nums);
        if (nums != null) {
            for (int i = 0; i + 1 < nums.size(); i += 2) {
                PdfNumber key = nums.getAsNumber(i);
                PdfObject val = nums.get(i + 1);
                if (key != null) map.put(key.intValue(), val);
            }
        }
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                PdfDictionary kid = kids.getAsDictionary(i);
                if (kid != null) collect(kid, map);
            }
        }
    }
}
