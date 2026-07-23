package com.netralabs.quality.rules;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * PAC "Validity of alternative texts" — one finding per struct element that
 * carries an {@code /Alt} entry. PASSED when the value is a non-blank string;
 * ERROR when it is empty or all-whitespace. Emits nothing on docs without any
 * {@code /Alt} entries; the leaf resolves to NA.
 */
public class ValidateAltTextValidity implements Rule {

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfString alt = se.getAsString(PdfName.Alt);
      if (alt == null) return;
      int page = StructUtils.pageNumOf(pdf, se);
      String value = alt.toUnicodeString();
      if (value != null && !value.trim().isEmpty()) {
        out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_ALT_TEXT_VALIDITY, page, null));
      } else {
        out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_ALT_TEXT_VALIDITY, page, null,
            "Alternative text is empty"));
      }
    });
    return out;
  }
}