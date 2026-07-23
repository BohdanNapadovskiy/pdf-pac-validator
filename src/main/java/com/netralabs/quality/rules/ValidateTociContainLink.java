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
 * PAC "TOCI elements contain Link elements" — for each {@code TOCI} struct
 * element in the tree, emit one PASSED if any descendant is a {@code Link}
 * struct element, one ERROR otherwise.
 */
public class ValidateTociContainLink implements Rule {

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s == null || !"TOCI".equals(s.getValue())) return;
      int page = StructUtils.pageNumOf(pdf, se);
      boolean hasLink = containsDescendantWithRole(se, "Link");
      if (hasLink) {
        out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_TOCI_CONTAIN_LINK, page, null));
      } else {
        out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_TOCI_CONTAIN_LINK, page, null,
            "TOCI element does not contain a Link element"));
      }
    });
    return out;
  }

  static boolean containsDescendantWithRole(PdfDictionary node, String role) {
    PdfObject k = node.get(PdfName.K);
    if (k == null) return false;
    if (k.isDictionary()) return matches((PdfDictionary) k, role);
    if (k.isArray()) {
      PdfArray arr = (PdfArray) k;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary() && matches((PdfDictionary) item, role)) return true;
      }
    }
    return false;
  }

  private static boolean matches(PdfDictionary kid, String role) {
    if (!StructUtils.isStructElem(kid)) return false;
    PdfName s = kid.getAsName(PdfName.S);
    if (s != null && role.equals(s.getValue())) return true;
    return containsDescendantWithRole(kid, role);
  }
}