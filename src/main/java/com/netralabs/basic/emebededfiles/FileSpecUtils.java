package com.netralabs.basic.emebededfiles;

import com.itextpdf.kernel.pdf.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class FileSpecUtils {
    private FileSpecUtils(){}

    public static List<PdfDictionary> collectAllFileSpecs(PdfDocument pdf) {
        List<PdfDictionary> out = new ArrayList<>();

        PdfDictionary catalog = pdf.getCatalog().getPdfObject();

        // 1) Names tree: /Names >/EmbeddedFiles
        PdfDictionary names = catalog.getAsDictionary(PdfName.Names);
        if (names != null) {
            PdfDictionary embeddedFiles = names.getAsDictionary(PdfName.EmbeddedFiles);
            collectFromNamesTree(embeddedFiles, out);
        }

        // 2) AF (Associated Files) on Catalog and Pages
        collectAF(catalog, out);
        for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
            PdfDictionary page = pdf.getPage(p).getPdfObject();
            collectAF(page, out);
        }

        // 3) FileAttachment annotations
        for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
            PdfArray annots = pdf.getPage(p).getPdfObject().getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int i = 0; i < annots.size(); i++) {
                PdfDictionary a = annots.getAsDictionary(i);
                if (a == null) continue;
                if (PdfName.FileAttachment.equals(a.getAsName(PdfName.Subtype))) {
                    PdfDictionary fs = a.getAsDictionary(PdfName.FS);
                    if (fs != null) out.add(fs);
                }
            }
        }
        LinkedHashMap<Integer, PdfDictionary> uniq = new LinkedHashMap<>();
        for (PdfDictionary fs : out) {
            PdfIndirectReference r = fs.getIndirectReference();
            int key = (r != null) ? r.getObjNumber() : System.identityHashCode(fs);
            uniq.putIfAbsent(key, fs);
        }
        return new ArrayList<>(uniq.values());
    }

    private static void collectAF(PdfDictionary holder, List<PdfDictionary> out) {
        if (holder == null) return;
        PdfObject af = holder.get(new PdfName("AF"));
        if (af == null) return;
        if (af.isArray()) {
            PdfArray arr = (PdfArray) af;
            for (int i = 0; i < arr.size(); i++) {
                PdfDictionary fs = arr.getAsDictionary(i);
                if (fs != null) out.add(fs);
            }
        } else if (af.isDictionary()) {
            out.add((PdfDictionary) af);
        }
    }

    private static void collectFromNamesTree(PdfDictionary node, List<PdfDictionary> out) {
        if (node == null) return;
        PdfArray names = node.getAsArray(PdfName.Names);
        if (names != null) {
            for (int i = 0; i + 1 < names.size(); i += 2) {
                PdfObject val = names.get(i + 1);
                if (val != null && val.isDictionary()) {
                    out.add((PdfDictionary) val);
                }
            }
        }
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                PdfDictionary kid = kids.getAsDictionary(i);
                collectFromNamesTree(kid, out);
            }
        }
    }
}
