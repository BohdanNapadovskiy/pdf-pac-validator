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
 * PAC "Note elements contain Lbl elements" — one PASSED per {@code Note}
 * struct element that has a direct-child {@code Lbl} struct element; one
 * ERROR per Note that does not. Emits nothing on docs with no Notes.
 */
public class ValidateNoteContainsLbl implements Rule {

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s == null || !"Note".equals(s.getValue())) return;
      int page = StructUtils.pageNumOf(pdf, se);
      if (hasDirectChildRole(se, "Lbl")) {
        out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_NOTE_CONTAINS_LBL, page, null));
      } else {
        out.add(new FindingDTO(Severity.WARNING, PDFUACheckpoint.Q_NOTE_CONTAINS_LBL, page, null,
            "\"Note\" element does not contain a \"Lbl\" element"));
      }
    });
    return out;
  }

  private static boolean hasDirectChildRole(PdfDictionary node, String role) {
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
    return s != null && role.equals(s.getValue());
  }
}