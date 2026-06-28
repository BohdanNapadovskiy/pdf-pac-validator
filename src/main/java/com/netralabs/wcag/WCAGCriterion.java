package com.netralabs.wcag;

import com.netralabs.domain.PDFUACheckpoint;
import lombok.Getter;

import java.util.List;

/**
 * WCAG 2.2 leaf-level checkpoints as PAC renders them in its "WCAG" tab.
 * <p>
 * Each constant is a leaf in the four-level tree:
 * {@code Principle → Guideline → Criterion → Leaf}.
 * <p>
 * Label strings are PAC-canonical — keep byte-identical to PAC's UI so the
 * downstream consumer aligns with PAC's report.
 * <p>
 * {@code leaf == null} signals "this criterion has no named sub-leaves in PAC";
 * the criterion row itself carries the status (e.g. 1.2.1, 2.1.1, 4.1.2).
 */
@Getter
public enum WCAGCriterion {

    // ============================================================
    // 1 Perceivable
    // ============================================================

    // 1.1 Text Alternatives — 1.1.1 Non-text Content
    P_1_1_1_FIGURE_ALT(
            "1 Perceivable", "1.1 Text Alternatives", "1.1.1 Non-text Content",
            "Alternative text for \"Figure\" structure elements",
            PDFUACheckpoint.ALTERNATIVE_TEXT_FOR_FIGURE),
    P_1_1_1_FORMULA_ALT(
            "1 Perceivable", "1.1 Text Alternatives", "1.1.1 Non-text Content",
            "Alternative text for \"Formula\" structure elements",
            PDFUACheckpoint.ALTERNATIVE_TEXT_FOR_FORMULA),
    P_1_1_1_FORM_FIELD_NAMES(
            "1 Perceivable", "1.1 Text Alternatives", "1.1.1 Non-text Content",
            "Alternate names for form fields",
            PDFUACheckpoint.ALTERNATIVE_NAMES_FORM_FIELDS),
    P_1_1_1_ANNOT_DESC(
            "1 Perceivable", "1.1 Text Alternatives", "1.1.1 Non-text Content",
            "Alternative description for annotations",
            PDFUACheckpoint.ALTERNATIVE_DESCRIPTION_FOR_ANNOT),

    // 1.2 Time-based Media — no sub-leaves; not applicable to static PDFs
    P_1_2_1("1 Perceivable", "1.2 Time-based Media",
            "1.2.1 Audio-only and Video-only (Prerecorded)", null),
    P_1_2_2("1 Perceivable", "1.2 Time-based Media",
            "1.2.2 Captions (Prerecorded)", null),
    P_1_2_3("1 Perceivable", "1.2 Time-based Media",
            "1.2.3 Audio Description or Media Alternative (Prerecorded)", null),
    P_1_2_4("1 Perceivable", "1.2 Time-based Media",
            "1.2.4 Captions (Live)", null),
    P_1_2_5("1 Perceivable", "1.2 Time-based Media",
            "1.2.5 Audio Description (Prerecorded)", null),

