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

        // Per-Link consistency: for each <Link> struct element with an OBJR child,
        // verify the referenced annotation's /StructParent → /Nums entry points
        // back to this same struct element. When an author wraps overlapping URL
        // text in multiple <Link> elements but all Link annotations share the same
        // /Rect and only one /StructParent, only one Link struct is the annotation's
        // back-reference target — the others are "inconsistent". PAC surfaces one
        // error per such Link struct with a bbox that unions the Link's MCID paint
        // bboxes with the referenced annotation's /Rect.
        //
        // This replaces the previous document-wide "first unresolved annotation
        // /StructParent" aggregate finding — the per-Link check subsumes that case
        // (a Link annotation whose /StructParent is missing from /Nums has no valid
        // back-reference target, so the Link struct fails the check here too), and
        // matches PAC's exact per-instance emission on OP_AoD and 2026-07663_AOD.
        emitBrokenLinkBackReferences(pdf, nums, out);

        return out;
    }

    private static void emitBrokenLinkBackReferences(PdfDocument pdf,
                                                     Map<Integer, PdfObject> nums,
                                                     List<FindingDTO> out) {
        Map<Integer, Map<Integer, BBoxDTO>> mcidByPage = new HashMap<>();
        walkStructure(pdf, (parent, se) -> {
            PdfName role = se.getAsName(PdfName.S);
            if (role == null || !"Link".equals(role.getValue())) return;
            PdfDictionary annot = firstObjrAnnot(se.get(PdfName.K));
            if (annot == null) return;
            PdfNumber sp = annot.getAsNumber(new PdfName("StructParent"));
            if (sp == null) return;
            PdfObject entry = nums.get(sp.intValue());
            if (entry == null) return;
            PdfIndirectReference target = null;
            if (entry.isIndirectReference()) target = (PdfIndirectReference) entry;
            else if (entry.isDictionary()) target = ((PdfDictionary) entry).getIndirectReference();
            PdfIndirectReference seRef = se.getIndirectReference();
            if (target == null || seRef == null) return;
            if (target.getObjNumber() == seRef.getObjNumber()) return; // consistent

            int page = StructUtils.pageNumOf(pdf, se);
            BBoxDTO bbox = brokenLinkBbox(pdf, page, se, annot, mcidByPage);
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, page, bbox,
                    "Inconsistent entry found"));
        });
    }

    /** Find the first {@code OBJR} child of a Link struct element and return its
     *  referenced annotation dictionary, or null. */
    private static PdfDictionary firstObjrAnnot(PdfObject k) {
        if (k == null) return null;
        if (k.isDictionary()) {
            PdfDictionary d = (PdfDictionary) k;
            if (new PdfName("OBJR").equals(d.getAsName(PdfName.Type))) {
                PdfObject obj = d.get(new PdfName("Obj"));
                if (obj instanceof PdfDictionary ad) return ad;
            }
            return null;
        }
        if (k.isArray()) {
            PdfArray a = (PdfArray) k;
            for (int i = 0; i < a.size(); i++) {
                PdfDictionary d = firstObjrAnnot(a.get(i));
                if (d != null) return d;
            }
        }
        return null;
    }

    /** Union of the Link struct element's MCID paint bboxes on {@code page} with
     *  the referenced annotation's {@code /Rect}. Matches PAC's detail bbox for
     *  broken-back-reference Link findings. */
    private static BBoxDTO brokenLinkBbox(PdfDocument pdf, int page, PdfDictionary se,
                                          PdfDictionary annot,
                                          Map<Integer, Map<Integer, BBoxDTO>> mcidCache) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        if (page > 0) {
            Map<Integer, BBoxDTO> mcids = mcidCache.get(page);
            if (mcids == null) {
                mcids = com.netralabs.basic.content.PageMcidBboxes.forPage(pdf, page);
                mcidCache.put(page, mcids);
            }
            List<Integer> ids = new ArrayList<>();
            collectMcidsOnPage(pdf, se, page, se.getAsDictionary(PdfName.Pg), ids);
            for (int id : ids) {
                BBoxDTO b = mcids.get(id);
                if (b == null) continue;
                double top = b.getTop(), left = b.getLeft();
                double bot = top - b.getHeight();
                double right = left + b.getWidth();
                if (left < minX) minX = left;
                if (bot < minY) minY = bot;
                if (right > maxX) maxX = right;
                if (top > maxY) maxY = top;
            }
        }

        PdfArray rect = annot.getAsArray(PdfName.Rect);
        if (rect != null && rect.size() >= 4) {
            try {
                double x1 = rect.getAsNumber(0).doubleValue();
                double y1 = rect.getAsNumber(1).doubleValue();
                double x2 = rect.getAsNumber(2).doubleValue();
                double y2 = rect.getAsNumber(3).doubleValue();
                double l = Math.min(x1, x2), r = Math.max(x1, x2);
                double b = Math.min(y1, y2), t = Math.max(y1, y2);
                if (l < minX) minX = l;
                if (b < minY) minY = b;
                if (r > maxX) maxX = r;
                if (t > maxY) maxY = t;
            } catch (Exception ignored) {}
        }

        if (minX == Double.POSITIVE_INFINITY) return null;
        float top = (float) maxY;
        float left = (float) minX;
        float height = (float) (maxY - minY);
        float width = (float) (maxX - minX);
        return new BBoxDTO(top, left, height, width);
    }

    private static void collectMcidsOnPage(PdfDocument pdf, PdfDictionary se, int page,
                                           PdfDictionary inheritedPg, List<Integer> out) {
        PdfDictionary myPg = se.getAsDictionary(PdfName.Pg);
        PdfDictionary effectivePg = myPg != null ? myPg : inheritedPg;
        collectMcidsFromK(pdf, se.get(PdfName.K), page, effectivePg, out);
    }

    private static void collectMcidsFromK(PdfDocument pdf, PdfObject k, int page,
                                          PdfDictionary inheritedPg, List<Integer> out) {
        if (k == null) return;
        if (k.isNumber()) {
            if (pageMatches(pdf, inheritedPg, page)) {
                out.add(((PdfNumber) k).intValue());
            }
        } else if (k.isDictionary()) {
            PdfDictionary d = (PdfDictionary) k;
            PdfName type = d.getAsName(PdfName.Type);
            if (new PdfName("MCR").equals(type)) {
                PdfDictionary pg = d.getAsDictionary(PdfName.Pg);
                PdfNumber mcid = d.getAsNumber(new PdfName("MCID"));
                if (mcid != null && pageMatches(pdf, pg != null ? pg : inheritedPg, page)) {
                    out.add(mcid.intValue());
                }
            }
        } else if (k.isArray()) {
            PdfArray arr = (PdfArray) k;
            for (int i = 0; i < arr.size(); i++) {
                collectMcidsFromK(pdf, arr.get(i), page, inheritedPg, out);
            }
        }
    }

    private static boolean pageMatches(PdfDocument pdf, PdfDictionary pgDict, int page) {
        if (pgDict == null) return false;
        try {
            PdfPage p = pdf.getPage(pgDict);
            return p != null && pdf.getPageNumber(p) == page;
        } catch (Exception ignored) {
            return false;
        }
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

        // The ParentTree entry for a page is an array indexed by MCID: element k
        // is the struct element that owns MCID k on that page. Verify that
        // arr[mcid] references our SE — a mismatch means the ParentTree entry
        // for this MCID points to a different SE (or is null), which PAC flags
        // as an "inconsistent entry".
        boolean found = false;
        int idx = mcid.intValue();
        PdfIndirectReference seRef = se.getIndirectReference();
        if (idx >= 0 && idx < arr.size()) {
            PdfObject o = arr.get(idx);
            if (o != null && seRef != null) {
                if (o.isIndirectReference()) {
                    found = ((PdfIndirectReference) o).getObjNumber() == seRef.getObjNumber();
                } else if (o.isDictionary()) {
                    PdfIndirectReference r = ((PdfDictionary) o).getIndirectReference();
                    found = r != null && r.getObjNumber() == seRef.getObjNumber();
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
