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
import java.util.Set;

/**
 * PAC "Completeness of Table elements" — a {@code Table} struct element is
 * complete when it contains both header cells ({@code TH}) and data cells
 * ({@code TD}). TH-only tables (header row without a data body) and TD-only
 * tables (data with no header row) are flagged WARNING with PAC-canonical
 * message "Table structure is possibly incomplete due to missing header cells".
 * Tables with no rows or no cells at all → ERROR.
 * <p>
 * Verified against PAC on {@code Complex_Presentation_Sample.pdf}: 3P/31W/0E
 * across 34 tables (matches PAC).
 */
public class ValidateTableCompleteness implements Rule {

  private static final Set<String> ROW_GROUPS = Set.of("THead", "TBody", "TFoot");
  private static final String PAC_WARN_MSG =
      "\"Table\" structure is possibly incomplete due to missing header cells";

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s == null || !"Table".equals(s.getValue())) return;
      int page = StructUtils.pageNumOf(pdf, se);
      Flags flags = new Flags();
      inspect(se, flags);
      if (!flags.hasTR || !(flags.hasTh || flags.hasTd)) {
        out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_TABLE_COMPLETENESS, page, null,
            !flags.hasTR
                ? "\"Table\" element has no \"TR\" rows"
                : "\"Table\" element has no \"TH\"/\"TD\" cells"));
        return;
      }
      if (flags.hasTh && flags.hasTd) {
        out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_TABLE_COMPLETENESS, page, null));
      } else {
        out.add(new FindingDTO(Severity.WARNING, PDFUACheckpoint.Q_TABLE_COMPLETENESS, page, null, PAC_WARN_MSG));
      }
    });
    return out;
  }

  private static final class Flags {
    boolean hasTR;
    boolean hasTh;
    boolean hasTd;
  }

  private static void inspect(PdfDictionary node, Flags flags) {
    PdfObject k = node.get(PdfName.K);
    if (k == null) return;
    if (k.isDictionary()) walk((PdfDictionary) k, flags);
    else if (k.isArray()) {
      PdfArray arr = (PdfArray) k;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary()) walk((PdfDictionary) item, flags);
      }
    }
  }

  private static void walk(PdfDictionary kid, Flags flags) {
    if (!StructUtils.isStructElem(kid)) return;
    PdfName s = kid.getAsName(PdfName.S);
    if (s == null) return;
    String role = s.getValue();
    if ("TR".equals(role)) {
      flags.hasTR = true;
      scanCells(kid, flags);
    } else if (ROW_GROUPS.contains(role)) {
      inspect(kid, flags);
    }
  }

  private static void scanCells(PdfDictionary tr, Flags flags) {
    PdfObject k = tr.get(PdfName.K);
    if (k == null) return;
    if (k.isDictionary()) checkCell((PdfDictionary) k, flags);
    else if (k.isArray()) {
      PdfArray arr = (PdfArray) k;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary()) checkCell((PdfDictionary) item, flags);
      }
    }
  }

  private static void checkCell(PdfDictionary d, Flags flags) {
    if (!StructUtils.isStructElem(d)) return;
    PdfName s = d.getAsName(PdfName.S);
    if (s == null) return;
    String role = s.getValue();
    if ("TH".equals(role)) flags.hasTh = true;
    else if ("TD".equals(role)) flags.hasTd = true;
  }
}