package com.netralabs.metadata;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.Rule;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.TAB_ORDER_PAGES;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class TabOrderByPageIdentifier implements Rule {

  @Override
  public EnumSet<Phase> phases() {
    return EnumSet.of(Phase.PAGE);
  }

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfPage page = ctx.page();
    PdfDictionary pd = page.getPdfObject();
    PdfArray annots = pd.getAsArray(PdfName.Annots);
    boolean hasAnnots = annots != null && !annots.isEmpty();
    if (!hasAnnots) return out;
    PdfName tabs = pd.getAsName(PdfName.Tabs);
    int pageNum = page.getDocument().getPageNumber(page);
    out.add(new FindingDTO(PdfName.S.equals(tabs) ? PASSED : ERROR, TAB_ORDER_PAGES, pageNum, null));
    return out;
  }

}
