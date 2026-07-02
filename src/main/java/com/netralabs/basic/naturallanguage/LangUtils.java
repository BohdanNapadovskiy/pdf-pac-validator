package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public class LangUtils {

    private static final Pattern BCP47 =
            Pattern.compile("(?i)^[a-z]{2,3}(-[a-z]{3}){0,3}(-[a-z]{4})?(-[a-z]{2}|-\\d{3})?(-[a-z0-9]{5,8}|-\\d[a-z0-9]{3})*(-[a-wy-z0-9]-[a-z0-9]{2,8})*(-x(-[a-z0-9]{1,8})+)?$");
    private LangUtils(){}

    /**
     * Decode a PdfString /Lang value. iText's {@code PdfString.getValue()} returns raw
     * bytes as Latin-1, which garbles UTF-16-BE-encoded strings (BOM {@code FE FF} +
     * two-byte chars). {@code toUnicodeString()} handles the BOM and 16-bit chars.
     * Always route /Lang reads through this helper.
     */
    public static String pdfStringValue(PdfString s) {
        return s == null ? null : s.toUnicodeString();
    }

    /** Document default language (Catalog /Lang), null if missing. */
    public static String docLang(PdfDocument pdf) {
        return pdfStringValue(pdf.getCatalog().getPdfObject().getAsString(PdfName.Lang));
    }

    /** Very tolerant BCP-47 (RFC 5646) check; accepts “en”, “en-US”, “zh-Hant-TW”, “de-CH-1901”, etc. */
    public static boolean isValidBCP47(String tag) {
        if (tag == null) return false;
        String t = tag.trim();
        // allow underscores but treat as invalid (many producers use en_US)
        if (t.contains("_")) return false;
        // basic/extended language tag (no need to be 100% exhaustive for validation)
        String re =
                "^[A-Za-z]{2,3}(-[A-Za-z]{3}){0,3}" +                // language + extlang
                        "(-[A-Za-z]{4})?" +                                   // script
                        "(-([A-Za-z]{2}|\\d{3}))?" +                          // region
                        "(-([A-Za-z0-9]{5,8}|\\d[A-Za-z0-9]{3}))*" +          // variants
                        "(-[0-9A-WY-Za-wy-z](-[A-Za-z0-9]{2,8})+)*" +         // extensions
                        "(-x(-[A-Za-z0-9]{1,8})+)?$";                         // private use
        return t.matches(re);
    }

    static boolean isValidLang(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.isEmpty() || s.contains("_")) return false;     // reject underscores
        for (int i = 0; i < s.length(); i++) {                // ASCII-only
            char c = s.charAt(i);
            if (!(c == '-' || (c >= '0' && c <= '9') ||
                    (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')))
                return false;
        }
        return BCP47.matcher(s).matches();
    }

    /** Resolve /Lang for a StructElem by inheriting up /P … StructTreeRoot, else fallback to docLang. */
    public static String resolveStructElemLang(PdfDictionary se, PdfDictionary structTreeRoot, String docLang) {
        PdfDictionary cur = se;
        while (cur != null && cur != structTreeRoot) {
            String s = pdfStringValue(cur.getAsString(PdfName.Lang));
            if (s != null && !s.isBlank()) return s;
            PdfObject p = cur.get(new PdfName("P"));
            cur = (p != null && p.isDictionary()) ? (PdfDictionary) p : null;
        }
        return docLang;
    }

    /** Walk the structure tree and visit each StructElem dictionary. */
    public static void walkStructElems(PdfDocument pdf, Consumer<PdfDictionary> visitor) {
        PdfDictionary str = pdf.getCatalog().getPdfObject().getAsDictionary(new PdfName("StructTreeRoot"));
        if (str == null) return;
        PdfObject k = str.get(PdfName.K);
        walkK(str, k, visitor);
    }
    private static void walkK(PdfDictionary parent, PdfObject k, Consumer<PdfDictionary> v) {
        if (k == null) return;
        if (k.isDictionary()) {
            PdfDictionary d = (PdfDictionary) k;
            if (isStructElem(d)) { v.accept(d); walkK(d, d.get(PdfName.K), v); }
        } else if (k.isArray()) {
            PdfArray a = (PdfArray) k;
            for (int i = 0; i < a.size(); i++) {
                PdfObject o = a.get(i);
                if (o != null && o.isDictionary()) {
                    PdfDictionary d = (PdfDictionary) o;
                    if (isStructElem(d)) { v.accept(d); walkK(d, d.get(PdfName.K), v); }
                }
            }
        }
    }


    private static boolean isStructElem(PdfDictionary d) {
        PdfName type = d.getAsName(PdfName.Type);
        return PdfName.StructElem.equals(type) || d.containsKey(PdfName.S);
    }
}
