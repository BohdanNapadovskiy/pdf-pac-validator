package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.xmp.XMPConst;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.netralabs.basic.naturallanguage.LangUtils.isValidLang;
import static com.netralabs.domain.PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

/**
 * PDF/UA-1 §7.2 — the document must declare a natural language, and every /Lang value
 * that IS declared must be a valid BCP-47 tag.
 * <p>
 * Emits exactly one finding per document to match PAC's per-document reporting
 * granularity for this checkpoint:
 * <ul>
 *   <li>ERROR — no /Lang or XMP dc:language present anywhere, OR any encountered
 *       /Lang value is not a valid BCP-47 tag.</li>
 *   <li>PASSED — at least one /Lang or dc:language is present and every encountered
 *       value is valid.</li>
 * </ul>
 */
@Slf4j
public class ValidateLangAttributeCorrectness implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        boolean sawAnyLang = false;
        boolean anyInvalid = false;

        PdfDictionary cat = pdf.getCatalog().getPdfObject();
        PdfString catLang = cat.getAsString(PdfName.Lang);
        if (catLang != null) {
            sawAnyLang = true;
            if (!isValidLang(LangUtils.pdfStringValue(catLang))) anyInvalid = true;
        }

        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfString pl = pdf.getPage(i).getPdfObject().getAsString(PdfName.Lang);
            if (pl != null) {
                sawAnyLang = true;
                if (!isValidLang(LangUtils.pdfStringValue(pl))) anyInvalid = true;
            }
            PdfArray annots = pdf.getPage(i).getPdfObject().getAsArray(PdfName.Annots);
            if (annots != null) {
                for (int j = 0; j < annots.size(); j++) {
                    PdfDictionary a = annots.getAsDictionary(j);
                    if (a == null) continue;
                    PdfString al = a.getAsString(PdfName.Lang);
                    if (al != null) {
                        sawAnyLang = true;
                        if (!isValidLang(LangUtils.pdfStringValue(al))) anyInvalid = true;
                    }
                }
            }
        }

        PdfDictionary str = cat.getAsDictionary(new PdfName("StructTreeRoot"));
        if (str != null) {
            Deque<PdfDictionary> stack = new ArrayDeque<>();
            pushKids(str.get(PdfName.K), stack);
            while (!stack.isEmpty()) {
                PdfDictionary se = stack.pop();
                PdfString l = se.getAsString(PdfName.Lang);
                if (l != null) {
                    sawAnyLang = true;
                    if (!isValidLang(LangUtils.pdfStringValue(l))) anyInvalid = true;
                }
                pushKids(se.get(PdfName.K), stack);
            }
        }

        PdfDictionary acro = cat.getAsDictionary(PdfName.AcroForm);
        if (acro != null) {
            PdfString afl = acro.getAsString(PdfName.Lang);
            if (afl != null) {
                sawAnyLang = true;
                if (!isValidLang(LangUtils.pdfStringValue(afl))) anyInvalid = true;
            }
        }

        // XMP dc:language (bag). Read tolerantly — a broken XMP shouldn't crash the rule.
        try {
            XMPMeta xmp = pdf.getXmpMetadata();
            if (xmp != null) {
                int n = xmp.countArrayItems(XMPConst.NS_DC, "language");
                for (int i = 1; i <= n; i++) {
                    String v = xmp.getArrayItem(XMPConst.NS_DC, "language", i).getValue();
                    if (v == null || v.trim().isEmpty()) continue;
                    sawAnyLang = true;
                    if (!isValidLang(v.trim())) anyInvalid = true;
                }
            }
        } catch (Exception e) {
            log.debug("XMP dc:language read failed", e);
        }

        List<FindingDTO> out = new ArrayList<>(1);
        if (!sawAnyLang || anyInvalid) {
            out.add(new FindingDTO(ERROR, CORRECTNESS_LANGUAGE_ATR, 0, null));
        } else {
            out.add(new FindingDTO(PASSED, CORRECTNESS_LANGUAGE_ATR, 0, null));
        }
        return out;
    }

    private static void pushKids(PdfObject k, Deque<PdfDictionary> stack) {
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
}
