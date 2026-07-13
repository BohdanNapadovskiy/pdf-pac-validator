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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PAC "Note elements are referenced" — a {@code Note} struct element should
 * be referenced from body content, either via a {@code Reference} struct
 * element carrying a {@code /Ref} → Note, or by any struct element whose
 * {@code /Ref} array points at the Note. Emits one PASSED per referenced Note
 * and one ERROR per unreferenced Note; nothing on docs with no Notes.
 */
public class ValidateNoteReferenced implements Rule {

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    Set<PdfDictionary> notes = new HashSet<>();
    Set<PdfDictionary> referenced = new HashSet<>();

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s != null && "Note".equals(s.getValue())) notes.add(se);
      collectRefTargets(se, referenced);
    });

    if (notes.isEmpty()) return out;

    for (PdfDictionary note : notes) {
      int page = StructUtils.pageNumOf(pdf, note);
      if (referenced.contains(note)) {
        out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_NOTE_REFERENCED, page, null));
      } else {
        out.add(new FindingDTO(Severity.WARNING, PDFUACheckpoint.Q_NOTE_REFERENCED, page, null,
            "\"Note\" element is not referenced by a \"Link\" element"));
      }
    }
    return out;
  }

  private static void collectRefTargets(PdfDictionary se, Set<PdfDictionary> out) {
    PdfObject ref = se.get(PdfName.Ref);
    if (ref == null) return;
    if (ref.isArray()) {
      PdfArray arr = (PdfArray) ref;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary()) out.add((PdfDictionary) item);
      }
    } else if (ref.isDictionary()) {
      out.add((PdfDictionary) ref);
    }
  }

}