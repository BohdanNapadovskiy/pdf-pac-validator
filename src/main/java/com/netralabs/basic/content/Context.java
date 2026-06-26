package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.netralabs.ContentRuleListener;
import com.netralabs.MultiCanvasListener;
import com.netralabs.report.FindingDTO;
import com.netralabs.vera.VeraValidationResults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

  // Convenience: run a content pass on THIS page with one or more listeners
  public List<FindingDTO> runContent(ContentRuleListener... listeners) {
    if (page == null) throw new IllegalStateException("CONTENT pass requires a page");
    List<IEventListener> delegates = new ArrayList<>();
    for (ContentRuleListener l : listeners) {
      delegates.add(l);
    }
    PdfCanvasProcessor proc = new PdfCanvasProcessor(new MultiCanvasListener(delegates));
    proc.processPageContent(page);
    if (listeners.length == 0) return Collections.emptyList();
    if (listeners.length == 1) return listeners[0].drain();
    List<FindingDTO> all = new ArrayList<>();
    for (ContentRuleListener l : listeners) all.addAll(l.drain());
    return all;
  }

}
