package com.netralabs;

import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.report.FindingDTO;

import java.util.EnumSet;
import java.util.List;

public interface Rule {

  EnumSet<Phase> phases();

  default boolean supportsRole(PdfName role) { return true; }

  List<FindingDTO> run(Context ctx);



}
