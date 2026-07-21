package com.netralabs.report.pac;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.basic.pdfsyntax.ValidateStructuralParentTree;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backfills page + bbox on ERROR findings whose emitting rule (typically
 * veraPDF) couldn't attach that context — currently the annotation-nesting
 * checkpoints ({@code NESTING_WIDGET_ANNOTATIONS},
 * {@code NESTING_LINK_ANNOTATIONS}, {@code NESTING_ANNOTATIONS_ANNOT}).
 * <p>
 * For each subtype we identify the actual failing annotations by walking the
 * struct tree: an annotation is nested when its {@code /StructParent} resolves
 * to a struct element whose role (or an ancestor's role) matches the required
 * wrapper (Form for Widget, Link for Link, Annot for others). The failing
 * annotations' pages and {@code /Rect} bboxes are then assigned to the ERROR
 * findings in enumeration order. When native and vera counts diverge we fall
 * back to leaving the finding unadorned rather than risk misattribution.
 */
public final class FindingBboxEnricher {

    private FindingBboxEnricher() {}

    /** Subtypes excluded from the catch-all "Annot" bucket per ISO 14289-1 §7.18. */
    private static final Set<PdfName> ANNOT_EXCLUDED_SUBTYPES = Set.of(
            PdfName.Widget, PdfName.Link, PdfName.Popup, PdfName.TrapNet, PdfName.PrinterMark);

    private static final PdfName PARENT_TREE = new PdfName("ParentTree");
    private static final PdfName NUMS = PdfName.Nums;
    private static final PdfName KIDS = PdfName.Kids;
    private static final PdfName STRUCT_PARENT = new PdfName("StructParent");

    public static void enrich(PdfDocument pdf, List<FindingDTO> findings) {
        if (pdf == null || findings == null || findings.isEmpty()) return;

        Map<Integer, PdfObject> parentTree = buildParentTreeNums(pdf);
        PdfDictionary roleMap = getRoleMap(pdf);

        Map<PDFUACheckpoint, List<PageRect>> pools = new EnumMap<>(PDFUACheckpoint.class);
        pools.put(PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS,
                findFailing(pdf, PdfName.Widget, null, "Form", parentTree, roleMap));
        pools.put(PDFUACheckpoint.NESTING_LINK_ANNOTATIONS,
                findFailing(pdf, PdfName.Link, null, "Link", parentTree, roleMap));
        pools.put(PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT,
                findFailing(pdf, null, ANNOT_EXCLUDED_SUBTYPES, "Annot", parentTree, roleMap));

        for (Map.Entry<PDFUACheckpoint, List<PageRect>> e : pools.entrySet()) {
            attach(findings, e.getKey(), e.getValue());
        }
    }

    private static void attach(List<FindingDTO> findings, PDFUACheckpoint cp, List<PageRect> pool) {
        List<FindingDTO> errs = new ArrayList<>();
        for (FindingDTO f : findings) {
            if (f.getCheckpoint() == cp && f.getSeverity() == Severity.ERROR) errs.add(f);
        }
        if (errs.isEmpty() || errs.size() != pool.size()) return;
        for (int i = 0; i < errs.size(); i++) {
            FindingDTO f = errs.get(i);
            PageRect pr = pool.get(i);
            if (f.getPage() == null || f.getPage() == 0) f.setPage(pr.page);
            if (f.getBBox() == null) f.setBBox(pr.bbox);
        }
    }

    /**
     * Enumerate annotations of the target subtype whose enclosing struct chain
     * does NOT contain the required role. Return their page + rect in page /
     * annotation-array order.
     */
    private static List<PageRect> findFailing(PdfDocument pdf,
                                              PdfName includeSubtype,
                                              Set<PdfName> excludeSubtypes,
                                              String requiredRole,
                                              Map<Integer, PdfObject> parentTree,
                                              PdfDictionary roleMap) {
        List<PageRect> failing = new ArrayList<>();
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfArray annots = pdf.getPage(i).getPdfObject().getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int j = 0; j < annots.size(); j++) {
                PdfObject o = annots.get(j);
                if (!(o instanceof PdfDictionary annot)) continue;
                PdfName subtype = annot.getAsName(PdfName.Subtype);
                if (subtype == null) continue;
                if (includeSubtype != null ? !includeSubtype.equals(subtype) : excludeSubtypes.contains(subtype)) continue;
                if (isNestedUnder(annot, requiredRole, parentTree, roleMap)) continue;
                BBoxDTO bbox = ValidateStructuralParentTree.rectToBBox(annot.getAsArray(PdfName.Rect));
                failing.add(new PageRect(i, bbox));
            }
        }
        return failing;
    }

    private static boolean isNestedUnder(PdfDictionary annot,
                                         String requiredRole,
                                         Map<Integer, PdfObject> parentTree,
                                         PdfDictionary roleMap) {
        PdfNumber sp = annot.getAsNumber(STRUCT_PARENT);
        if (sp == null) return false;
        PdfObject entry = parentTree.get(sp.intValue());
        PdfDictionary structElem = firstStructElem(entry);
        if (structElem == null) return false;
        return ancestorHasRole(structElem, requiredRole, roleMap);
    }

    /**
     * A ParentTree entry for an annotation is normally the struct element that
     * wraps it. Occasionally the entry is an array — pick the first struct
     * element in it (matching veraPDF's resolution).
     */
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
            // Bounded traversal in case of a self-referential role map.
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

    /**
     * Build the ParentTree {@code /Nums} lookup: struct-parent number → target
     * object. Handles both flat {@code /Nums} and nested {@code /Kids} number
     * trees.
     */
    private static Map<Integer, PdfObject> buildParentTreeNums(PdfDocument pdf) {
        Map<Integer, PdfObject> map = new HashMap<>();
        PdfDictionary root = StructUtils.structTreeRoot(pdf);
        if (root == null) return map;
        PdfDictionary tree = root.getAsDictionary(PARENT_TREE);
        if (tree == null) return map;
        collect(tree, map);
        return map;
    }

    private static void collect(PdfDictionary node, Map<Integer, PdfObject> map) {
        PdfArray nums = node.getAsArray(NUMS);
        if (nums != null) {
            for (int i = 0; i + 1 < nums.size(); i += 2) {
                PdfNumber key = nums.getAsNumber(i);
                PdfObject val = nums.get(i + 1);
                if (key != null) map.put(key.intValue(), val);
            }
        }
        PdfArray kids = node.getAsArray(KIDS);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                PdfDictionary kid = kids.getAsDictionary(i);
                if (kid != null) collect(kid, map);
            }
        }
    }

    private record PageRect(int page, BBoxDTO bbox) {}
}
