package com.netralabs.logicalstructure.structureelements;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;

import java.util.List;
import java.util.function.Consumer;

public class StructWalk {

    private static final PdfName Lvl = new PdfName("Lvl");

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
                // Recurse into children
                walkNode(pdf, elem, visitor);
            }
            // Other node types you may see and usually skip for "structure element" rules:
            // - PdfMcr (marked-content ref)
            // - PdfObjRef (object ref)
            // - PdfStructTreeRoot (already handled as root)
        }
    }

    public static String normRole(PdfDocument pdf, PdfStructElem elem) {
        String role = elem.getRole() != null ? elem.getRole().getValue() : null;
        if (role == null) return null;
        PdfDictionary roleMap = pdf.getStructTreeRoot().getRoleMap();
        if (roleMap != null && roleMap.containsKey(new PdfName(role))) {
            return roleMap.getAsName(new PdfName(role)).getValue();
        }
        return role;
    }

    public static int headingLevel(PdfDocument pdf, PdfStructElem elem) {
        String role = normRole(pdf, elem);
        if (role == null) return -1;
        // H1..H6
        if (role.length() == 2 && role.charAt(0) == 'H' && Character.isDigit(role.charAt(1))) {
            int n = role.charAt(1) - '0';
            return (n >= 1 && n <= 6) ? n : -1;
        }
        // Generic H with /Lvl
        if ("H".equals(role)) {
            PdfNumber lvl = elem.getPdfObject().getAsNumber(Lvl);
            return (lvl != null) ? lvl.intValue() : -1;
        }
        return -1;
    }

    public static boolean parentIsStructureNode(PdfDocument pdf, PdfStructElem elem) {
        IStructureNode parentNode = elem.getParent();   // iText 9 returns IStructureNode
        if (!(parentNode instanceof PdfStructElem parent)) {
            return false;
        }
        String r = normRole(pdf, parent);
        return "Document".equals(r) || "Part".equals(r) || "Art".equals(r)
                || "Sect".equals(r) || "Div".equals(r);
    }
}
