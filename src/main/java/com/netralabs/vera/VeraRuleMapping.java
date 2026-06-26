package com.netralabs.vera;

import com.netralabs.domain.PDFUACheckpoint;

import java.util.Map;
import java.util.Set;

public final class VeraRuleMapping {

  private static final String P = "ISO 14289-1:2014-";

  /**
   * veraPDF rule IDs that PAC treats as warnings (orange triangle) rather than errors.
   * These correspond to "should" clauses in ISO 14289-1 — soft recommendations whose
   * violation doesn't break PDF/UA compliance. Per docs/pac-diff-report.md §"veraPDF
   * severity wrong".
   */
  private static final Set<String> WARNING_RULES = Set.of(
      P + "7.2-6",   // TBody container
      P + "7.2-15",  // Table regularity
      P + "7.2-41",  // Table regularity
      P + "7.2-42",  // Table regularity
      P + "7.2-43"   // Table regularity
  );

  private static final Map<String, PDFUACheckpoint> MAP = Map.ofEntries(
      // Role mapping
      Map.entry(P + "7.1-5",   PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE),
      Map.entry(P + "7.1-6",   PDFUACheckpoint.CIRCULAR_ROLE_MAPPING),
      Map.entry(P + "7.1-7",   PDFUACheckpoint.ROLE_MAPPING_FOR_STANDARD_STRUCTURE),

      // Tables — structure tree leaves
      Map.entry(P + "7.2-3",   PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-4",   PDFUACheckpoint.TR_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-5",   PDFUACheckpoint.THEAD_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-6",   PDFUACheckpoint.TBODY_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-7",   PDFUACheckpoint.TFOOT_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-8",   PDFUACheckpoint.TH_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-9",   PDFUACheckpoint.TD_STRUCTURE_ELEMENTS),
      // 7.2-10 ("TR may contain only TH and TD") is handled natively by
      // StructElementByRoleRule, which pins the error on the offending CHILD (e.g. Span)
      // the way PAC does, instead of veraPDF's TR-level attribution.
      Map.entry(P + "7.2-11",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-12",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-13",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-14",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-15",  PDFUACheckpoint.TABLE_REGULARITY),
      Map.entry(P + "7.2-16",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-36",  PDFUACheckpoint.THEAD_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-37",  PDFUACheckpoint.TBODY_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-38",  PDFUACheckpoint.TFOOT_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-39",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-41",  PDFUACheckpoint.TABLE_REGULARITY),
      Map.entry(P + "7.2-42",  PDFUACheckpoint.TABLE_REGULARITY),
      Map.entry(P + "7.2-43",  PDFUACheckpoint.TABLE_REGULARITY),

      // Lists
      Map.entry(P + "7.2-17",  PDFUACheckpoint.LI_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-18",  PDFUACheckpoint.LBODY_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-19",  PDFUACheckpoint.L_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-20",  PDFUACheckpoint.LI_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-40",  PDFUACheckpoint.L_STRUCTURE_ELEMENTS),

      // TOC
      Map.entry(P + "7.2-26",  PDFUACheckpoint.TOCI_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-27",  PDFUACheckpoint.TOC_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-28",  PDFUACheckpoint.TOC_STRUCTURE_ELEMENTS),

      // Alternative descriptions
      Map.entry(P + "7.3-1",   PDFUACheckpoint.ALTERNATIVE_TEXT_FOR_FIGURE),
      Map.entry(P + "7.5-1",   PDFUACheckpoint.TABLE_HEADER_CELL_ASSIGNMENTS),
      Map.entry(P + "7.5-2",   PDFUACheckpoint.TABLE_HEADER_CELL_ASSIGNMENTS),
      Map.entry(P + "7.7-1",   PDFUACheckpoint.ALTERNATIVE_TEXT_FOR_FORMULA),

      // Annotations
      Map.entry(P + "7.18.1-1", PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT),
      Map.entry(P + "7.18.1-2", PDFUACheckpoint.ALTERNATIVE_DESCRIPTION_FOR_ANNOT),
      Map.entry(P + "7.18.1-3", PDFUACheckpoint.ALTERNATIVE_NAMES_FORM_FIELDS),
      Map.entry(P + "7.18.2-1", PDFUACheckpoint.TRAP_NET_ANNOTATIONS),
      Map.entry(P + "7.18.4-1", PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS),
      Map.entry(P + "7.18.4-2", PDFUACheckpoint.FORM_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.18.5-1", PDFUACheckpoint.NESTING_LINK_ANNOTATIONS),
      // 7.18.5-2 ("Links shall contain an alternate description") is intentionally NOT mapped:
      // veraPDF fires it alongside 7.18.1-2 for every Link annotation missing /Contents, so
      // keeping both double-counts the same defect. PAC reports it once, via the general rule.
      Map.entry(P + "7.18.8-1", PDFUACheckpoint.PRINTER_MARK_ANNOTATIONS),

      // XObjects
      Map.entry(P + "7.20-1",  PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT),
      Map.entry(P + "7.20-2",  PDFUACheckpoint.CONTENT_PRESENT)
  );

  private VeraRuleMapping() {}

  public static PDFUACheckpoint toCheckpoint(String veraRuleId) {
    return MAP.get(veraRuleId);
  }

  public static boolean covers(PDFUACheckpoint cp) {
    return MAP.containsValue(cp);
  }

  public static boolean isWarning(String veraRuleId) {
    return WARNING_RULES.contains(veraRuleId);
  }
}