    // 1.3 Adaptable — 1.3.1 Info and Relationships (the heavy one)
    P_1_3_1_MARK_TAGGED(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Mark for tagged documents", PDFUACheckpoint.MARK_TAGGED_DOCUMENT),
    P_1_3_1_ARTIFACTS_IN_TAGGED(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Artifacts inside tagged content", PDFUACheckpoint.ARTIFACT_INSIDE_TAGGED_CONTENT),
    P_1_3_1_TAGGED_IN_ARTIFACTS(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Tagged content inside artifacts", PDFUACheckpoint.TAGGED_CONTENT_INSIDE_ARTIFACT),
    P_1_3_1_TAGGED_AND_ARTIFACTS(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Tagged content and artifacts", PDFUACheckpoint.TAGGED_CONTENT_ARTIFACTS),
    P_1_3_1_TAG_SUSPECTS(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Tag suspects", PDFUACheckpoint.TAG_SUSPECTS),
    P_1_3_1_ROLE_MAP_NONSTD(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Role mapping of non-standard structure types",
            PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE),
    P_1_3_1_ROLE_MAP_CIRCULAR(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Circular role mapping", PDFUACheckpoint.CIRCULAR_ROLE_MAPPING),
    P_1_3_1_ROLE_MAP_STD(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Role mapping for standard structure types",
            PDFUACheckpoint.ROLE_MAPPING_FOR_STANDARD_STRUCTURE),
    P_1_3_1_TABLE_REGULARITY(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Table regularity", PDFUACheckpoint.TABLE_REGULARITY),
    P_1_3_1_BOUNDING_BOXES(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Bounding boxes", PDFUACheckpoint.BOUNDED_BOXES),
    P_1_3_1_UNICODE_MAP(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Mapping of characters to Unicode",
            PDFUACheckpoint.MAPPING_OF_CHARACTER_TO_UNICODE),
    P_1_3_1_TABLE_HEADERS(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Table header cell assignments",
            PDFUACheckpoint.TABLE_HEADER_CELL_ASSIGNMENTS),
    P_1_3_1_SECURITY(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Security settings and document accessibility by assistive technologies",
            PDFUACheckpoint.SECURITY_SETTINGS),
    P_1_3_1_OCCD_NAME(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Name entry in OCCDs (optional content configuration dictionaries)",
            PDFUACheckpoint.NAME_ENTRY_OCCD),
    P_1_3_1_PRINTER_MARK(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "\"PrinterMark\" annotations", PDFUACheckpoint.PRINTER_MARK_ANNOTATIONS),
    P_1_3_1_REF_EXTERNAL(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Referenced external objects", PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT),
    P_1_3_1_FONT_REGISTRY(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "\"Registry\" entries in Type 0 fonts", PDFUACheckpoint.REGISTRY_ENTRIES),
    P_1_3_1_FONT_ORDERING(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "\"Ordering\" entries in Type 0 fonts", PDFUACheckpoint.ORDERING_ENTRIES),
    P_1_3_1_FONT_SUPPLEMENT(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "\"Supplement\" entries in Type 0 fonts", PDFUACheckpoint.SUPPLEMENT_ENTRIES),
    P_1_3_1_FONT_CID_GID(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "\"CID\" to \"GID\" mapping of Type 2 CID fonts", PDFUACheckpoint.CID_GID_MAPPING),
    P_1_3_1_FONT_CMAP(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Predefined or embedded CMaps", PDFUACheckpoint.PREDEFINED_CMAPS),
    P_1_3_1_FONT_WMODE(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "\"WMode\" entry in CMap definition and CMap data",
            PDFUACheckpoint.WMODE_ENTRY_IN_CMAP),
    P_1_3_1_FONT_CMAP_REF(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "References inside CMaps to other CMaps", PDFUACheckpoint.REFERENCE_CMAP),
    P_1_3_1_FONT_TT_ENCODING(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Encoding entry in non-symbolic TrueType font", PDFUACheckpoint.ENCODING_ENTRY),
    P_1_3_1_FONT_TT_GLYPHS(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Glyph names in non-symbolic TrueType font", PDFUACheckpoint.GLYPH_NAMES),
    P_1_3_1_FONT_EMBEDDING(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Font embedding", PDFUACheckpoint.FONT_EMBEDDING),
    P_1_3_1_FONT_TT_SYMBOLIC(
            "1 Perceivable", "1.3 Adaptable", "1.3.1 Info and Relationships",
            "Encoding of symbolic TrueType fonts", PDFUACheckpoint.ENCODING_SYMBOLIC),

    // 1.3.2 - 1.3.5
    P_1_3_2("1 Perceivable", "1.3 Adaptable", "1.3.2 Meaningful Sequence", null),
    P_1_3_3("1 Perceivable", "1.3 Adaptable", "1.3.3 Sensory Characteristics", null),
    P_1_3_4("1 Perceivable", "1.3 Adaptable", "1.3.4 Orientation", null),
    P_1_3_5("1 Perceivable", "1.3 Adaptable", "1.3.5 Identify Input Purpose", null),

    // 1.4 Distinguishable
    P_1_4_1("1 Perceivable", "1.4 Distinguishable", "1.4.1 Use of Color", null),
    P_1_4_2("1 Perceivable", "1.4 Distinguishable", "1.4.2 Audio Control", null),
    // 1.4.3 Contrast (Minimum) → one leaf "Contrast of text" (rendering-based, not implemented in option a)
    P_1_4_3_TEXT_CONTRAST(
            "1 Perceivable", "1.4 Distinguishable", "1.4.3 Contrast (Minimum)",
            "Contrast of text"),
    P_1_4_4("1 Perceivable", "1.4 Distinguishable", "1.4.4 Resize text", null),
    P_1_4_5("1 Perceivable", "1.4 Distinguishable", "1.4.5 Images of Text", null),
    P_1_4_10("1 Perceivable", "1.4 Distinguishable", "1.4.10 Reflow", null),
    P_1_4_11("1 Perceivable", "1.4 Distinguishable", "1.4.11 Non-text Contrast", null),
    P_1_4_12("1 Perceivable", "1.4 Distinguishable", "1.4.12 Text Spacing", null),
    P_1_4_13("1 Perceivable", "1.4 Distinguishable", "1.4.13 Content on Hover or Focus", null),

    // ============================================================
    // 2 Operable
    // ============================================================

    O_2_1_1("2 Operable", "2.1 Keyboard Accessible", "2.1.1 Keyboard", null),
    O_2_1_2("2 Operable", "2.1 Keyboard Accessible", "2.1.2 No Keyboard Trap", null),
    O_2_1_4("2 Operable", "2.1 Keyboard Accessible", "2.1.4 Character Key Shortcuts", null),

    O_2_2_1("2 Operable", "2.2 Enough Time", "2.2.1 Timing Adjustable", null),
    O_2_2_2("2 Operable", "2.2 Enough Time", "2.2.2 Pause, Stop, Hide", null),

    O_2_3_1("2 Operable", "2.3 Seizures and Physical Reactions",
            "2.3.1 Three Flashes or Below Threshold", null),

    // 2.4 Navigable
    O_2_4_1("2 Operable", "2.4 Navigable", "2.4.1 Bypass Blocks", null),
    O_2_4_2_XMP_METADATA(
            "2 Operable", "2.4 Navigable", "2.4.2 Page Titled",
            "XMP Metadata", PDFUACheckpoint.XMP_METADATA),
    O_2_4_2_TITLE_IN_XMP(
            "2 Operable", "2.4 Navigable", "2.4.2 Page Titled",
            "Title in XMP metadata", PDFUACheckpoint.TITLE_XMP_METADATA),
    O_2_4_2_DISPLAY_TITLE(
            "2 Operable", "2.4 Navigable", "2.4.2 Page Titled",
            "Display of document title in window title",
            PDFUACheckpoint.DISPLAY_DOCUMENT_TITLE),
    O_2_4_3_TAB_ORDER(
            "2 Operable", "2.4 Navigable", "2.4.3 Focus Order",
            "Tab order for pages with annotations", PDFUACheckpoint.TAB_ORDER_PAGES),
    O_2_4_4("2 Operable", "2.4 Navigable", "2.4.4 Link Purpose (In Context)", null),
    O_2_4_5("2 Operable", "2.4 Navigable", "2.4.5 Multiple Ways", null),
    O_2_4_6("2 Operable", "2.4 Navigable", "2.4.6 Headings and Labels", null),
    O_2_4_7("2 Operable", "2.4 Navigable", "2.4.7 Focus Visible", null),

    O_2_5_1("2 Operable", "2.5 Input Modalities", "2.5.1 Pointer Gestures", null),
    O_2_5_2("2 Operable", "2.5 Input Modalities", "2.5.2 Pointer Cancellation", null),
    O_2_5_3("2 Operable", "2.5 Input Modalities", "2.5.3 Label in Name", null),
    O_2_5_4("2 Operable", "2.5 Input Modalities", "2.5.4 Motion Actuation", null),

    // ============================================================
    // 3 Understandable
    // ============================================================

    U_3_1_1("3 Understandable", "3.1 Readable", "3.1.1 Language of Page",
            null, PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR),

    // 3.1.2 Language of Parts (7 sub-leaves)
    U_3_1_2_TEXT_OBJECTS(
            "3 Understandable", "3.1 Readable", "3.1.2 Language of Parts",
            "Natural language of text objects",
            PDFUACheckpoint.NATURAL_LANGUAGE_TEXT_OBJECT),
    U_3_1_2_ALT_TEXT(
            "3 Understandable", "3.1 Readable", "3.1.2 Language of Parts",
            "Natural language of alternative text",
            PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATIVE_TEXT),
    U_3_1_2_ACTUAL_TEXT(
            "3 Understandable", "3.1 Readable", "3.1.2 Language of Parts",
            "Natural language of actual text",
            PDFUACheckpoint.NATURAL_LANGUAGE_ACTUAL_TEXT),
    U_3_1_2_EXPANSION_TEXT(
            "3 Understandable", "3.1 Readable", "3.1.2 Language of Parts",
            "Natural language of expansion text",
            PDFUACheckpoint.NATURAL_LANGUAGE_EXPANSION_TEXT),
    U_3_1_2_CONTENTS(
            "3 Understandable", "3.1 Readable", "3.1.2 Language of Parts",
            "Natural language of \"Contents\" entries in annotations",
            PDFUACheckpoint.NATURAL_LANGUAGE_CONTENTS),
    U_3_1_2_FORM_ALT_NAMES(
            "3 Understandable", "3.1 Readable", "3.1.2 Language of Parts",
            "Natural language of alternate names of form fields",
            PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATE_NAMES_FORM_FIELD),
    U_3_1_2_BOOKMARKS(
            "3 Understandable", "3.1 Readable", "3.1.2 Language of Parts",
            "Natural language of bookmarks (document outline)",
            PDFUACheckpoint.NATURAL_LANGUAGE_BOOKMARK),

    U_3_2_1("3 Understandable", "3.2 Predictable", "3.2.1 On Focus", null),
    U_3_2_2("3 Understandable", "3.2 Predictable", "3.2.2 On Input", null),
    U_3_2_3("3 Understandable", "3.2 Predictable", "3.2.3 Consistent Navigation", null),
    U_3_2_4("3 Understandable", "3.2 Predictable", "3.2.4 Consistent Identification", null),

    U_3_3_1("3 Understandable", "3.3 Input Assistance", "3.3.1 Error Identification", null),
    U_3_3_2("3 Understandable", "3.3 Input Assistance", "3.3.2 Labels or Instructions", null),
    U_3_3_3("3 Understandable", "3.3 Input Assistance", "3.3.3 Error Suggestion", null),
    U_3_3_4("3 Understandable", "3.3 Input Assistance",
            "3.3.4 Error Prevention (Legal, Financial, Data)", null),

    // ============================================================
    // 4 Robust
    // ============================================================

    // 4.1.1 Parsing — bulk of remapped checks
    R_4_1_1_PDF_SYNTAX(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "PDF syntax", PDFUACheckpoint.PDF_SYNTAX),
    R_4_1_1_STRUCT_PARENT_TREE(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Structural parent tree", PDFUACheckpoint.STRUCTURE_PARENT_TREE),
    R_4_1_1_PARENTS_OF_STRUCT(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Parents of structure elements", PDFUACheckpoint.PARENTS_OF_STRUCTURE_ELEMENTS),
    R_4_1_1_LOGICAL_STRUCT(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Logical structure syntax", PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX),
    R_4_1_1_USE_OF_H_OR_HN(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Use of either \"H\" or \"Hn\" structure elements", PDFUACheckpoint.USE_OF_EITHER),
    R_4_1_1_H_WITHIN_STRUCT(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H\" structure elements within a structure node",
            PDFUACheckpoint.H_STRUCTURE_ELEMENTS_WITHIN),
    // Structure-tree per-role counters
    R_4_1_1_P("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"P\" structure elements", PDFUACheckpoint.P_STRUCTURE_ELEMENTS),
    R_4_1_1_H("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H\" structure elements", PDFUACheckpoint.H_STRUCTURE_ELEMENTS),
    R_4_1_1_H1("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H1\" structure elements", PDFUACheckpoint.H1_STRUCTURE_ELEMENTS),
    R_4_1_1_H2("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H2\" structure elements", PDFUACheckpoint.H2_STRUCTURE_ELEMENTS),
    R_4_1_1_H3("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H3\" structure elements", PDFUACheckpoint.H3_STRUCTURE_ELEMENTS),
    R_4_1_1_H4("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H4\" structure elements", PDFUACheckpoint.H4_STRUCTURE_ELEMENTS),
    R_4_1_1_H5("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H5\" structure elements", PDFUACheckpoint.H5_STRUCTURE_ELEMENTS),
    R_4_1_1_H6("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"H6\" structure elements", PDFUACheckpoint.H6_STRUCTURE_ELEMENTS),
    R_4_1_1_DOCUMENT("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Document\" structure elements", PDFUACheckpoint.DOCUMENT_STRUCTURE_ELEMENT),
    R_4_1_1_PART("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Part\" structure elements", PDFUACheckpoint.PART_STRUCTURE_ELEMENT),
    R_4_1_1_ART("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Art\" structure elements", PDFUACheckpoint.ART_STRUCTURE_ELEMENT),
    R_4_1_1_SECT("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Sect\" structure elements", PDFUACheckpoint.SECT_STRUCTURE_ELEMENT),
    R_4_1_1_DIV("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Div\" structure elements", PDFUACheckpoint.DIV_STRUCTURE_ELEMENT),
    R_4_1_1_CAPTION("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Caption\" structure elements", PDFUACheckpoint.CAPTION_STRUCTURE_ELEMENTS),
    R_4_1_1_BLOCKQUOTE("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"BlockQuote\" structure elements", PDFUACheckpoint.BLOCKQUOTE_STRUCTURE_ELEMENT),
    R_4_1_1_INDEX("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Index\" structure elements", PDFUACheckpoint.INDEX_STRUCTURE_ELEMENTS),
    R_4_1_1_PRIVATE("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Private\" structure elements", PDFUACheckpoint.PRIVATE_STRUCTURE_ELEMENTS),
    R_4_1_1_LINK("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Link\" structure elements", PDFUACheckpoint.LINK_STRUCTURE_ELEMENTS),
    R_4_1_1_SPAN("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Span\" structure elements", PDFUACheckpoint.SPAN_STRUCTURE_ELEMENTS),
    R_4_1_1_BIBENTRY("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"BibEntry\" structure elements", PDFUACheckpoint.BIBENTRY_STRUCTURE_ELEMENTS),
    R_4_1_1_CODE("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Code\" structure elements", PDFUACheckpoint.CODE_STRUCTURE_ELEMENTS),
    R_4_1_1_NOTE("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Note\" structure elements", PDFUACheckpoint.NOTE_STRUCTURE_ELEMENTS),
    R_4_1_1_LBODY("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"LBody\" structure elements", PDFUACheckpoint.LBODY_STRUCTURE_ELEMENTS),
    R_4_1_1_LBL("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Lbl\" structure elements", PDFUACheckpoint.Lbl_STRUCTURE_ELEMENTS),
    R_4_1_1_TOC("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"TOC\" structure elements", PDFUACheckpoint.TOC_STRUCTURE_ELEMENTS),
    R_4_1_1_TOCI("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"TOCI\" structure elements", PDFUACheckpoint.TOCI_STRUCTURE_ELEMENTS),
    R_4_1_1_RUBY("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Ruby\" structure elements", PDFUACheckpoint.RUBY_STRUCTURE_ELEMENTS),
    R_4_1_1_RB("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"RB\" structure elements", PDFUACheckpoint.RB_STRUCTURE_ELEMENTS),
    R_4_1_1_RT("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"RT\" structure elements", PDFUACheckpoint.RT_STRUCTURE_ELEMENTS),
    R_4_1_1_RP("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"RP\" structure elements", PDFUACheckpoint.RP_STRUCTURE_ELEMENTS),
    R_4_1_1_WARICHU("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"Warichu\" structure elements", PDFUACheckpoint.WARICHU_STRUCTURE_ELEMENTS),
    R_4_1_1_WT("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"WT\" structure elements", PDFUACheckpoint.WT_STRUCTURE_ELEMENTS),
    R_4_1_1_WP("4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "\"WP\" structure elements", PDFUACheckpoint.WP_STRUCTURE_ELEMENTS),
    // 4.1.1 Annotation-nesting leaves
    R_4_1_1_NEST_ANNOT(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Nesting of annotations in Annot structure elements",
            PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT),
    R_4_1_1_NEST_LINK(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Nesting of \"Link\" annotations inside \"Link\" structure elements",
            PDFUACheckpoint.NESTING_LINK_ANNOTATIONS),
    R_4_1_1_NEST_WIDGET(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Nesting of \"Widget\" annotations inside a \"Form\" structure elements",
            PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS),
    R_4_1_1_UNIQUE_ID_NOTE(
            "4 Robust", "4.1 Compatible", "4.1.1 Parsing",
            "Unique \"ID\" entries in Note structure elements",
            PDFUACheckpoint.UNIQUE_ID_ENTRIES),

    R_4_1_2("4 Robust", "4.1 Compatible", "4.1.2 Name, Role, Value", null),
    R_4_1_3("4 Robust", "4.1 Compatible", "4.1.3 Status Messages", null);


    private final String principle;
    private final String guideline;
    private final String criterion;
    /** null means this criterion has no named sub-leaves in PAC. */
    private final String leaf;
    private final List<PDFUACheckpoint> sources;

    WCAGCriterion(String principle, String guideline, String criterion, String leaf,
                  PDFUACheckpoint... sources) {
        this.principle = principle;
        this.guideline = guideline;
        this.criterion = criterion;
        this.leaf = leaf;
        this.sources = List.of(sources);
    }
}