package com.netralabs.logicalstructure.structureelements;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.*;

import java.util.List;
import java.util.function.Consumer;

public class StructUtil {
    private StructUtil() {}

    // Stable DFS over the structure tree (iText 9)
    public static void walk(PdfDocument pdf, Consumer<PdfStructElem> visitor) {
        PdfStructTreeRoot root = pdf.getStructTreeRoot();
        if (root == null || root.getPdfObject() == null) return;
        walkNode(pdf, root, visitor);
    }

    private static void walkNode(PdfDocument pdf, IStructureNode node, Consumer<PdfStructElem> visitor) {
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return;
        for (IStructureNode k : kids) {
            if (k instanceof PdfStructElem elem) {
                visitor.accept(elem);
                walkNode(pdf, elem, visitor);
            } else if (k instanceof PdfMcr) {
                // ignore (content references)
            } else if (k instanceof PdfObjRef) {
                // handled by callers when they need actual annotation objects
            }
        }
    }

    // RoleMap-aware role normalization
    public static String normRole(PdfDocument pdf, PdfStructElem elem) {
        String role = elem.getRole() != null ? elem.getRole().getValue() : null;
        if (role == null) return null;
        PdfDictionary roleMap = pdf.getStructTreeRoot().getRoleMap();
        if (roleMap != null && roleMap.containsKey(new PdfName(role))) {
            return roleMap.getAsName(new PdfName(role)).getValue();
        }
        return role;
    }

    // Returns the first annotation dictionary referenced by ObjRef (or null)
    public static PdfDictionary firstReferencedAnnotation(PdfStructElem elem) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return null;
        for (IStructureNode k : kids) {
            if (k instanceof PdfObjRef ref) {
                PdfObject obj = ref.getReferencedObject();
                if (obj instanceof PdfDictionary dict && PdfName.Annot.equals(dict.getAsName(PdfName.Type))) {
                    return dict;
                }
                // Many files omit /Type /Annot; Subtype alone is enough to detect
                if (obj instanceof PdfDictionary dict2 && dict2.containsKey(PdfName.Subtype)) {
                    return dict2;
                }
            }
        }
        return null;
    }

    // Is any ancestor a given role?
    public static boolean hasAncestorRole(PdfDocument pdf, PdfStructElem elem, String role) {
        IStructureNode p = elem.getParent();
        while (p instanceof PdfStructElem parent) {
            String r = normRole(pdf, parent);
            if (role.equals(r)) return true;
            p = parent.getParent();
        }
        return false;
    }
}
