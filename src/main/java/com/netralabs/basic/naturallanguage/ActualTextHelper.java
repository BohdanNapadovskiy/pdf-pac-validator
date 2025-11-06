package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.List;

public class ActualTextHelper {

    public static String getStr(PdfString s) {
        return s == null ? null : s.getValue();
    }

    public static String firstNonBlank(String... v) {
        for (String s : v) if (s != null && !s.isBlank()) return s;
        return null;
    }

    public static int pageOf(PdfStructElem elem, PdfDocument pdf) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        if (pg == null) return 0;
        PdfPage p = pdf.getPage(pg);
        return p == null ? 0 : pdf.getPageNumber(p);
    }

    public static String pageLangOf(PdfStructElem elem) {
        PdfDictionary pg = elem.getPdfObject().getAsDictionary(PdfName.Pg);
        return (pg == null) ? null : pageLangOf(pg);
    }

    public static String pageLangOf(PdfDictionary pgDict) {
        PdfString s = pgDict.getAsString(PdfName.Lang);
        return getStr(s);
    }

    public static void addLangFinding(List<FindingDTO> out, String lang,
                                      PDFUACheckpoint checkpoint, int pageNum) {
        if (lang == null || lang.isBlank() || !LangUtils.isValidBCP47(lang)) {
            out.add(new FindingDTO(Severity.ERROR, checkpoint, pageNum, null));
        } else {
            out.add(new FindingDTO(Severity.PASSED, checkpoint, pageNum, null));
        }
    }
}
