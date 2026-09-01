package com.netralabs.vera;

import com.netralabs.domain.PDFUACheckpoint;
import lombok.extern.slf4j.Slf4j;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.validation.profiles.Profiles;
import org.verapdf.pdfa.validation.profiles.Rule;
import org.verapdf.pdfa.validation.profiles.ValidationProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
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
      // Lists (LI structural expectations) — PAC surfaces these as warnings, not errors.
      // On Metro, 15 vera failures on LITag showed as ERROR while PAC showed WARNING;
      // adding both clauses so LI-related vera failures roll up to W in the report.
      P + "7.2-17",
      P + "7.2-20",
      // TOC / TOCI (7.2-26): same treatment — 15 TOCI vera failures on Metro were
      // ERROR-classified by us but PAC surfaces them as WARNING.
      P + "7.2-26"
      // 7.2-15 / 7.2-41 / 7.2-42 / 7.2-43 handled natively (see UA1_MAP note).
  );

  /**
   * Checkpoints where PAC displays only ERROR counts — no PASSED tally — even though
   * veraPDF's pass-2 collection would emit one PASSED per qualifying object. Suppress
   * those passes so our totals match PAC's error-only rows.
   *
   * <p>The three annotation-nesting checkpoints are error-only from vera's side because
   * their PASSED counts are supplied natively by {@code ValidateAnnotationNesting}, which
   * walks page {@code /Annots} once and routes by subtype. Letting vera also emit passes
   * would double-count on top of the native tally.
   */
  private static final Set<PDFUACheckpoint> ERROR_ONLY_CHECKPOINTS = Set.of(
      PDFUACheckpoint.ALTERNATIVE_DESCRIPTION_FOR_ANNOT,
      PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS,
      PDFUACheckpoint.NESTING_LINK_ANNOTATIONS,
      PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT,
      // ValidateReferencedExternalObjects emits per-page PASSED for resolved Do
      // references; vera 7.20-1's pass-2 count would stack on top and inflate
      // the row (Complex: 13 native + 8 vera = 21 vs PAC's 13).
      PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT,
      // Font-metadata checkpoints where PAC leaves the row as N/A on documents
      // that pass trivially. On UA-2 documents the OBJECT_TO_CHECKPOINT catch-all
      // (PDType0Font/PDCMap/PDTrueTypeFont/PDCIDFont) routes many vera pass-2
      // assertions to these rows, inflating counts that PAC does not surface.
      // Native rules already report real errors on the same checkpoints, and
      // ValidateCidToGidMapForType2 emits per-font PASSED that matches PAC's tally.
      PDFUACheckpoint.REGISTRY_ENTRIES,
      PDFUACheckpoint.PREDEFINED_CMAPS,
      PDFUACheckpoint.GLYPH_NAMES,
      PDFUACheckpoint.CID_GID_MAPPING,
      // ValidateLangAttributeCorrectness emits exactly one PASSED per document
      // (matching PAC's per-document granularity). On UA-2 docs the PDDocument
      // and CosLang catch-alls contribute additional pass-2 assertions that
      // inflate the row (OP_AoD: 1 native + 3 vera = 4 vs PAC 1).
      PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR,
      // ValidateTaggedCoverage emits one PASSED per paint event
      // (RENDER_TEXT/RENDER_IMAGE/non-NO_OP RENDER_PATH), matching PAC's
      // per-paint tally on this row. On UA-2 docs the SESimpleContentItem and
      // SEGraphicContentItem catch-alls fire additional UA-2 semantic-check
      // pass-2 assertions that PAC does not surface (OP_AoD: 11523 native +
      // 845 vera = 12368P vs PAC 11523).
      PDFUACheckpoint.TAGGED_CONTENT_ARTIFACTS
  );

  public static boolean isErrorOnly(PDFUACheckpoint cp) {
    return ERROR_ONLY_CHECKPOINTS.contains(cp);
  }

  private static final Map<String, PDFUACheckpoint> UA1_MAP = Map.ofEntries(
      // Role mapping
      // 7.1-5 ("non-standard type is neither mapped nor a valid PDF/UA type") is
      // handled natively by RoleMapValidatorRule, which emits one ERROR per offending
      // struct element (matching PAC). Vera's per-doc aggregate would double-count.
      // 7.1-7 ("standard tags shall not be remapped") is intentionally NOT mapped —
      // vera over-fires per struct element on Metro-Planners-Handbook (17E vs PAC 1E),
      // apparently attributing the error to every SE affected by a standard-type
      // remapping rather than to the offending RoleMap entry. Native
      // RoleMapValidatorRule emits per-RoleMap-entry findings with PAC-compatible
      // granularity for this checkpoint.
      Map.entry(P + "7.1-6",   PDFUACheckpoint.CIRCULAR_ROLE_MAPPING),

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
      // 7.2-15 and 7.2-41/42/43 (Table regularity) are handled natively by
      // ValidateTableRegularity — one PASSED per regular Table, one WARNING per
      // irregular TR. Vera's per-table WARNING would double-count.
      Map.entry(P + "7.2-16",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-36",  PDFUACheckpoint.THEAD_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-37",  PDFUACheckpoint.TBODY_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-38",  PDFUACheckpoint.TFOOT_STRUCTURE_ELEMENTS),
      Map.entry(P + "7.2-39",  PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      // 7.2-41/42/43 handled natively — see note above.

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
      // 7.18.1-1 / 7.18.4-1 / 7.18.5-1 (annotation-nesting clauses) are handled
      // natively by ValidateAnnotationNesting, which walks page /Annots and
      // checks StructParent → ParentTree → ancestor-role for the required
      // wrapper (Form / Link / Annot). Vera's mapping over-counts on some
      // documents (e.g. 146 vs PAC's 90 widget errors on a scanned form).
      // 7.18.1-2 (annotation Contents alt text) is handled natively by
      // ValidateAnnotationAltText, which emits WARNING for whitespace-only
      // /Contents — matching PAC. Vera fires ERROR on the same annotations,
      // which double-counted the warning as an error on Metro (E: 2077 vs PAC 2).
      // 7.18.1-3 ("form fields shall have TU or widget alt descriptions") is handled
      // natively by ValidateFormFieldAltNames — vera's pass-2 assertion cap silently
      // dropped tail-end field passes on multi-field forms (15/31 on Filled_Graduate).
      Map.entry(P + "7.18.2-1", PDFUACheckpoint.TRAP_NET_ANNOTATIONS),
      Map.entry(P + "7.18.4-2", PDFUACheckpoint.FORM_STRUCTURE_ELEMENTS),
      // 7.18.5-2 ("Links shall contain an alternate description") is intentionally NOT mapped:
      // veraPDF fires it alongside 7.18.1-2 for every Link annotation missing /Contents, so
      // keeping both double-counts the same defect. PAC reports it once, via the general rule.
      Map.entry(P + "7.18.8-1", PDFUACheckpoint.PRINTER_MARK_ANNOTATIONS),

      // XObjects
      Map.entry(P + "7.20-1",  PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT)
      // 7.20-2 ("Content shall be present in admissible locations") intentionally
      // unmapped — PAC treats this checkpoint as NA universally; vera's PASSED count
      // would inflate the row.
  );

  /**
   * PDFUA_2 / ISO 32005 mapping.
   *
   * <p>Instead of hardcoding 1723 rule IDs, we build the map at class-init by iterating
   * the profile and routing each rule by its <em>object type</em> (e.g. every rule with
   * {@code object="SEFigure"} → {@link PDFUACheckpoint#FIGURE_STRUCTURE_ELEMENTS}). One
   * clause-specific override handles {@code 8.2.5.28.2} which is Figure BBox geometric
   * containment and belongs under {@link PDFUACheckpoint#BOUNDED_BOXES}.
   *
   * <p>Runtime rule IDs come in two prefixes:
   * <ul>
   *   <li>{@code "ISO 14289-2:2024-<clause>-<test>"} — the PDF/UA-2 core rules</li>
   *   <li>{@code "ISO 32005:2023-<clause>-<test>"}   — nesting rules ("Table 5. X-Y")</li>
   * </ul>
   * Both are handled uniformly since we key off the runtime {@code RuleId} string.
   */
  private static final Map<String, PDFUACheckpoint> OBJECT_TO_CHECKPOINT = Map.<String, PDFUACheckpoint>ofEntries(
      Map.entry("MainXMPPackage",       PDFUACheckpoint.PDF_UA_IDENTIFIER),
      Map.entry("PDFUAIdentification",  PDFUACheckpoint.PDF_UA_IDENTIFIER),
      Map.entry("PDDocument",           PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR),
      Map.entry("PDAcroForm",           PDFUACheckpoint.ALTERNATIVE_NAMES_FORM_FIELDS),
      Map.entry("PDStructTreeRoot",     PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX),
      Map.entry("PDStructElem",         PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX),
      // "CosDocument" intentionally NOT mapped — DisplayDocTitleIdentifier
      // (native) emits exactly one finding checking /DisplayDocTitle. Vera's
      // UA-2 CosDocument-level tests are unrelated (file format / structural
      // sanity) and over-emitted 2 extra passes on OP_AoD (PAC 1P, our 3P).
      // Structure elements
      Map.entry("SEDocument",           PDFUACheckpoint.DOCUMENT_STRUCTURE_ELEMENT),
      Map.entry("SEDocumentFragment",   PDFUACheckpoint.DOCUMENT_STRUCTURE_ELEMENT),
      Map.entry("SEPart",               PDFUACheckpoint.PART_STRUCTURE_ELEMENT),
      Map.entry("SEArt",                PDFUACheckpoint.ART_STRUCTURE_ELEMENT),
      Map.entry("SEAside",              PDFUACheckpoint.ART_STRUCTURE_ELEMENT),
      Map.entry("SESect",               PDFUACheckpoint.SECT_STRUCTURE_ELEMENT),
      Map.entry("SEDiv",                PDFUACheckpoint.DIV_STRUCTURE_ELEMENT),
      Map.entry("SEBlockQuote",         PDFUACheckpoint.BLOCKQUOTE_STRUCTURE_ELEMENT),
      Map.entry("SECaption",            PDFUACheckpoint.CAPTION_STRUCTURE_ELEMENTS),
      Map.entry("SEIndex",              PDFUACheckpoint.INDEX_STRUCTURE_ELEMENTS),
      Map.entry("SEPrivate",            PDFUACheckpoint.PRIVATE_STRUCTURE_ELEMENTS),
      Map.entry("SEBibEntry",           PDFUACheckpoint.BIBENTRY_STRUCTURE_ELEMENTS),
      Map.entry("SECode",               PDFUACheckpoint.CODE_STRUCTURE_ELEMENTS),
      Map.entry("SENote",               PDFUACheckpoint.NOTE_STRUCTURE_ELEMENTS),
      Map.entry("SEFENote",             PDFUACheckpoint.NOTE_STRUCTURE_ELEMENTS),
      Map.entry("SEReference",          PDFUACheckpoint.REFERENCE_STRUCTURE_ELEMENTS),
      Map.entry("SETOC",                PDFUACheckpoint.TOC_STRUCTURE_ELEMENTS),
      Map.entry("SETOCI",               PDFUACheckpoint.TOCI_STRUCTURE_ELEMENTS),
      Map.entry("SEP",                  PDFUACheckpoint.P_STRUCTURE_ELEMENTS),
      Map.entry("SEH",                  PDFUACheckpoint.H_STRUCTURE_ELEMENTS),
      Map.entry("SEHn",                 PDFUACheckpoint.H_STRUCTURE_ELEMENTS),
      Map.entry("SETitle",              PDFUACheckpoint.H_STRUCTURE_ELEMENTS),
      Map.entry("SEFigure",             PDFUACheckpoint.FIGURE_STRUCTURE_ELEMENTS),
      Map.entry("SEFormula",            PDFUACheckpoint.FORMULA_STRUCTURE_ELEMENTS),
      Map.entry("SEForm",               PDFUACheckpoint.FORM_STRUCTURE_ELEMENTS),
      Map.entry("SESpan",               PDFUACheckpoint.SPAN_STRUCTURE_ELEMENTS),
      Map.entry("SEQuote",              PDFUACheckpoint.QUOTE_STRUCTURE_ELEMENTS),
      Map.entry("SELink",               PDFUACheckpoint.LINK_STRUCTURE_ELEMENTS),
      Map.entry("SEAnnot",              PDFUACheckpoint.ANNOT_STRUCTURE_ELEMENTS),
      Map.entry("SEL",                  PDFUACheckpoint.L_STRUCTURE_ELEMENTS),
      Map.entry("SELI",                 PDFUACheckpoint.LI_STRUCTURE_ELEMENTS),
      Map.entry("SELBody",              PDFUACheckpoint.LBODY_STRUCTURE_ELEMENTS),
      Map.entry("SELbl",                PDFUACheckpoint.Lbl_STRUCTURE_ELEMENTS),
      Map.entry("SETable",              PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry("SETR",                 PDFUACheckpoint.TR_STRUCTURE_ELEMENTS),
      Map.entry("SETH",                 PDFUACheckpoint.TH_STRUCTURE_ELEMENTS),
      Map.entry("SETD",                 PDFUACheckpoint.TD_STRUCTURE_ELEMENTS),
      Map.entry("SETHead",              PDFUACheckpoint.THEAD_STRUCTURE_ELEMENTS),
      Map.entry("SETBody",              PDFUACheckpoint.TBODY_STRUCTURE_ELEMENTS),
      Map.entry("SETFoot",              PDFUACheckpoint.TFOOT_STRUCTURE_ELEMENTS),
      Map.entry("SERuby",               PDFUACheckpoint.RUBY_STRUCTURE_ELEMENTS),
      Map.entry("SERB",                 PDFUACheckpoint.RB_STRUCTURE_ELEMENTS),
      Map.entry("SERT",                 PDFUACheckpoint.RT_STRUCTURE_ELEMENTS),
      Map.entry("SERP",                 PDFUACheckpoint.RP_STRUCTURE_ELEMENTS),
      Map.entry("SEWarichu",            PDFUACheckpoint.WARICHU_STRUCTURE_ELEMENTS),
      Map.entry("SEWP",                 PDFUACheckpoint.WP_STRUCTURE_ELEMENTS),
      Map.entry("SEWT",                 PDFUACheckpoint.WT_STRUCTURE_ELEMENTS),
      // Annotations
      // PDLinkAnnot / PDWidgetAnnot / PDAnnot (and other markup annotation object
      // types) intentionally NOT mapped to NESTING_* checkpoints — those are
      // handled natively by ValidateAnnotationNesting, which walks page /Annots
      // and does StructParent → ParentTree → ancestor-role lookup. Vera's UA-2
      // object-routed pass-2 assertions would double-count on top of the native
      // per-annotation emission.
      Map.entry("PDTrapNetAnnot",       PDFUACheckpoint.TRAP_NET_ANNOTATIONS),
      Map.entry("PDPrinterMarkAnnot",   PDFUACheckpoint.PRINTER_MARK_ANNOTATIONS),
      Map.entry("PD3DAnnot",            PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT),
      Map.entry("PDTextField",          PDFUACheckpoint.ALTERNATIVE_NAMES_FORM_FIELDS),
      // Content / real-content
      Map.entry("SEArtifact",           PDFUACheckpoint.ARTIFACT_INSIDE_TAGGED_CONTENT),
      Map.entry("SESimpleContentItem",  PDFUACheckpoint.TAGGED_CONTENT_ARTIFACTS),
      Map.entry("SEGraphicContentItem", PDFUACheckpoint.TAGGED_CONTENT_ARTIFACTS),
      Map.entry("SEMathMLStructElem",   PDFUACheckpoint.FORMULA_STRUCTURE_ELEMENTS),
      Map.entry("SEEm",                 PDFUACheckpoint.SPAN_STRUCTURE_ELEMENTS),
      Map.entry("SEStrong",             PDFUACheckpoint.SPAN_STRUCTURE_ELEMENTS),
      Map.entry("SESub",                PDFUACheckpoint.SPAN_STRUCTURE_ELEMENTS),
      Map.entry("SETableCell",          PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS),
      Map.entry("SENonStandard",        PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE),
      Map.entry("SENonStruct",          PDFUACheckpoint.DIV_STRUCTURE_ELEMENT),
      // Fonts / CMap / glyph
      // NOTE: veraPDF's "Glyph" rule is intentionally NOT mapped — ValidateUnicodeMapping
      // (native) covers this checkpoint with PAC-matching per-text-object granularity.
      // Adding "Glyph" here double-counts (native ~330 + vera ~10K on tagged docs).
      Map.entry("CMapFile",             PDFUACheckpoint.PREDEFINED_CMAPS),
      Map.entry("PDCMap",               PDFUACheckpoint.PREDEFINED_CMAPS),
      Map.entry("PDReferencedCMap",     PDFUACheckpoint.REFERENCE_CMAP),
      Map.entry("PDCIDFont",            PDFUACheckpoint.CID_GID_MAPPING),
      Map.entry("PDType0Font",          PDFUACheckpoint.REGISTRY_ENTRIES),
      Map.entry("PDTrueTypeFont",       PDFUACheckpoint.GLYPH_NAMES),
      Map.entry("PDFont",               PDFUACheckpoint.FONT_EMBEDDING),
      Map.entry("TrueTypeFontProgram",  PDFUACheckpoint.GLYPH_NAMES),
      // Lang / actual text
      Map.entry("CosLang",              PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR),
      Map.entry("CosActualText",        PDFUACheckpoint.NATURAL_LANGUAGE_ACTUAL_TEXT),
      Map.entry("CosAlt",               PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATIVE_TEXT),
      Map.entry("CosTextString",        PDFUACheckpoint.NATURAL_LANGUAGE_TEXT_OBJECT),
      Map.entry("CosFileSpecification", PDFUACheckpoint.F_UF_FILE_SPECIFICATION),
      // Misc
      // "PDPage" / "PDGoToAction" / "PDDestination" intentionally NOT mapped to
      // PDF_SYNTAX — CorePdfSyntaxCheck (native) already tallies the skeleton
      // objects PAC counts. Vera's UA-2 object-level passes on these types
      // over-emitted 3 extra passes on OP_AoD (PAC 978P, our 986P).
      Map.entry("PDOCConfig",           PDFUACheckpoint.NAME_ENTRY_OCCD)
  );

  /**
   * Clause-specific overrides that take precedence over object-based mapping. Used when
   * a particular rule inside an object has a distinct semantic (e.g. Figure BBox
   * geometric-containment rule lives in the Figure object but belongs under BBox).
   */
  private static final Map<String, PDFUACheckpoint> CLAUSE_OVERRIDES = Map.of(
      "8.2.5.28.2", PDFUACheckpoint.BOUNDED_BOXES
  );

  /**
   * UA-2 rule IDs that would otherwise map (via object type) to a checkpoint but which
   * PAC does not count on that row. Excluded to avoid false-positive errors that PAC
   * ignores.
   *
   * <ul>
   *   <li>{@code 5-5} — "pdfuaid:rev shall be the four digit year". Fires when {@code rev}
   *       is absent, not just when it's present-but-wrong. PAC's "PDF/UA identifier" row
   *       only checks that the identifier ({@code pdfuaid:part}) is declared; missing
   *       {@code rev} is not surfaced on that row.</li>
   *   <li>{@code 8.2.5.2-2} — "Document structure element shall be in the PDF 2.0
   *       namespace ({@code http://iso.org/pdf2/ssn})". Fires on UA-2 docs whose
   *       {@code Document} element uses a non-standard or missing namespace URI. PAC's
   *       "Logical structure syntax" row does not enforce namespace membership.</li>
   *   <li>{@code 8.8-1} / {@code 8.8-2} — "All destinations whose target lies within
   *       the current document shall be structure destinations". Fires on every page-
   *       level {@code /Dest} and {@code /GoTo} action. PAC's "PDF syntax" row does not
   *       enforce the structure-destination requirement; leaving these mapped inflates
   *       the row with dozens of false-positive errors on real-world UA-2 documents.</li>
   *   <li>{@code 8.4.5.5.1-1} — "The font programs for all fonts used for rendering
   *       shall be embedded". Native {@code ValidateFontsEmbedding} already visits
   *       every font wrapper and descendant CIDFont with PAC-matching semantics; vera's
   *       {@code containsFontFile} check reports false positives on some embedded
   *       CIDFonts and would also double-count PASSED entries via the pass-2 collection.</li>
   *   <li>{@code 8.2.5.14-1} — "The Note standard structure type shall not be present
   *       in conforming documents unless role mapped to a structure element in the
   *       PDF 2.0 namespace". Vera's test is unconditional ({@code false}), firing an
   *       error for every SENote on UA-2 documents. PAC leaves the per-tag Note row
   *       clean; the Note-ID uniqueness check under "Notes" already covers what PAC
   *       displays there.</li>
   *   <li>{@code 8.2.5.8-1} — "Each TOCI shall identify the target of the reference
   *       using the Ref entry". PAC does not enforce {@code Ref} presence on TOCI
   *       structure elements on the "TOCI" tag row.</li>
   *   <li>{@code 8.2.5.25-1} — "If Lbl structure elements are present, the ListNumbering
   *       attribute shall be present on the respective L structure element". PAC does
   *       not enforce {@code ListNumbering} on the "L" tag row.</li>
   *   <li>{@code 8.2.5.26-3} / {@code 8.2.5.26-4} — "Tables shall be regular. Table
   *       rows shall have the same number of columns". Native {@code
   *       ValidateTableRegularity} already emits the per-row findings PAC displays
   *       under Structure Elements → Tables → Table regularity; letting vera also fire
   *       under the Structure tree "Table" row double-attributes the same defect.</li>
   * </ul>
   */
  private static final Set<String> UA2_EXCLUDED_RULES = Set.of(
      "ISO 14289-2:2024-5-5",
      "ISO 14289-2:2024-8.2.5.2-2",
      "ISO 14289-2:2024-8.8-1",
      "ISO 14289-2:2024-8.8-2",
      "ISO 14289-2:2024-8.4.5.5.1-1",
      "ISO 14289-2:2024-8.2.5.14-1",
      "ISO 14289-2:2024-8.2.5.8-1",
      "ISO 14289-2:2024-8.2.5.25-1",
      "ISO 14289-2:2024-8.2.5.26-3",
      "ISO 14289-2:2024-8.2.5.26-4"
  );

  // Declared last so both OBJECT_TO_CHECKPOINT and CLAUSE_OVERRIDES are initialised
  // before we iterate the profile.
  private static final Map<String, PDFUACheckpoint> UA2_MAP = buildUa2Map();

  private static Map<String, PDFUACheckpoint> buildUa2Map() {
    Map<String, PDFUACheckpoint> m = new HashMap<>(2048);
    try {
      // Foundry must be initialised before profiles can be resolved. VeraRunner calls
      // VeraGreenfieldFoundryProvider.initialise() before running validation; call it
      // here too so map construction is independent of first-validation ordering.
      org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider.initialise();
      ValidationProfile profile = Profiles.getVeraProfileDirectory()
          .getValidationProfileByFlavour(PDFAFlavour.PDFUA_2);
      for (Rule r : profile.getRules()) {
        String clause = r.getRuleId().getClause();
        String test   = String.valueOf(r.getRuleId().getTestNumber());
        String spec   = r.getRuleId().getSpecification().getId();
        String ruleId = spec + "-" + clause + "-" + test;
        if (UA2_EXCLUDED_RULES.contains(ruleId)) continue;
        PDFUACheckpoint cp = CLAUSE_OVERRIDES.getOrDefault(clause,
            OBJECT_TO_CHECKPOINT.get(r.getObject()));
        if (cp != null) m.put(ruleId, cp);
      }
      log.info("PDFUA_2 rule mapping built: {} of {} rules mapped",
          m.size(), profile.getRules().size());
    } catch (Exception e) {
      log.warn("Failed to build PDFUA_2 mapping — checkpoints served only by UA-2 will show NA: {}",
          e.getMessage());
    }
    return Map.copyOf(m);
  }

  private VeraRuleMapping() {}

  public static PDFUACheckpoint toCheckpoint(String veraRuleId) {
    PDFUACheckpoint cp = UA1_MAP.get(veraRuleId);
    return cp != null ? cp : UA2_MAP.get(veraRuleId);
  }

  public static boolean covers(PDFUACheckpoint cp) {
    return UA1_MAP.containsValue(cp) || UA2_MAP.containsValue(cp);
  }

  public static boolean isWarning(String veraRuleId) {
    return WARNING_RULES.contains(veraRuleId);
  }
}
