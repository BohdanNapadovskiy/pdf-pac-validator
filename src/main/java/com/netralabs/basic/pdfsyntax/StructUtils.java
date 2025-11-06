package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;

public class StructUtils {
    private StructUtils() {}

    public static PdfDictionary structTreeRoot(PdfDocument pdf) {
        PdfDictionary catalog = pdf.getCatalog().getPdfObject();
        return catalog.getAsDictionary(new PdfName("StructTreeRoot"));
    }

    /** Depth-first walk over all StructElem dictionaries; calls visitor(parent, child). */
    public static void walkStructure(PdfDocument pdf, BiConsumer<PdfDictionary, PdfDictionary> visitor) {
        PdfDictionary root = structTreeRoot(pdf);
        if (root == null) return;

        // Root’s /K can be a dict (StructElem), array of kids, or null
        Deque<PdfDictionary> stack = new ArrayDeque<>();
        // synthesize a parent holder for top-level (/P must be StructTreeRoot)
        PdfDictionary parent = root;

        // enqueue children of root
        pushKids(pdf, parent, root.get(PdfName.K), stack, visitor);

        // DFS
        while (!stack.isEmpty()) {
            PdfDictionary cur = stack.pop();
            visitor.accept(parentOf(cur), cur); // we’ll set /_P cache in parentOf()
            // descend
            parent = cur;
            pushKids(pdf, cur, cur.get(PdfName.K), stack, visitor);
        }
    }

    private static void pushKids(PdfDocument pdf, PdfDictionary parent, PdfObject k, Deque<PdfDictionary> stack,
                                 BiConsumer<PdfDictionary, PdfDictionary> visitor) {
        if (k == null) return;
        if (k.isDictionary()) {
            PdfDictionary kid = (PdfDictionary) k;
            if (isStructElem(kid)) {
                kid.put(new PdfName("_P"), parent.getIndirectReference()); // cache parent
                stack.push(kid);
                visitor.accept(parent, kid);
            }
        } else if (k.isArray()) {
            PdfArray arr = (PdfArray) k;
            for (int i = arr.size() - 1; i >= 0; i--) {
                PdfObject item = arr.get(i);
                if (item != null && item.isDictionary()) {
                    PdfDictionary kid = (PdfDictionary) item;
                    if (isStructElem(kid)) {
                        kid.put(new PdfName("_P"), parent.getIndirectReference());
                        stack.push(kid);
                        visitor.accept(parent, kid);
                    }
                }
            }
        }
    }

    public static boolean isStructElem(PdfDictionary d) {
        PdfName type = d.getAsName(PdfName.Type);
        return PdfName.StructElem.equals(type) || d.containsKey(PdfName.S);
    }

    /** Parent we cached in walk (/_P); falls back to /P if present. */
    public static PdfDictionary parentOf(PdfDictionary se) {
        PdfIndirectReference pRef = se.getIndirectReference();
        PdfObject cached = se.get(new PdfName("_P"));
        if (cached instanceof PdfIndirectReference) {
            PdfObject p = ((PdfIndirectReference) cached).getRefersTo();
            return p != null && p.isDictionary() ? (PdfDictionary) p : null;
        }
        PdfObject fromDoc = se.get(new PdfName("P"));
        return (fromDoc != null && fromDoc.isDictionary()) ? (PdfDictionary) fromDoc : null;
    }
}
