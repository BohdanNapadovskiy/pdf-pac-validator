package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.*;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public class FontUtils {
    private FontUtils() {}

    public static void forEachFontOnPage(PdfDocument pdf, int page,
                                         BiConsumer<PdfName, PdfDictionary> visitor) {
        PdfDictionary res = pdf.getPage(page).getResources().getPdfObject();
        if (res == null) return;
        visitResources(res, new HashSet<>(), visitor);
    }

    private static void visitResources(PdfDictionary resources, Set<Integer> seen,
                                       BiConsumer<PdfName, PdfDictionary> visitor) {
        if (resources == null) return;

        PdfDictionary fonts = resources.getAsDictionary(PdfName.Font);
        if (fonts != null) {
            for (PdfName fname : fonts.keySet()) {
                PdfDictionary font = fonts.getAsDictionary(fname);
                if (font == null) continue;
                PdfIndirectReference ref = font.getIndirectReference();
                Integer id = (ref != null) ? ref.getObjNumber() : System.identityHashCode(font);
                if (seen.add(id)) visitor.accept(fname, font);
            }
        }

        PdfDictionary xobjs = resources.getAsDictionary(PdfName.XObject);
        if (xobjs != null) {
            for (PdfName xn : xobjs.keySet()) {
                PdfStream xo = xobjs.getAsStream(xn);
                if (xo == null) continue;
                if (PdfName.Form.equals(xo.getAsName(PdfName.Subtype))) {
                    visitResources(xo.getAsDictionary(PdfName.Resources), seen, visitor);
                }
            }
        }

        PdfDictionary patterns = resources.getAsDictionary(PdfName.Pattern);
        if (patterns != null) {
            for (PdfName pn : patterns.keySet()) {
                PdfStream pat = patterns.getAsStream(pn);
                if (pat == null) continue;
                PdfNumber t = pat.getAsNumber(PdfName.PatternType);
                if (t != null && t.intValue() == 1) {
                    visitResources(pat.getAsDictionary(PdfName.Resources), seen, visitor);
                }
            }
        }
    }

    public static PdfDictionary firstDescendantCidFont(PdfDictionary type0) {
        PdfArray arr = type0.getAsArray(PdfName.DescendantFonts);
        if (arr == null || arr.isEmpty()) return null;
        PdfObject o = arr.get(0);
        return (o != null && o.isDictionary()) ? (PdfDictionary) o : null;
    }

    public static PdfObject encodingOf(PdfDictionary type0) {
        return type0.get(PdfName.Encoding);
    }

    public static boolean hasValidCidToGidMap(PdfDictionary cidFont) {
        if (cidFont == null) return false;
        PdfName subtype = cidFont.getAsName(PdfName.Subtype);
        if (!PdfName.CIDFontType2.equals(subtype)) return false;
        PdfObject m = cidFont.get(PdfName.CIDToGIDMap);
        if (m == null) return false;
        if (m.isName()) return PdfName.Identity.equals(m);
        return m.isStream(); // stream mapping is okay
    }

    public static boolean isPredefinedOrEmbeddedCMap(PdfObject enc) {
        if (enc == null) return false;
        if (enc.isStream()) {                     // embedded CMap stream => /Type /CMap recommended
            PdfStream s = (PdfStream) enc;
            PdfName cmapType = new PdfName("CMap");
            PdfName type = s.getAsName(PdfName.Type);
            return cmapType.equals(type) || type == null; // some files omit /Type but it's still a CMap stream
        }
        return false;
    }

    public static boolean isTrueTypeSimple(PdfDictionary font) {
        return PdfName.TrueType.equals(font.getAsName(PdfName.Subtype));
    }

    public static boolean isSymbolic(PdfDictionary font) {
        PdfDictionary fd = font.getAsDictionary(PdfName.FontDescriptor);
        if (fd == null) return false;
        PdfNumber flags = fd.getAsNumber(PdfName.Flags);
        if (flags == null) return false;
        return (flags.intValue() & 0x04) != 0;
    }

    public static PdfObject getEncoding(PdfDictionary font) {
        return font.get(PdfName.Encoding);
    }

    public static String fontResName(PdfName fname, PdfDictionary font) {
        String n = fname != null ? fname.getValue() : null;
        PdfName base = font.getAsName(PdfName.BaseFont);
        return (base != null ? base.getValue() : n);
    }

    public static boolean isWinAnsiOrMacRomanName(PdfObject enc) {
        if (enc == null || !enc.isName()) return false;
        String v = ((PdfName) enc).getValue();
        return "WinAnsiEncoding".equals(v) || "MacRomanEncoding".equals(v);
    }

    public static boolean isIdentityName(PdfObject enc) {
        if (enc == null || !enc.isName()) return false;
        PdfName n = (PdfName) enc;
//        return PdfName.IdentityH.equals(n) || PdfName.IdentityV.equals(n);
        return PdfName.Identity.equals(n);
    }

    public static boolean baseIsWinAnsiOrMacRoman(PdfDictionary encDict) {
        if (encDict == null) return false;
        PdfName base = encDict.getAsName(PdfName.BaseEncoding);
        if (base == null) return false;
        String v = base.getValue();
        return "WinAnsiEncoding".equals(v) || "MacRomanEncoding".equals(v);
    }
}
