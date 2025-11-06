package com.netralabs;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.netralabs.report.FindingDTO;

import java.util.List;

@FunctionalInterface
public interface DocumentRuleCheck {

  List<FindingDTO> run(PdfDocument pdf);

}
