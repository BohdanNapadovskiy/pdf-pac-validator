package com.netralabs.metadata;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.netralabs.basic.content.Context;
import com.netralabs.Rule;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.SECURITY_SETTINGS;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class SecuritySettingIdentifier implements Rule {

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    PdfDictionary encrypt = pdf.getTrailer().getAsDictionary(PdfName.Encrypt);
    if (encrypt != null) {
      PdfNumber p = encrypt.getAsNumber(PdfName.P);
      int perms = (p != null) ? p.intValue() : 0;
      final int BIT10_ACCESS = 0x0200;
      boolean allowsAT = (perms & BIT10_ACCESS) != 0;
      out.add(new FindingDTO(allowsAT ? PASSED : ERROR, SECURITY_SETTINGS, 0, null));
    }
    return out;
  }
}
