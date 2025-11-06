package com.netralabs.metadata;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.domain.Phase;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.DYNAMIC_XFA_FORM;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class DynamicXfaFormIdentifier implements Rule {

  @Override
  public EnumSet<Phase> phases() {
    return EnumSet.of(Phase.DOCUMENT);
  }


  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    boolean hasXfa = hasXfa(pdf);
    out.add(new FindingDTO(hasXfa ? ERROR : PASSED, DYNAMIC_XFA_FORM, 0, null));
    return out;
  }

  private static boolean hasXfa(PdfDocument pdf) {
    PdfAcroForm form = PdfAcroForm.getAcroForm(pdf, false);
    PdfDictionary af = (form != null) ? form.getPdfObject() : null;
    return af != null && af.get(PdfName.XFA) != null;
  }

}
