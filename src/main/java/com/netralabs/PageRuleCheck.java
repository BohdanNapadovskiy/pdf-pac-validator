package com.netralabs;

import com.itextpdf.kernel.pdf.PdfPage;
import com.netralabs.report.FindingDTO;

import java.util.List;

@FunctionalInterface
public interface PageRuleCheck {

  List<FindingDTO> run(PdfPage pdf);


}
