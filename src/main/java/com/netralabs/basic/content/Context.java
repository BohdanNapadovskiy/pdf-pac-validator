package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.netralabs.vera.VeraValidationResults;

public class Context {

  private final PdfDocument pdf;
  private final PdfPage page;
  private final TagTreePointer ttp;
  private final int pageNum;
  private final VeraValidationResults veraResults;


  public Context(PdfDocument pdf, PdfPage page, TagTreePointer ttp, int pageNum) {
    this(pdf, page, ttp, pageNum, VeraValidationResults.empty());
  }

  public Context(PdfDocument pdf, PdfPage page, TagTreePointer ttp, int pageNum, VeraValidationResults veraResults) {
    this.pdf = pdf; this.page = page; this.ttp = ttp; this.pageNum = pageNum;
    this.veraResults = veraResults != null ? veraResults : VeraValidationResults.empty();
  }

  public PdfDocument pdf() { return pdf; }
  public PdfPage page() { return page; }
  public TagTreePointer ttp() { return ttp; }
  public int pageNum() { return pageNum; }
  public VeraValidationResults veraResults() { return veraResults; }

}
