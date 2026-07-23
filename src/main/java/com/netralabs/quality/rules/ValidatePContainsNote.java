package com.netralabs.quality.rules;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * PAC "P elements contain Note elements" — flags Notes nested inside a P
 * struct element. Note is a block-level construct and should not appear
 * inside a paragraph. Emits one ERROR per offending P; nothing otherwise
 * (leaf resolves to NA).
 */
public class ValidatePContainsNote implements Rule {

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s == null || !"P".equals(s.getValue())) return;
      if (!hasDescendantWithRole(se, "Note")) return;
      int page = StructUtils.pageNumOf(pdf, se);
      out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_P_CONTAINS_NOTE, page, null,
          "P element contains a Note element"));
    });
    return out;
  }

  private static boolean hasDescendantWithRole(PdfDictionary node, String role) {
    PdfObject k = node.get(PdfName.K);
    if (k == null) return false;
    if (k.isDictionary()) return match((PdfDictionary) k, role);
    if (k.isArray()) {
      PdfArray arr = (PdfArray) k;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary() && match((PdfDictionary) item, role)) return true;
      }
    }
    return false;
  }

  private static boolean match(PdfDictionary kid, String role) {
    if (!StructUtils.isStructElem(kid)) return false;
    PdfName s = kid.getAsName(PdfName.S);
    if (s != null && role.equals(s.getValue())) return true;
    return hasDescendantWithRole(kid, role);
  }
}