package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;

import static com.netralabs.basic.pdfsyntax.StructUtils.structTreeRoot;
import static com.netralabs.basic.pdfsyntax.StructUtils.walkStructure;
import static com.netralabs.domain.PDFUACheckpoint.STRUCTURE_PARENT_TREE;

public class ValidateStructuralParentTree implements Rule {

    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        PdfDictionary str = structTreeRoot(pdf);
        if (str == null) return out; // skip

        PdfDictionary parentTree = str.getAsDictionary(new PdfName("ParentTree"));
        if (parentTree == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null));
            return out;
        }
        if (parentTree.getAsArray(PdfName.Nums) == null && parentTree.getAsArray(PdfName.Kids) == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null));
            return out;
        } else {
            out.add(new FindingDTO(Severity.PASSED, STRUCTURE_PARENT_TREE, 0, null));
        }

        // Build quick map of ParentTree Nums (flat only; Kids trees are expanded lazily)
        Map<Integer, PdfObject> nums = new HashMap<>();
        collectNums(parentTree, nums);

        // For each StructElem MCR, verify there is a matching ParentTree entry
        walkStructure(pdf, (parent, se) -> {
            PdfObject k = se.get(PdfName.K);
            if (k == null) return;
            checkK(k, se, nums, out);
        });

        return out;
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

    private static void checkK(PdfObject k, PdfDictionary se, Map<Integer, PdfObject> nums, List<FindingDTO> out) {
        if (k.isDictionary()) {
            PdfDictionary d = (PdfDictionary) k;
            if (new PdfName("MCR").equals(d.getAsName(PdfName.Type))) {
                verifyMcrInParentTree(d, se, nums, out);
            }
        } else if (k.isArray()) {
            PdfArray arr = (PdfArray) k;
            for (int i = 0; i < arr.size(); i++) {
                PdfObject item = arr.get(i);
                if (item != null && item.isDictionary()) {
                    PdfDictionary d = (PdfDictionary) item;
                    if (new PdfName("MCR").equals(d.getAsName(PdfName.Type))) {
                        verifyMcrInParentTree(d, se, nums, out);
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
                verifyMcrInParentTree(mcr, se, nums, out);
            }
        }
    }

    private static void verifyMcrInParentTree(PdfDictionary mcr, PdfDictionary se,
                                              Map<Integer, PdfObject> nums, List<FindingDTO> out) {
        PdfDictionary pg = mcr.getAsDictionary(PdfName.Pg);
        PdfNumber mcid = mcr.getAsNumber(new PdfName("MCID"));
        if (pg == null || mcid == null) return; // syntax check handled elsewhere

        PdfNumber sp = pg.getAsNumber(new PdfName("StructParents"));
        if (sp == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null));
            return;
        }

        PdfObject ptVal = nums.get(sp.intValue());
        if (ptVal == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null));
            return;
        }

        // ParentTree value should be an array; one item per parented object
        PdfArray arr = ptVal.isArray() ? (PdfArray) ptVal : null;
        if (arr == null) {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null));
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
            out.add(new FindingDTO(Severity.PASSED, STRUCTURE_PARENT_TREE, 0, null));
        } else {
            out.add(new FindingDTO(Severity.ERROR, STRUCTURE_PARENT_TREE, 0, null));
        }
    }
}
