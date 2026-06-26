package com.netralabs.metadata;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;
import com.netralabs.basic.content.Context;
import com.netralabs.Rule;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.PDF_UA_IDENTIFIER;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class PdfUAIdentifier implements Rule {

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    XMPMeta xmp = XMPMetaHelper.tryGetXmpMeta(pdf);
    if (xmp == null) {
      out.add(new FindingDTO(ERROR, PDF_UA_IDENTIFIER, 0, null));
      return out;
    }
    try {
      XMPMetaFactory.getSchemaRegistry()
          .registerNamespace("http://www.aiim.org/pdfua/ns/id/", "pdfuaid");
      String part = xmp.getPropertyString("http://www.aiim.org/pdfua/ns/id/", "part");
      out.add(new FindingDTO("1".equals(part) ? PASSED : ERROR, PDF_UA_IDENTIFIER, 0, null));
    } catch (Exception e) {
      out.add(new FindingDTO(ERROR, PDF_UA_IDENTIFIER, 0, null));
    }
    return out;
  }


}
