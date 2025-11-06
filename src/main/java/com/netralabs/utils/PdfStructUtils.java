package com.netralabs.utils;

import com.itextpdf.kernel.pdf.*;
import org.verapdf.pdfa.results.ValidationResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public class PdfStructUtils {
    private PdfStructUtils() {}

    public static boolean isStructElemLike(PdfDictionary d) {
        PdfName type = d.getAsName(PdfName.Type);
        return PdfName.StructElem.equals(type) || d.containsKey(PdfName.S) || d.containsKey(PdfName.K);
    }

    public static boolean looksLikeStructOrRoot(PdfDictionary d) {
        PdfName t = d.getAsName(PdfName.Type);
        return PdfName.StructElem.equals(t) || PdfName.StructTreeRoot.equals(t) || d.containsKey(PdfName.K) || d.containsKey(PdfName.S);
    }

    public static String ident(PdfDictionary d) {
        PdfIndirectReference ref = d.getIndirectReference();
        return ref == null ? "<direct>" : (ref.getObjNumber() + " " + ref.getGenNumber() + " R");
    }

    /** Add child StructElem dictionaries from /K to 'out'. */
    public static void collectStructKids(PdfDictionary parent, List<PdfDictionary> out,
                                         ValidationResult res, String rule, String parentPath) {
        PdfObject k = parent.get(PdfName.K);
        if (k == null) return;

        if (k.isArray()) {
            PdfArray arr = (PdfArray) k;
            for (int i = 0; i < arr.size(); i++) {
                PdfObject kid = arr.get(i, true);
                inspectStructKid(kid, out, res, rule, parentPath + "/K[" + i + "]");
            }
        } else {
            inspectStructKid(k, out, res, rule, parentPath + "/K");
        }
    }

    private static void inspectStructKid(PdfObject kid, List<PdfDictionary> out,
                                         ValidationResult res, String rule, String path) {
        if (kid.isDictionary()) {
            PdfDictionary kd = (PdfDictionary) kid;
            if (isStructElemLike(kd)) out.add(kd);
        } else if (!(kid.isArray() || kid.isNumber())) {
//            res.error(rule, "Unsupported /K kid type: " + kid.getClass().getSimpleName() + " at " + path);
        }
    }

    /** Recurses into nested StructElem children of a node. */
    public static void recurseStructKids(PdfDictionary node, BiConsumer<PdfDictionary, String> onChild) {
        PdfObject k = node.get(PdfName.K);
        if (k == null) return;
        if (k.isArray()) {
            PdfArray arr = (PdfArray) k;
            for (int i = 0; i < arr.size(); i++) {
                PdfObject kid = arr.get(i, true);
                if (kid.isDictionary()) {
                    PdfDictionary kd = (PdfDictionary) kid;
                    if (isStructElemLike(kd)) onChild.accept(kd, ident(kd));
                }
            }
        } else if (k.isDictionary()) {
            PdfDictionary kd = (PdfDictionary) k;
            if (isStructElemLike(kd)) onChild.accept(kd, ident(kd));
        }
    }

    /** Iterate through /K entries and supply subPath for diagnostics. */
    public static void iterateK(PdfObject k, BiConsumer<PdfObject, String> consumer) {
        if (k.isArray()) {
            PdfArray arr = (PdfArray) k;
            for (int i = 0; i < arr.size(); i++) consumer.accept(arr.get(i, true), "/K[" + i + "]");
        } else {
            consumer.accept(k, "/K");
        }
    }

    public static PdfDictionary findOwningDictionary(PdfObject obj) {
        PdfIndirectReference ref = obj.getIndirectReference();
        if (ref != null && ref.getRefersTo() instanceof PdfDictionary) {
            return (PdfDictionary) ref.getRefersTo();
        }
        return null;
    }

//    public static int resolvePageNumber(PdfDocument pdf, PdfDictionary pageDict) {
//        PdfIndirectReference pr = pageDict.getIndirectReference();
//        if (pr == null) return -1;
//        PdfPage page = pdf.getPage(pr);
//        return page == null ? -1 : pdf.getPageNumber(page);
//    }

    public static Set<Integer> collectStructParents(PdfDocument pdf) {
        Set<Integer> keys = new HashSet<>();
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfPage page = pdf.getPage(i);
            PdfNumber sp = page.getPdfObject().getAsNumber(PdfName.StructParents);
            if (sp != null) keys.add(sp.intValue());

            PdfArray annots = page.getPdfObject().getAsArray(PdfName.Annots);
            if (annots != null) {
                for (int j = 0; j < annots.size(); j++) {
                    PdfDictionary a = annots.getAsDictionary(j);
                    if (a != null) {
                        PdfNumber asp = a.getAsNumber(PdfName.StructParent);
                        if (asp != null) keys.add(asp.intValue());
                    }
                }
            }

            PdfDictionary resources = page.getResources() != null ? page.getResources().getPdfObject() : null;
            if (resources != null) {
                PdfDictionary xobjs = resources.getAsDictionary(PdfName.XObject);
                if (xobjs != null) {
                    for (PdfName key : xobjs.keySet()) {
                        PdfObject xo = xobjs.get(key, true);
                        if (xo instanceof PdfStream) {
                            PdfNumber spx = ((PdfStream) xo).getAsNumber(PdfName.StructParents);
                            if (spx != null) keys.add(spx.intValue());
                        }
                    }
                }
            }
        }
        return keys;
    }

    public static void checkParentTreeValueObject(PdfObject o, int key, ValidationResult out, String rule) {
        if (o.isDictionary()) {
            PdfDictionary d = (PdfDictionary) o;
            PdfName t = d.getAsName(PdfName.Type);
            if (!(PdfName.MCR.equals(t) || PdfName.OBJR.equals(t) || isStructElemLike(d))) {
//                out.error(rule, "Key " + key + " maps to dict with unexpected /Type=" + t);
            }
        } else if (!o.isIndirectReference()) {
//            out.error(rule, "Key " + key + " maps to unexpected primitive: " + o.getClass().getSimpleName());
        }
    }
}
