package com.netralabs.metadata;

import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.Rule;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.TITLE_XMP_METADATA;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

public class TitleInXMPIdentifier implements Rule {

  @Override
  public EnumSet<Phase> phases() {
    return EnumSet.of(Phase.DOCUMENT);
  }

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    XMPMeta xmp = XMPMetaHelper.tryGetXmpMeta(ctx.pdf());
    if (xmp == null) {
      out.add(new FindingDTO(ERROR, TITLE_XMP_METADATA, 0, null));
      return out;
    }
    try {
      int n = xmp.countArrayItems("http://purl.org/dc/elements/1.1/", "title");
      boolean ok = false;
      for (int i = 1; i <= n; i++) {
        String v = xmp.getArrayItem("http://purl.org/dc/elements/1.1/", "title", i).getValue();
        if (v != null && !v.trim().isEmpty()) { ok = true; break; }
      }
      out.add(new FindingDTO(ok ? PASSED : ERROR, TITLE_XMP_METADATA, 0, null));
    } catch (XMPException e) {
      out.add(new FindingDTO(ERROR, TITLE_XMP_METADATA, 0, null));
    }
    return out;

  }

}
