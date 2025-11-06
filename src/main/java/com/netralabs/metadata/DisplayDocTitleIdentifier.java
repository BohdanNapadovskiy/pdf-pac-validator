package com.netralabs.metadata;

import com.itextpdf.kernel.pdf.PdfBoolean;
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

import static com.netralabs.domain.PDFUACheckpoint.DISPLAY_DOCUMENT_TITLE;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class DisplayDocTitleIdentifier implements Rule {

  @Override
  public EnumSet<Phase> phases() {
    return EnumSet.of(Phase.DOCUMENT);
  }

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    PdfDictionary cat = pdf.getCatalog().getPdfObject();
    PdfDictionary vp = cat.getAsDictionary(PdfName.ViewerPreferences);
    boolean display = (vp != null) && (vp.get(PdfName.DisplayDocTitle) instanceof PdfBoolean b) && b.getValue();
    out.add(new FindingDTO(display ? PASSED : ERROR, DISPLAY_DOCUMENT_TITLE, 0, null));
    return out;
  }

}
