package com.netralabs;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Phase;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Slf4j
public class Runner {


  private record Entry(PDFUACheckpoint cp, Rule rule, EnumSet<Phase> phases) {}

  public List<FindingDTO> runAll(PdfDocument pdf) {
    List<FindingDTO> out = new ArrayList<>();
    int pages = pdf.getNumberOfPages();

    // Materialize rule instances once
    List<Entry> rules = new ArrayList<>();
    for (PDFUACheckpoint cp : PDFUACheckpoint.values()) {
      if (cp.getFactory() != null) {
        rules.add(new Entry(cp, cp.getFactory().get(), cp.getPhases()));
      }
    }

    // DOCUMENT phase
    for (Entry r : rules) if (r.phases().contains(Phase.DOCUMENT)) {
      out.addAll(r.rule().run(new Context(pdf, null, null, 0)));
    }

    // PAGE phase
    for (int i = 1; i <= pages; i++) {
      var ctx = new Context(pdf, pdf.getPage(i), null, i);
      for (Entry r : rules) if (r.phases().contains(Phase.PAGE)) {
        out.addAll(r.rule().run(ctx));
      }
    }

    // CONTENT phase
    for (int i = 1; i <= pages; i++) {
      var ctx = new Context(pdf, pdf.getPage(i), null, i);
      for (Entry r : rules) if (r.phases().contains(Phase.CONTENT)) {
        out.addAll(r.rule().run(ctx));
      }
    }

    // STRUCT phase
    TagTreePointer ttp = new TagTreePointer(pdf);
    int rootKids = ttp.getKidsRoles().size();
    for (int i = 0; i < rootKids; i++) {
      ttp.moveToKid(i);
      dfsStruct(pdf, ttp, rules, out);
      ttp.moveToParent();
    }

    return out;
  }

  private int safeStructPageNumber(PdfDocument pdf, TagTreePointer ttp) {
    // Try TagTreePointer#getPageNumber() if present (iText 7.2.x+)
    try {
      var m = ttp.getClass().getMethod("getPageNumber");
      Object res = m.invoke(ttp);
      if (res instanceof Integer i && i > 0) return i;
    } catch (ReflectiveOperationException ignore) {}

    // Try TagTreePointer#getPage() if present, then map to page number
    try {
      var m = ttp.getClass().getMethod("getPage");
      Object res = m.invoke(ttp);
      if (res instanceof com.itextpdf.kernel.pdf.PdfPage p) {
        return pdf.getPageNumber(p);
      }
    } catch (ReflectiveOperationException ignore) {}
    return 0;
  }

  private void dfsStruct(PdfDocument pdf, TagTreePointer ttp,
      List<Entry> rules, List<FindingDTO> out) {
    int pageNum = safeStructPageNumber(pdf, ttp);
    var ctx = new Context(pdf, null, ttp, pageNum);

    // Run STRUCT-phase rules at the current element
    for (Entry r : rules)
      if (r.phases().contains(Phase.STRUCT)) {
        out.addAll(r.rule().run(ctx));
      }

    // Get the current struct element and iterate over its *element* kids only
    PdfStructElem elem = pdf.getTagStructureContext().getPointerStructElem(ttp);
    if (elem == null)
      return;

    for (IStructureNode kid : elem.getKids()) {
      if (!(kid instanceof PdfStructElem kidElem)) {
        // This is an MCR (MCID/ObjRef) or flushed node — skip recursion
        continue;
      }
      TagTreePointer childPtr = pdf.getTagStructureContext().createPointerForStructElem(kidElem);
      dfsStruct(pdf, childPtr, rules, out);
    }
  }
}
