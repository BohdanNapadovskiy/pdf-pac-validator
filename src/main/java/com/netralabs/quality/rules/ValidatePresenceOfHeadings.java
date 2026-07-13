package com.netralabs.quality.rules;

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
 * PAC "Presence of headings" — emits one PASSED per tagged document containing
 * at least one heading ({@code H}, {@code H1}..{@code H6}). Rule is untagged-safe:
 * emits nothing when the document isn't tagged, so the leaf resolves to NA
 * (matches PAC's dash on untagged docs).
 */
public class ValidatePresenceOfHeadings implements Rule {

  private static final Set<String> HEADING_ROLES =
      Set.of("H", "H1", "H2", "H3", "H4", "H5", "H6");

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    boolean[] found = new boolean[1];
    StructUtils.walkStructure(pdf, (parent, se) -> {
      if (found[0]) return;
      PdfName s = se.getAsName(PdfName.S);
      if (s != null && HEADING_ROLES.contains(s.getValue())) found[0] = true;
    });

    if (found[0]) {
      out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_PRESENCE_HEADINGS, 0, null));
    } else {
      out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_PRESENCE_HEADINGS, 0, null,
          "Document has no heading structure elements"));
    }
    return out;
  }
}