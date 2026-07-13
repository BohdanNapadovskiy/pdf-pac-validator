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
 * PAC "Alternative text on text elements" — {@code /Alt} on text-carrying
 * struct elements (P, Span, H*, LI, LBody, TD, TH) suppresses the underlying
 * text for assistive tech, which is almost always an authoring mistake. Emits
 * one ERROR per offending element; nothing on clean docs (leaf resolves to NA).
 */
public class ValidateAltOnTextElements implements Rule {

  private static final Set<String> TEXT_ROLES = Set.of(
      "P", "Span", "H", "H1", "H2", "H3", "H4", "H5", "H6",
      "LI", "LBody", "Lbl", "TD", "TH", "Caption");

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    StructUtils.walkStructure(pdf, (parent, se) -> {
      if (!se.containsKey(PdfName.Alt)) return;
      PdfName s = se.getAsName(PdfName.S);
      if (s == null) return;
      if (!TEXT_ROLES.contains(s.getValue())) return;
      int page = StructUtils.pageNumOf(pdf, se);
      out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_ALT_ON_TEXT_ELEMENTS, page, null,
          "Text structure element \"" + s.getValue() + "\" carries an /Alt entry"));
    });
    return out;
  }
}