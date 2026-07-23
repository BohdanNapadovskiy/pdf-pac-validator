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
 * PAC "Formal correctness of LI elements" — an {@code LI} struct element must
 * contain <em>exactly one</em> {@code LBody} child. Zero or multiple LBody
 * children → ERROR with PAC-canonical message. Extra {@code Lbl} children are
 * permitted. Emits one PASSED per formally correct LI; nothing on docs
 * without LI elements (leaf resolves to NA).
 */
public class ValidateLiFormalCorrectness implements Rule {

  private static final String PAC_MESSAGE =
      "\"LI\" element must contain exactly one \"LBody\" element";

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s == null || !"LI".equals(s.getValue())) return;
      int page = StructUtils.pageNumOf(pdf, se);
      int lbodyCount = countChildRole(se, "LBody");
      if (lbodyCount == 1) {
        out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_LI_FORMAL_CORRECTNESS, page, null));
      } else {
        out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_LI_FORMAL_CORRECTNESS, page, null, PAC_MESSAGE));
      }
    });
    return out;
  }

  private static int countChildRole(PdfDictionary li, String role) {
    PdfObject k = li.get(PdfName.K);
    if (k == null) return 0;
    if (k.isDictionary()) return matches((PdfDictionary) k, role) ? 1 : 0;
    if (k.isArray()) {
      int n = 0;
      PdfArray arr = (PdfArray) k;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary() && matches((PdfDictionary) item, role)) n++;
      }
      return n;
    }
    return 0;
  }

  private static boolean matches(PdfDictionary kid, String role) {
    if (!StructUtils.isStructElem(kid)) return false;
    PdfName s = kid.getAsName(PdfName.S);
    return s != null && role.equals(s.getValue());
  }
}