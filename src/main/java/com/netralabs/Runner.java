package com.netralabs;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Phase;
import com.netralabs.metadata.XMPMetaHelper;
import com.netralabs.report.FindingDTO;
import com.netralabs.vera.VeraPdfAdapterRule;
import com.netralabs.vera.VeraRuleMapping;
import com.netralabs.vera.VeraRunner;
import com.netralabs.vera.VeraValidationResults;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Slf4j
public class Runner {


  private record Entry(PDFUACheckpoint cp, Rule rule, EnumSet<Phase> phases) {}

  public List<FindingDTO> runAll(PdfDocument pdf) {
    return runAll(pdf, null);
  }

  public List<FindingDTO> runAll(PdfDocument pdf, String pdfPath) {
    List<FindingDTO> out = new ArrayList<>();
    int pages = pdf.getNumberOfPages();

    boolean isUa2 = declaresPdfUa2(pdf);
    VeraValidationResults vera = (pdfPath != null)
        ? VeraRunner.validate(pdfPath, /*applyUa1CoreRules*/ !isUa2, /*applyUa2CoreRules*/ isUa2)
        : VeraValidationResults.empty();

    // Materialize rule instances. Native and vera adapter can coexist for a checkpoint —
    // e.g. StructElementByRoleRule emits one PASSED per element (count) while the vera
    // adapter still drains vera-reported failures into the same checkpoint.
    List<Entry> rules = new ArrayList<>();
    for (PDFUACheckpoint cp : PDFUACheckpoint.values()) {
      boolean hasRunnableNative = cp.getFactory() != null && !cp.getPhases().isEmpty();
      if (hasRunnableNative) {
        rules.add(new Entry(cp, cp.getFactory().get(), cp.getPhases()));
      }
      if (VeraRuleMapping.covers(cp)) {
        rules.add(new Entry(cp, new VeraPdfAdapterRule(cp), EnumSet.of(Phase.DOCUMENT)));
      }
    }

    // DOCUMENT phase
    for (Entry r : rules) if (r.phases().contains(Phase.DOCUMENT)) {
      safeRun(r, new Context(pdf, null, null, 0, vera), out);
    }

    // PAGE phase
    for (int i = 1; i <= pages; i++) {
      var ctx = new Context(pdf, pdf.getPage(i), null, i, vera);
      for (Entry r : rules) if (r.phases().contains(Phase.PAGE)) {
        safeRun(r, ctx, out);
      }
    }

    // CONTENT phase
    for (int i = 1; i <= pages; i++) {
      var ctx = new Context(pdf, pdf.getPage(i), null, i, vera);
      for (Entry r : rules) if (r.phases().contains(Phase.CONTENT)) {
        safeRun(r, ctx, out);
      }
    }

    // STRUCT phase — skipped for untagged PDFs so we behave like PAC (NA on struct-tree rows)
    // instead of crashing with "Must be a tagged document". Also guards against iText quirks
    // like "no associate PdfWriter" thrown by RootTagNormalizer on partial structure trees.
    if (pdf.isTagged()) {
      try {
        TagTreePointer ttp = new TagTreePointer(pdf);
        int rootKids = ttp.getKidsRoles().size();
        for (int i = 0; i < rootKids; i++) {
          ttp.moveToKid(i);
          dfsStruct(pdf, ttp, rules, out, vera);
          ttp.moveToParent();
        }
      } catch (Exception e) {
        log.warn("Skipping STRUCT phase — structure tree not traversable: {}", e.getMessage());
      }
    } else {
      log.info("PDF is not tagged — skipping STRUCT-phase rules");
    }

    return out;
  }

  private void safeRun(Entry r, Context ctx, List<FindingDTO> out) {
    try {
      out.addAll(r.rule().run(ctx));
    } catch (Exception e) {
      log.warn("Rule {} crashed on checkpoint {}: {}",
          r.rule().getClass().getSimpleName(), r.cp(), e.getMessage());
    }
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

  private static boolean declaresPdfUa2(PdfDocument pdf) {
    XMPMeta xmp = XMPMetaHelper.tryGetXmpMeta(pdf);
    if (xmp == null) return false;
    try {
      XMPMetaFactory.getSchemaRegistry()
          .registerNamespace("http://www.aiim.org/pdfua/ns/id/", "pdfuaid");
      return "2".equals(xmp.getPropertyString("http://www.aiim.org/pdfua/ns/id/", "part"));
    } catch (Exception e) {
      return false;
    }
  }

  private void dfsStruct(PdfDocument pdf, TagTreePointer ttp,
      List<Entry> rules, List<FindingDTO> out, VeraValidationResults vera) {
    int pageNum = safeStructPageNumber(pdf, ttp);
    var ctx = new Context(pdf, null, ttp, pageNum, vera);

    // Run STRUCT-phase rules at the current element
    for (Entry r : rules)
      if (r.phases().contains(Phase.STRUCT)) {
        safeRun(r, ctx, out);
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
      try {
        TagTreePointer childPtr = pdf.getTagStructureContext().createPointerForStructElem(kidElem);
        dfsStruct(pdf, childPtr, rules, out, vera);
      } catch (Exception e) {
        // Malformed struct branch (e.g. missing parent) — skip but keep walking siblings.
        log.warn("Skipping malformed struct branch: {}", e.getMessage());
      }
    }
  }
}
