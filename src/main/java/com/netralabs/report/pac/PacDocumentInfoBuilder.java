package com.netralabs.report.pac;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.netralabs.basic.naturallanguage.LangUtils;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.metadata.XMPMetaHelper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Populates {@link DocumentInformationDTO} straight from the source PDF —
 * matches the shape PAC emits in its {@code documentInformation} block.
 */
@Slf4j
public final class PacDocumentInfoBuilder {

    private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
    private static final String REPORT_LANGUAGE = "en";

    private PacDocumentInfoBuilder() {}

    public static DocumentInformationDTO build(PdfDocument pdf, String documentPath) {
        DocumentInformationDTO dto = new DocumentInformationDTO();
        Path path = Paths.get(documentPath).toAbsolutePath();

        dto.setPageCount(pdf.getNumberOfPages());
        dto.setTitle(resolveTitle(pdf));
        dto.setSubject(nullIfBlank(pdf.getDocumentInfo() != null ? pdf.getDocumentInfo().getSubject() : null));
        dto.setAuthor(pdf.getDocumentInfo() != null ? nvl(pdf.getDocumentInfo().getAuthor()) : "");
        dto.setKeywords(nullIfBlank(pdf.getDocumentInfo() != null ? pdf.getDocumentInfo().getKeywords() : null));
        dto.setCreator(nullIfBlank(pdf.getDocumentInfo() != null ? pdf.getDocumentInfo().getCreator() : null));
        dto.setProducer(nullIfBlank(pdf.getDocumentInfo() != null ? pdf.getDocumentInfo().getProducer() : null));

        PdfDictionary info = pdf.getTrailer() != null ? pdf.getTrailer().getAsDictionary(PdfName.Info) : null;
        dto.setCreationDate(readIsoDate(info, PdfName.CreationDate));
        dto.setModificationDate(readIsoDate(info, PdfName.ModDate));

        dto.setLanguage(resolveLanguage(pdf));
        dto.setIds(resolveIds(pdf));
        dto.setIsTagged(StructUtils.isTaggedPdf(pdf));
        dto.setReportLanguage(REPORT_LANGUAGE);
        dto.setNumberOfTags(countStructElements(pdf));
        dto.setSizeInKb(fileSizeKb(path));

        return dto;
    }

    private static String resolveTitle(PdfDocument pdf) {
        String xmpTitle = xmpTitle(pdf);
        if (xmpTitle != null) return xmpTitle;
        String infoTitle = pdf.getDocumentInfo() != null ? pdf.getDocumentInfo().getTitle() : null;
        return nullIfBlank(infoTitle);
    }

    private static String xmpTitle(PdfDocument pdf) {
        XMPMeta xmp = XMPMetaHelper.tryGetXmpMeta(pdf);
        if (xmp == null) return null;
        try {
            int n = xmp.countArrayItems(DC_NS, "title");
            for (int i = 1; i <= n; i++) {
                String v = xmp.getArrayItem(DC_NS, "title", i).getValue();
                if (v != null && !v.isBlank()) return v;
            }
        } catch (XMPException e) {
            log.debug("Failed to read XMP title", e);
        }
        return null;
    }

    private static String resolveLanguage(PdfDocument pdf) {
        try {
            String lang = LangUtils.docLang(pdf);
            if (lang != null && !lang.isBlank()) return lang;
        } catch (Exception e) {
            log.debug("Failed to read document language", e);
        }
        return null;
    }

    /**
     * PDF file identifier — two hex-encoded strings joined with ", " matching PAC's
     * concatenated {@code ids} field.
     */
    private static String resolveIds(PdfDocument pdf) {
        if (pdf.getTrailer() == null) return null;
        PdfArray idArr = pdf.getTrailer().getAsArray(PdfName.ID);
        if (idArr == null || idArr.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idArr.size(); i++) {
            PdfString s = idArr.getAsString(i);
            if (s == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(HexFormat.of().withUpperCase().formatHex(s.getValueBytes()));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * Convert a PDF date string ({@code D:YYYYMMDDHHmmSSOHH'mm'}) to ISO 8601.
     * Returns null when the entry is absent or unparseable.
     */
    private static String readIsoDate(PdfDictionary info, PdfName key) {
        if (info == null) return null;
        PdfString raw = info.getAsString(key);
        if (raw == null) return null;
        String value = raw.getValue();
        if (value == null || value.isBlank()) return null;
        try {
            return pdfDateToIso(value);
        } catch (RuntimeException e) {
            log.debug("Unparseable PDF date '{}'", value, e);
            return value;
        }
    }

    private static String pdfDateToIso(String s) {
        String v = s.trim();
        if (v.startsWith("D:")) v = v.substring(2);
        // YYYYMMDDHHmmSS + optional timezone
        int len = v.length();
        int year   = intAt(v, 0, 4, 1970);
        int month  = safeInt(v, 4, 6, 1);
        int day    = safeInt(v, 6, 8, 1);
        int hour   = safeInt(v, 8, 10, 0);
        int minute = safeInt(v, 10, 12, 0);
        int second = safeInt(v, 12, 14, 0);

        String tz = "Z";
        if (len > 14) {
            char sign = v.charAt(14);
            if (sign == 'Z') {
                tz = "Z";
            } else if (sign == '+' || sign == '-') {
                int tzH = safeInt(v, 15, 17, 0);
                int tzM = 0;
                // Skip the "'" separator between hour and minute if present.
                int minStart = 17;
                if (minStart < len && v.charAt(minStart) == '\'') minStart++;
                tzM = safeInt(v, minStart, minStart + 2, 0);
                tz = String.format("%c%02d:%02d", sign, tzH, tzM);
            }
        }
        return String.format("%04d-%02d-%02dT%02d:%02d:%02d%s",
                year, month, day, hour, minute, second, tz);
    }

    private static int intAt(String s, int start, int end, int fallback) {
        if (end > s.length()) return fallback;
        try { return Integer.parseInt(s.substring(start, end)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static int safeInt(String s, int start, int end, int fallback) {
        return intAt(s, start, end, fallback);
    }

    private static int countStructElements(PdfDocument pdf) {
        AtomicInteger count = new AtomicInteger();
        StructWalk.walk(pdf, (PdfStructElem e) -> count.incrementAndGet());
        return count.get();
    }

    private static long fileSizeKb(Path path) {
        try {
            long bytes = Files.size(path);
            return Math.round(bytes / 1024.0);
        } catch (IOException e) {
            log.debug("Unable to read file size for {}", path, e);
            return 0L;
        }
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
