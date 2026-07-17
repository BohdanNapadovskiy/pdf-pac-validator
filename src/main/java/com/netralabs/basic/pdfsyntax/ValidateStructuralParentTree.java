package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.*;

import static com.netralabs.basic.pdfsyntax.StructUtils.structTreeRoot;
import static com.netralabs.basic.pdfsyntax.StructUtils.walkStructure;
import static com.netralabs.domain.PDFUACheckpoint.STRUCTURE_PARENT_TREE;

public class ValidateStructuralParentTree implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        PdfDictionary str = structTreeRoot(pdf);
        if (str == null) return out; // skip

        PdfDictionary parentTree = str.getAsDictionary(new PdfName("ParentTree"));
        if (parentTree == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null, "StructTreeRoot /ParentTree missing"));
            return out;
        }
        if (parentTree.getAsArray(PdfName.Nums) == null && parentTree.getAsArray(PdfName.Kids) == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null, "/ParentTree has no /Nums or /Kids"));
            return out;
        }
        // Passes for the PDF Syntax subcategory are emitted by ValidateLogicalStructureSyntax;
        // this rule contributes errors only to avoid duplicating per-element passes.

        // Build quick map of ParentTree Nums (flat only; Kids trees are expanded lazily)
        Map<Integer, PdfObject> nums = new HashMap<>();
        collectNums(parentTree, nums);

        // Errors only: collect findings per element and keep just the worst (ERROR if any).
        walkStructure(pdf, (parent, se) -> {
            PdfObject k = se.get(PdfName.K);
            if (k == null) return;

            List<FindingDTO> elemFindings = new ArrayList<>();
            checkK(pdf, k, se, nums, elemFindings);

            for (FindingDTO f : elemFindings) {
                if (f.getSeverity() == Severity.ERROR) {
                    out.add(f);
                    return;
                }
            }
        });

        // Also check annotation /StructParent keys resolve into /ParentTree /Nums.
        // PAC rolls this up to a single aggregate finding per document, so emit at most
        // one error even when multiple annotations are unresolved. The page + rect
        // come from the first offender so the detailed report can highlight it.
        UnresolvedAnnot ua = firstUnresolvedAnnotStructParent(pdf, nums);
        if (ua != null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, ua.page, ua.bbox,
                    "Inconsistent entry found"));
        }

        return out;
    }

    private record UnresolvedAnnot(int page, BBoxDTO bbox) {}

    private static UnresolvedAnnot firstUnresolvedAnnotStructParent(PdfDocument pdf, Map<Integer, PdfObject> nums) {
        int pages = pdf.getNumberOfPages();
        for (int i = 1; i <= pages; i++) {
            PdfDictionary page = pdf.getPage(i).getPdfObject();
            PdfArray annots = page.getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int j = 0; j < annots.size(); j++) {
                PdfDictionary annot = annots.getAsDictionary(j);
                if (annot == null) continue;
                PdfNumber sp = annot.getAsNumber(new PdfName("StructParent"));
                if (sp == null) continue;
                if (!nums.containsKey(sp.intValue())) {
                    return new UnresolvedAnnot(i, rectToBBox(annot.getAsArray(PdfName.Rect)));
                }
            }
        }
        return null;
    }

    /** Convert a PDF /Rect [x1 y1 x2 y2] to a BBoxDTO in top/left/height/width form. */
    public static BBoxDTO rectToBBox(PdfArray rect) {
        if (rect == null || rect.size() < 4) return null;
        try {
            float x1 = rect.getAsNumber(0).floatValue();
            float y1 = rect.getAsNumber(1).floatValue();
            float x2 = rect.getAsNumber(2).floatValue();
            float y2 = rect.getAsNumber(3).floatValue();
            float left = Math.min(x1, x2);
            float right = Math.max(x1, x2);
            float bottom = Math.min(y1, y2);
            float top = Math.max(y1, y2);
            return new BBoxDTO(top, left, top - bottom, right - left);
        } catch (Exception e) {
            return null;
        }
    }

    private static void collectNums(PdfDictionary node, Map<Integer, PdfObject> nums) {
        PdfArray n = node.getAsArray(PdfName.Nums);
        if (n != null) {
            for (int i = 0; i + 1 < n.size(); i += 2) {
                PdfNumber key = n.getAsNumber(i);
                PdfObject val = n.get(i + 1);
                if (key != null) nums.put(key.intValue(), val);
            }
        }
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                PdfDictionary kid = kids.getAsDictionary(i);
                if (kid != null) collectNums(kid, nums);
            }
        }
    }

    private static void checkK(PdfDocument pdf, PdfObject k, PdfDictionary se, Map<Integer, PdfObject> nums, List<FindingDTO> out) {
        if (k.isDictionary()) {
            PdfDictionary d = (PdfDictionary) k;
            if (new PdfName("MCR").equals(d.getAsName(PdfName.Type))) {
                verifyMcrInParentTree(pdf, d, se, nums, out);
            }
        } else if (k.isArray()) {
            PdfArray arr = (PdfArray) k;
            for (int i = 0; i < arr.size(); i++) {
                PdfObject item = arr.get(i);
                if (item != null && item.isDictionary()) {
                    PdfDictionary d = (PdfDictionary) item;
                    if (new PdfName("MCR").equals(d.getAsName(PdfName.Type))) {
                        verifyMcrInParentTree(pdf, d, se, nums, out);
                    }
                }
            }
        } else if (k.isNumber()) {
            // MCID number; must have /Pg on the SE
            PdfDictionary pg = se.getAsDictionary(PdfName.Pg);
            if (pg != null) {
                PdfDictionary mcr = new PdfDictionary();
                mcr.put(new PdfName("MCID"), (PdfNumber) k);
                mcr.put(PdfName.Pg, pg);
                verifyMcrInParentTree(pdf, mcr, se, nums, out);
            }
        }
    }

    private static void verifyMcrInParentTree(PdfDocument pdf, PdfDictionary mcr, PdfDictionary se,
                                              Map<Integer, PdfObject> nums, List<FindingDTO> out) {
        PdfDictionary pg = mcr.getAsDictionary(PdfName.Pg);
        PdfNumber mcid = mcr.getAsNumber(new PdfName("MCID"));
        if (pg == null || mcid == null) return; // syntax check handled elsewhere

        int page = 0;
        try {
            com.itextpdf.kernel.pdf.PdfPage pp = pdf.getPage(pg);
            if (pp != null) page = pdf.getPageNumber(pp);
        } catch (Exception ignored) {}

        PdfNumber sp = pg.getAsNumber(new PdfName("StructParents"));
        if (sp == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, page, null, "Page is missing /StructParents"));
            return;
        }

        PdfObject ptVal = nums.get(sp.intValue());
        if (ptVal == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, page, null, "ParentTree has no entry for /StructParents key"));
            return;
        }

        // ParentTree value should be an array; one item per parented object
        PdfArray arr = ptVal.isArray() ? (PdfArray) ptVal : null;
        if (arr == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, page, null, "ParentTree entry is not an array"));
            return;
        }

        // Look for either this StructElem or an MCR with same MCID
        boolean found = false;
        PdfIndirectReference seRef = se.getIndirectReference();
        for (int i = 0; i < arr.size(); i++) {
            PdfObject o = arr.get(i);
            if (o == null) continue;

            if (o.isDictionary()) {
                PdfDictionary od = (PdfDictionary) o;
                PdfNumber omcid = od.getAsNumber(new PdfName("MCID"));
                if (omcid != null && omcid.intValue() == mcid.intValue()) {
                    found = true;
                    break;
                }
                PdfIndirectReference pRef = od.getIndirectReference();
                if (pRef != null && seRef != null && pRef.getObjNumber() == seRef.getObjNumber()) {
                    found = true;
                    break;
                }
            } else if (o.isIndirectReference() && seRef != null) {
                PdfIndirectReference r = (PdfIndirectReference) o;
                if (r.getObjNumber() == seRef.getObjNumber()) {
                    found = true;
                    break;
                }
            }
        }

        if (found) {
            out.add(new FindingDTO(Severity.PASSED, STRUCTURE_PARENT_TREE, page, null));
        } else {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, page, null, "ParentTree entry does not reference this structure element"));
        }
    }
}
