package com.netralabs.quality.rules;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfPage;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PAC "TOCI elements are correctly linked to headings" — for each {@code TOCI}
 * struct element, find its descendant Link struct element(s), resolve the Link
 * annotation's {@code /Dest} (or GoTo action) to a target page, and check for
 * a heading struct element ({@code H}/{@code H1..H6}) on that page.
 * <p>
 * PASSED per TOCI whose Link resolves to a page containing a heading; ERROR
 * per TOCI where no heading target can be found (missing Link, unresolvable
 * dest, or page without headings). Emits nothing on docs with no TOCI, so the
 * leaf resolves to NA (matches PAC dash).
 */
public class ValidateTociLinkedToHeadings implements Rule {

  private static final Set<String> HEADING_ROLES =
      Set.of("H", "H1", "H2", "H3", "H4", "H5", "H6");

  @Override
  public List<FindingDTO> run(Context ctx) {
    List<FindingDTO> out = new ArrayList<>();
    PdfDocument pdf = ctx.pdf();
    if (!StructUtils.isTaggedPdf(pdf)) return out;

    Set<Integer> headingPages = collectHeadingPages(pdf);

    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s == null || !"TOCI".equals(s.getValue())) return;
      int tociPage = StructUtils.pageNumOf(pdf, se);

      List<PdfDictionary> links = new ArrayList<>();
      collectDescendantsWithRole(se, "Link", links);
      if (links.isEmpty()) {
        out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_TOCI_LINKED_TO_HEADINGS, tociPage, null,
            "TOCI element has no Link descendant to resolve"));
        return;
      }

      boolean anyLinksToHeading = false;
      for (PdfDictionary link : links) {
        PdfDictionary annot = firstLinkAnnot(link);
        if (annot == null) continue;
        Integer target = resolveDestPage(pdf, annot);
        if (target != null && headingPages.contains(target)) {
          anyLinksToHeading = true;
          break;
        }
      }
      if (anyLinksToHeading) {
        out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.Q_TOCI_LINKED_TO_HEADINGS, tociPage, null));
      } else {
        out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.Q_TOCI_LINKED_TO_HEADINGS, tociPage, null,
            "TOCI element is not linked to a heading"));
      }
    });
    return out;
  }

  private static Set<Integer> collectHeadingPages(PdfDocument pdf) {
    Set<Integer> pages = new HashSet<>();
    StructUtils.walkStructure(pdf, (parent, se) -> {
      PdfName s = se.getAsName(PdfName.S);
      if (s != null && HEADING_ROLES.contains(s.getValue())) {
        int p = StructUtils.pageNumOf(pdf, se);
        if (p > 0) pages.add(p);
      }
    });
    return pages;
  }

  private static void collectDescendantsWithRole(PdfDictionary node, String role, List<PdfDictionary> out) {
    PdfObject k = node.get(PdfName.K);
    if (k == null) return;
    if (k.isDictionary()) considerKid((PdfDictionary) k, role, out);
    else if (k.isArray()) {
      PdfArray arr = (PdfArray) k;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary()) considerKid((PdfDictionary) item, role, out);
      }
    }
  }

  private static void considerKid(PdfDictionary kid, String role, List<PdfDictionary> out) {
    if (!StructUtils.isStructElem(kid)) return;
    PdfName s = kid.getAsName(PdfName.S);
    if (s != null && role.equals(s.getValue())) out.add(kid);
    collectDescendantsWithRole(kid, role, out);
  }

  /** Find the first Link-annotation dict referenced via /K → OBJR under a Link struct element. */
  private static PdfDictionary firstLinkAnnot(PdfDictionary linkStruct) {
    PdfObject k = linkStruct.get(PdfName.K);
    if (k == null) return null;
    if (k.isDictionary()) return extractAnnot((PdfDictionary) k);
    if (k.isArray()) {
      PdfArray arr = (PdfArray) k;
      for (int i = 0; i < arr.size(); i++) {
        PdfObject item = arr.get(i);
        if (item != null && item.isDictionary()) {
          PdfDictionary a = extractAnnot((PdfDictionary) item);
          if (a != null) return a;
        }
      }
    }
    return null;
  }

  private static PdfDictionary extractAnnot(PdfDictionary d) {
    if (PdfName.OBJR.equals(d.getAsName(PdfName.Type))) {
      PdfObject obj = d.get(PdfName.Obj);
      if (obj != null && obj.isDictionary()) return (PdfDictionary) obj;
    }
    return null;
  }

  /**
   * Best-effort resolution of a Link annotation's /Dest (or /A GoTo) to a
   * 1-based page number. Handles named destinations via catalog /Names /Dests
   * and explicit destination arrays whose first entry is a page ref.
   */
  private static Integer resolveDestPage(PdfDocument pdf, PdfDictionary annot) {
    PdfObject dest = annot.get(PdfName.Dest);
    if (dest == null) {
      PdfObject a = annot.get(PdfName.A);
      if (a instanceof PdfDictionary action && PdfName.GoTo.equals(action.getAsName(PdfName.S))) {
        dest = action.get(PdfName.D);
      }
    }
    if (dest == null) return null;

    if (dest.isString()) {
      dest = resolveNamedDest(pdf, ((PdfString) dest).toUnicodeString());
    } else if (dest.isName()) {
      dest = resolveNamedDest(pdf, ((PdfName) dest).getValue());
    }
    if (dest == null) return null;

    if (dest.isArray()) {
      PdfArray arr = (PdfArray) dest;
      if (arr.size() > 0) {
        PdfObject pageObj = arr.get(0);
        if (pageObj != null && pageObj.isDictionary()) {
          try {
            PdfPage page = pdf.getPage((PdfDictionary) pageObj);
            if (page != null) return pdf.getPageNumber(page);
          } catch (Exception ignore) {}
        }
      }
    } else if (dest.isDictionary()) {
      PdfObject d = ((PdfDictionary) dest).get(PdfName.D);
      if (d != null) return resolveDestArrayFirstPage(pdf, d);
    }
    return null;
  }

  private static Integer resolveDestArrayFirstPage(PdfDocument pdf, PdfObject d) {
    if (!d.isArray()) return null;
    PdfArray arr = (PdfArray) d;
    if (arr.size() == 0) return null;
    PdfObject pageObj = arr.get(0);
    if (pageObj != null && pageObj.isDictionary()) {
      try {
        PdfPage page = pdf.getPage((PdfDictionary) pageObj);
        if (page != null) return pdf.getPageNumber(page);
      } catch (Exception ignore) {}
    }
    return null;
  }

  private static PdfObject resolveNamedDest(PdfDocument pdf, String name) {
    if (name == null) return null;
    try {
      PdfDictionary catalog = pdf.getCatalog().getPdfObject();
      PdfDictionary names = catalog.getAsDictionary(PdfName.Names);
      if (names != null) {
        PdfDictionary dests = names.getAsDictionary(PdfName.Dests);
        if (dests != null) {
          PdfObject resolved = findInNameTree(dests, name);
          if (resolved != null) return resolved;
        }
      }
      PdfDictionary destsFlat = catalog.getAsDictionary(PdfName.Dests);
      if (destsFlat != null) {
        return destsFlat.get(new PdfName(name));
      }
    } catch (Exception ignore) {}
    return null;
  }

  private static PdfObject findInNameTree(PdfDictionary node, String name) {
    PdfArray names = node.getAsArray(PdfName.Names);
    if (names != null) {
      for (int i = 0; i + 1 < names.size(); i += 2) {
        PdfObject k = names.get(i);
        if (k instanceof PdfString ps && name.equals(ps.toUnicodeString())) {
          return names.get(i + 1);
        }
      }
    }
    PdfArray kids = node.getAsArray(PdfName.Kids);
    if (kids != null) {
      for (int i = 0; i < kids.size(); i++) {
        PdfObject kid = kids.get(i);
        if (kid != null && kid.isDictionary()) {
          PdfObject found = findInNameTree((PdfDictionary) kid, name);
          if (found != null) return found;
        }
      }
    }
    return null;
  }
}