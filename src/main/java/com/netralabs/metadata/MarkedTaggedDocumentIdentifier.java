package com.netralabs.metadata;

import com.itextpdf.kernel.pdf.PdfBoolean;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.Rule;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.MARK_TAGGED_DOCUMENT;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class MarkedTaggedDocumentIdentifier implements Rule {

  @Override
  public EnumSet<Phase> phases() {
    return EnumSet.of(Phase.DOCUMENT);
  }


  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    PdfDictionary cat = pdf.getCatalog().getPdfObject();
    PdfDictionary markInfo = cat.getAsDictionary(PdfName.MarkInfo);
    boolean marked = markInfo != null && (markInfo.get(PdfName.Marked) instanceof PdfBoolean b) && b.getValue();
    boolean hasStruct = cat.containsKey(PdfName.StructTreeRoot);
    out.add(new FindingDTO((marked && hasStruct) ? PASSED : ERROR, MARK_TAGGED_DOCUMENT, 0, null));
    return out;

  }
}
