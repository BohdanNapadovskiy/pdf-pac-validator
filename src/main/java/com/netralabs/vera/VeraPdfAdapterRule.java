package com.netralabs.vera;

import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.report.FindingDTO;

import java.util.List;

public final class VeraPdfAdapterRule implements Rule {

  private final PDFUACheckpoint cp;

  public VeraPdfAdapterRule(PDFUACheckpoint cp) {
    this.cp = cp;
  }

  @Override
  public List<FindingDTO> run(Context ctx) {
    return ctx.veraResults().findingsFor(cp);
  }
}
