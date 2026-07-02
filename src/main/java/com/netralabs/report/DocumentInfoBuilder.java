package com.netralabs.report;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.netralabs.basic.naturallanguage.LangUtils;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.metadata.XMPMetaHelper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class DocumentInfoBuilder {

  private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
  private static final String NO_TITLE_PLACEHOLDER = "(no title)";
  private static final String NO_LANGUAGE_PLACEHOLDER = "(no language)";

  private DocumentInfoBuilder() {}

  public static DocumentInfoDTO build(PdfDocument pdf, String documentPath) {
    Path path = Paths.get(documentPath).toAbsolutePath();
    long sizeBytes = fileSizeOrZero(path);
    return DocumentInfoDTO.builder()
        .title(resolveTitle(pdf))
        .filename(path.getFileName().toString())
        .language(resolveLanguage(pdf))
        .pages(pdf.getNumberOfPages())
        .tags(countStructElements(pdf))
        .sizeBytes(sizeBytes)
        .size(formatSize(sizeBytes))
        .build();
  }

  private static String resolveLanguage(PdfDocument pdf) {
    try {
      String lang = LangUtils.docLang(pdf);
      if (lang != null && !lang.isBlank()) return lang;
    } catch (Exception e) {
      log.debug("Failed to read document language", e);
    }
    return NO_LANGUAGE_PLACEHOLDER;
  }

  private static String resolveTitle(PdfDocument pdf) {
    String xmpTitle = xmpTitle(pdf);
    if (xmpTitle != null) return xmpTitle;
    String infoTitle = pdf.getDocumentInfo() != null ? pdf.getDocumentInfo().getTitle() : null;
    if (infoTitle != null && !infoTitle.isBlank()) return infoTitle;
    return NO_TITLE_PLACEHOLDER;
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

  private static int countStructElements(PdfDocument pdf) {
    AtomicInteger count = new AtomicInteger();
    StructWalk.walk(pdf, (PdfStructElem e) -> count.incrementAndGet());
    return count.get();
  }

  private static long fileSizeOrZero(Path path) {
    try {
      return Files.size(path);
    } catch (IOException e) {
      log.debug("Unable to read file size for {}", path, e);
      return 0L;
    }
  }

  private static String formatSize(long bytes) {
    if (bytes <= 0) return "0 B";
    if (bytes < 1024) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024) return Math.round(kb) + " KB";
    double mb = kb / 1024.0;
    if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
    double gb = mb / 1024.0;
    return String.format(Locale.ROOT, "%.1f GB", gb);
  }
}
