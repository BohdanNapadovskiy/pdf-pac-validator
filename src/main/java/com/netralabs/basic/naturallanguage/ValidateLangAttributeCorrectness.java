package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.xmp.XMPConst;
import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.netralabs.basic.naturallanguage.ContentLangWalker.walkPage;
import static com.netralabs.basic.naturallanguage.LangUtils.*;
import static com.netralabs.domain.PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR;

public class ValidateLangAttributeCorrectness implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        List<String> problems = new ArrayList<>();

        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfDictionary pg = pdf.getPage(i).getPdfObject();
            PdfString pl = pg.getAsString(PdfName.Lang);
            if (pl != null && !isValidLang(pl.getValue()))
                problems.add("Page " + i + " /Lang='" + pl.getValue() + "'");
        }

        PdfDictionary cat = pdf.getCatalog().getPdfObject();
        PdfString catLang = cat.getAsString(PdfName.Lang);
        if (catLang != null && !isValidLang(catLang.getValue()))
            problems.add("Catalog /Lang='" + catLang.getValue() + "'");

        PdfDictionary str = cat.getAsDictionary(new PdfName("StructTreeRoot"));
        if (str != null) {
            Deque<PdfDictionary> stack = new ArrayDeque<>();
            pushKids(str.get(PdfName.K), stack);
            while (!stack.isEmpty()) {
                PdfDictionary se = stack.pop();
                PdfString l = se.getAsString(PdfName.Lang);
                if (l != null && !isValidLang(l.getValue()))
                    problems.add("StructElem obj#" + objNum(se) + " /Lang='" + l.getValue() + "'");
                pushKids(se.get(PdfName.K), stack);
            }
        }

        PdfDictionary acro = cat.getAsDictionary(PdfName.AcroForm);
        if (acro != null) {
            PdfString afl = acro.getAsString(PdfName.Lang);
            if (afl != null && !isValidLang(afl.getValue()))
                problems.add("AcroForm /Lang='" + afl.getValue() + "'");
        }
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfArray annots = pdf.getPage(i).getPdfObject().getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int j = 0; j < annots.size(); j++) {
                PdfDictionary a = annots.getAsDictionary(j);
                if (a == null) continue;
                PdfString al = a.getAsString(PdfName.Lang);
                if (al != null && !isValidLang(al.getValue()))
                    problems.add("Annot on page " + i + " /Lang='" + al.getValue() + "'");
            }
        }



//        AtomicBoolean sawAnyLang = new AtomicBoolean(false);
//
//        String dl = docLang(pdf);
//        if (dl != null) {
//            sawAnyLang.set(true);
//            if (!isValidBCP47(dl))
//                out.add(new FindingDTO(Severity.ERROR, CORRECTNESS_LANGUAGE_ATR, 0, null));
//        }
//        // Structure elements
//        walkStructElems(pdf, se -> {
//            PdfString s = se.getAsString(PdfName.Lang);
//            if (s != null) {
//                sawAnyLang.set(true);
//                if (isValidBCP47(s.getValue()))
//                    out.add(new FindingDTO(Severity.PASSED, CORRECTNESS_LANGUAGE_ATR, 0, null));
//                else out.add(new FindingDTO(Severity.ERROR, CORRECTNESS_LANGUAGE_ATR, 0, null));
//            }
//        });
//
//        // BDC property dictionaries with /Lang
//        for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
//            PdfPage page = pdf.getPage(p);
//            walkPage(page, (evt, payload) -> {
//                if ("LANG_PUSH".equals(evt) && payload != null && payload.isString()) {
//                    String v = ((PdfString) payload).getValue();
//                    sawAnyLang.set(true);
//                    if (!isValidBCP47(v))
//                        out.add(new FindingDTO(Severity.ERROR, CORRECTNESS_LANGUAGE_ATR, 0, null));
//                }
//            });
//        }
//        validateXmpLanguages(pdf, out);
        return out;
    }

    static void pushKids(PdfObject k, Deque<PdfDictionary> stack) {
        if (k == null) return;
        if (k.isDictionary()) {
            PdfDictionary d = (PdfDictionary) k;
            if (d.containsKey(PdfName.S)) stack.push(d);
        } else if (k.isArray()) {
            PdfArray a = (PdfArray) k;
            for (int i = 0; i < a.size(); i++) {
                PdfObject o = a.get(i);
                if (o != null && o.isDictionary() && ((PdfDictionary) o).containsKey(PdfName.S))
                    stack.push((PdfDictionary) o);
            }
        }
    }
    static String objNum(PdfDictionary d) {
        PdfIndirectReference r = d.getIndirectReference();
        return r != null ? (r.getObjNumber() + "") : "(direct)";
    }

    private void validateXmpLanguages(PdfDocument pdf, List<FindingDTO> out) {

        try {
            XMPMeta xmp = pdf.getXmpMetadata();
            if (xmp == null) return; // no XMP → skip this sub-check
            XMPMeta meta = XMPMetaFactory.parseFromBuffer(xmp.getPropertyBase64(XMPConst.NS_XMP, "XMPMeta"));

            // dc:language is an unordered array (bag)
            int n = meta.countArrayItems(XMPConst.NS_DC, "language");
            int ok = 0, bad = 0;
            for (int i = 1; i <= n; i++) {
                String lang = meta.getArrayItem(XMPConst.NS_DC, "language", i).getValue();
                if (lang != null) lang = lang.trim();
                if (lang == null || lang.isEmpty() || lang.contains("_") || !isValidBCP47(lang)) {
                    bad++;
                } else {
                    ok++;
                }
            }

            if (bad > 0) {
                out.add(new FindingDTO(Severity.ERROR,
                        PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR,
                        0,
                        null));
            } else if (n > 0 && ok == n) {
                out.add(new FindingDTO(Severity.PASSED,
                        PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR,
                        0,
                        null));
            }
        } catch (XMPException e) {
            out.add(new FindingDTO(Severity.ERROR,
                    PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR,
                    0,
                    null));
        }
    }
}
