package com.netralabs.metadata;

import com.itextpdf.kernel.xmp.XMPMeta;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.Rule;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class XMPXIdentifier implements Rule {

  @Override
  public EnumSet<Phase> phases() {
    return EnumSet.of(Phase.DOCUMENT);
  }

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    XMPMeta xmp = XMPMetaHelper.tryGetXmpMeta(ctx.pdf());
    out.add(new FindingDTO(xmp != null ? PASSED : ERROR, PDFUACheckpoint.XMP_METADATA, 0, null));
    return out;
  }


}
