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

    /**
     * Tagged PDF per ISO 32000-1 §14.7: catalog has {@code /MarkInfo}
     * with {@code /Marked true} AND a {@code /StructTreeRoot} is present.
     * A vestigial struct tree without the {@code /Marked} flag is not a tagged
     * PDF and structure-syntax rules should not apply to it — PAC treats
     * these documents' logical-structure rows as NA.
     */
    public static boolean isTaggedPdf(PdfDocument pdf) {
        PdfDictionary catalog = pdf.getCatalog().getPdfObject();
        PdfDictionary markInfo = catalog.getAsDictionary(PdfName.MarkInfo);
        boolean marked = markInfo != null
                && (markInfo.get(PdfName.Marked) instanceof PdfBoolean b) && b.getValue();
        return marked && catalog.containsKey(new PdfName("StructTreeRoot"));
    }

    /** Depth-first walk over all StructElem dictionaries; calls visitor(parent, child). */
    public static void walkStructure(PdfDocument pdf, BiConsumer<PdfDictionary, PdfDictionary> visitor) {
        PdfDictionary root = structTreeRoot(pdf);
        if (root == null) return;

        // Push root's children onto the stack; the main loop is the single visit site.
        Deque<PdfDictionary> stack = new ArrayDeque<>();
        pushKids(root, root.get(PdfName.K), stack);

        while (!stack.isEmpty()) {
            PdfDictionary cur = stack.pop();
            visitor.accept(parentOf(cur), cur);
            pushKids(cur, cur.get(PdfName.K), stack);
        }
    }

    private static void pushKids(PdfDictionary parent, PdfObject k, Deque<PdfDictionary> stack) {
        if (k == null) return;
        if (k.isDictionary()) {
            PdfDictionary kid = (PdfDictionary) k;
            if (isStructElem(kid)) {
                kid.put(new PdfName("_P"), parent.getIndirectReference());
                stack.push(kid);
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
                    }
                }
            }
        }
    }

    public static boolean isStructElem(PdfDictionary d) {
        PdfName type = d.getAsName(PdfName.Type);
        return PdfName.StructElem.equals(type) || d.containsKey(PdfName.S);
    }

    /**
     * Resolve the 1-based page number associated with a structure element via its /Pg entry,
     * or via the /Pg of an MCR child if the element itself has none. Returns 0 when no page
     * can be derived (treated as null by the report layer).
     */
    public static int pageNumOf(PdfDocument pdf, PdfDictionary se) {
        if (se == null) return 0;
        int n = pageOfDict(pdf, se.getAsDictionary(PdfName.Pg));
        if (n > 0) return n;
        PdfObject k = se.get(PdfName.K);
        if (k instanceof PdfDictionary kd) {
            n = pageOfDict(pdf, kd.getAsDictionary(PdfName.Pg));
            if (n > 0) return n;
        } else if (k instanceof PdfArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                PdfObject item = arr.get(i);
                if (item instanceof PdfDictionary kd) {
                    n = pageOfDict(pdf, kd.getAsDictionary(PdfName.Pg));
                    if (n > 0) return n;
                }
            }
        }
        return 0;
    }

    private static int pageOfDict(PdfDocument pdf, PdfDictionary pg) {
        if (pg == null) return 0;
        try {
            PdfPage page = pdf.getPage(pg);
            if (page != null) return pdf.getPageNumber(page);
        } catch (Exception ignored) {}
        return 0;
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
