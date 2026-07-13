package com.netralabs.quality.rules;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PAC "Presence of bookmarks (document outline) if there are headings" — gated
 * on the presence of at least one heading struct element ({@code H}/{@code H1..H6}).
 * When headings exist, emit one PASSED per doc with an {@code /Outlines}
 * dictionary carrying a {@code /First} entry, ERROR otherwise. Emits nothing
 * when the document has no headings (leaf resolves to NA — matches PAC dash).
 */
public class ValidatePresenceOfBookmarks implements Rule {

  private static final Set<String> HEADING_ROLES =
      Set.of("H", "H1", "H2", "H3", "H4", "H5", "H6");

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;
    if (!hasHeadings(pdf)) return out;

    PdfDictionary catalog = pdf.getCatalog().getPdfObject();
    PdfDictionary outlines = catalog.getAsDictionary(PdfName.Outlines);
    boolean hasBookmarks = outlines != null && outlines.get(PdfName.First) != null;

    if (hasBookmarks) {
      out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_PRESENCE_BOOKMARKS, 0, null));
    } else {
      out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_PRESENCE_BOOKMARKS, 0, null,
          "Headings are present without bookmarks (document outline)"));
    }
    return out;
  }

  private static boolean hasHeadings(PdfDocument pdf) {
    boolean[] found = new boolean[1];
    StructUtils.walkStructure(pdf, (parent, se) -> {
      if (found[0]) return;
      PdfName s = se.getAsName(PdfName.S);
      if (s != null && HEADING_ROLES.contains(s.getValue())) found[0] = true;
    });
    return found[0];
  }
}