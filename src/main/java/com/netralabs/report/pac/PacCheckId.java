package com.netralabs.report.pac;

import com.netralabs.domain.PDFUACheckpoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PAC-canonical {@code checkId} strings for every leaf checkpoint and every internal
 * branch node of the PDF/UA and WCAG 2 report trees.
 * <p>
 * These strings must stay byte-identical to PAC's output — downstream consumers key
 * off them. Extracted from PAC's reference {@code simple.json} export.
 */
public final class PacCheckId {

    public static final String PDFUA_ROOT = "PDF/UA";
    public static final String WCAG_ROOT = "WCAG2CheckSet";

    private PacCheckId() {}

    // ================================================================
    // Leaf checkIds — one per PDFUACheckpoint constant.
    // ================================================================
    private static final Map<PDFUACheckpoint, String> LEAF_IDS = new LinkedHashMap<>();
    static {
        // Basic requirements — PDF Syntax (ISO 32000-1)
        LEAF_IDS.put(PDFUACheckpoint.PDF_SYNTAX,                             "PDFParsable");
        LEAF_IDS.put(PDFUACheckpoint.PARENTS_OF_STRUCTURE_ELEMENTS,          "StructureElementHasParentKey");
        LEAF_IDS.put(PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX,               "StructureHasCorruptElements");
        LEAF_IDS.put(PDFUACheckpoint.STRUCTURE_PARENT_TREE,                  "StructuralParentTree");

        // Basic requirements — Fonts
        LEAF_IDS.put(PDFUACheckpoint.REGISTRY_ENTRIES,                       "FontType0RegistryIsIdentical");
        LEAF_IDS.put(PDFUACheckpoint.ORDERING_ENTRIES,                       "FontType0RegistryIsOrdering");
        LEAF_IDS.put(PDFUACheckpoint.SUPPLEMENT_ENTRIES,                     "FontType0SupplementIsNotLess");
        LEAF_IDS.put(PDFUACheckpoint.CID_GID_MAPPING,                        "FontType2HasCIDToGIDMap");
        LEAF_IDS.put(PDFUACheckpoint.PREDEFINED_CMAPS,                       "CMapPredefinedOrEmbedded");
        LEAF_IDS.put(PDFUACheckpoint.WMODE_ENTRY_IN_CMAP,                    "WModeInDictAndStreamIdentical");
        LEAF_IDS.put(PDFUACheckpoint.REFERENCE_CMAP,                         "CMapOnlyReferencesToPredefinedCMaps");
        LEAF_IDS.put(PDFUACheckpoint.FONT_EMBEDDING,                         "FontsAreEmbedded");
        LEAF_IDS.put(PDFUACheckpoint.ENCODING_ENTRY,                         "NonSymbolicTTFontContainsEncoding");
        LEAF_IDS.put(PDFUACheckpoint.ENCODING_SYMBOLIC,                      "SymbolicTTFontContainsNoEncoding");
        LEAF_IDS.put(PDFUACheckpoint.GLYPH_NAMES,                            "NonSymbolicTTFontContainsOnlyAdobeGlyphNames");

        // Basic requirements — Content
        LEAF_IDS.put(PDFUACheckpoint.TAGGED_CONTENT_ARTIFACTS,               "ContentIsTaggedOrArtifacted");
        LEAF_IDS.put(PDFUACheckpoint.ARTIFACT_INSIDE_TAGGED_CONTENT,         "NoArtifactInTaggedContent");
        LEAF_IDS.put(PDFUACheckpoint.TAGGED_CONTENT_INSIDE_ARTIFACT,         "NoTaggedContentInArtifact");
        LEAF_IDS.put(PDFUACheckpoint.MAPPING_OF_CHARACTER_TO_UNICODE,        "CharactersUnicodeMappable");
        LEAF_IDS.put(PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT,             "DocuementContainsNoReferenceXObjects");
        LEAF_IDS.put(PDFUACheckpoint.NAME_ENTRY_OCCD,                        "OCConfDictHasName");
        LEAF_IDS.put(PDFUACheckpoint.AS_ENTRY_OCCD,                          "OCConfDictHasNoAS");

        // Basic requirements — Embedded Files
        LEAF_IDS.put(PDFUACheckpoint.F_UF_FILE_SPECIFICATION,                "FSDictsContainsFAndUF");

        // Basic requirements — Natural language
        LEAF_IDS.put(PDFUACheckpoint.CORRECTNESS_LANGUAGE_ATR,               "ValidLanguageCheck");
        LEAF_IDS.put(PDFUACheckpoint.NATURAL_LANGUAGE_TEXT_OBJECT,           "TextContentHasLanguage");
        LEAF_IDS.put(PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATIVE_TEXT,      "AltTextHasLanguage");
        LEAF_IDS.put(PDFUACheckpoint.NATURAL_LANGUAGE_ACTUAL_TEXT,           "ActualTextHasLanguage");
        LEAF_IDS.put(PDFUACheckpoint.NATURAL_LANGUAGE_EXPANSION_TEXT,        "ExpansionTexHasLanguage");
        LEAF_IDS.put(PDFUACheckpoint.NATURAL_LANGUAGE_BOOKMARK,              "OutlineItemHasLanguage");
        LEAF_IDS.put(PDFUACheckpoint.NATURAL_LANGUAGE_CONTENTS,              "ContentsEntryHasLanguage");
        LEAF_IDS.put(PDFUACheckpoint.NATURAL_LANGUAGE_ALTERNATE_NAMES_FORM_FIELD, "TUEntryHasLanguage");

        // Logical Structure — Structure Elements — Headings
        LEAF_IDS.put(PDFUACheckpoint.USE_OF_EITHER,                          "HeadingStructureTypesNotMixed");
        LEAF_IDS.put(PDFUACheckpoint.FIRST_HEADING_LEVEL,                    "FirstHeadingIsH1");
        LEAF_IDS.put(PDFUACheckpoint.NESTING_HEADING_LEVEL,                  "NoHeadingsSkipped");
        LEAF_IDS.put(PDFUACheckpoint.H_STRUCTURE_ELEMENTS_WITHIN,            "OnlyOneHPerNode");

        // Logical Structure — Structure Elements — Notes
        LEAF_IDS.put(PDFUACheckpoint.ID_NOTE,                                "NoteTagHasID");
        LEAF_IDS.put(PDFUACheckpoint.UNIQUE_ID_ENTRIES,                      "NoteTagHasUniqueID");

        // Logical Structure — Structure Elements — Annotations
        LEAF_IDS.put(PDFUACheckpoint.TRAP_NET_ANNOTATIONS,                   "DocumentHasNoTrapNetAnnots");
        LEAF_IDS.put(PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS,             "WidgetAnnotationsInFormTag");
        LEAF_IDS.put(PDFUACheckpoint.NESTING_LINK_ANNOTATIONS,               "LinkAnnotationsInLinkTag");
        LEAF_IDS.put(PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT,              "AnnotationsInAnnotTag");
        LEAF_IDS.put(PDFUACheckpoint.PRINTER_MARK_ANNOTATIONS,               "PrinterMarkAnnotationaNotTagged");

        // Logical Structure — Structure Elements — Figures
        LEAF_IDS.put(PDFUACheckpoint.BOUNDED_BOXES,                          "FigureHasBBox");

        // Logical Structure — Structure Elements — Tables
        LEAF_IDS.put(PDFUACheckpoint.TABLE_REGULARITY,                       "TablesAreRegular");
        LEAF_IDS.put(PDFUACheckpoint.TABLE_HEADER_CELL_ASSIGNMENTS,          "THHasAssociatedCells");

        // Logical Structure — Structure tree
        LEAF_IDS.put(PDFUACheckpoint.DOCUMENT_STRUCTURE_ELEMENT,             "DocumentTag");
        LEAF_IDS.put(PDFUACheckpoint.PART_STRUCTURE_ELEMENT,                 "PartTag");
        LEAF_IDS.put(PDFUACheckpoint.ART_STRUCTURE_ELEMENT,                  "ArtTag");
        LEAF_IDS.put(PDFUACheckpoint.SECT_STRUCTURE_ELEMENT,                 "SectTag");
        LEAF_IDS.put(PDFUACheckpoint.DIV_STRUCTURE_ELEMENT,                  "DivTag");
        LEAF_IDS.put(PDFUACheckpoint.BLOCKQUOTE_STRUCTURE_ELEMENT,           "BlockQuoteTag");
        LEAF_IDS.put(PDFUACheckpoint.CAPTION_STRUCTURE_ELEMENTS,             "CaptionTag");
        LEAF_IDS.put(PDFUACheckpoint.TOC_STRUCTURE_ELEMENTS,                 "TOCTag");
        LEAF_IDS.put(PDFUACheckpoint.TOCI_STRUCTURE_ELEMENTS,                "TOCITag");
        LEAF_IDS.put(PDFUACheckpoint.INDEX_STRUCTURE_ELEMENTS,               "IndexTag");
        LEAF_IDS.put(PDFUACheckpoint.PRIVATE_STRUCTURE_ELEMENTS,             "PrivateTag");
        LEAF_IDS.put(PDFUACheckpoint.H_STRUCTURE_ELEMENTS,                   "HTag");
        LEAF_IDS.put(PDFUACheckpoint.H1_STRUCTURE_ELEMENTS,                  "H1Tag");
        LEAF_IDS.put(PDFUACheckpoint.H2_STRUCTURE_ELEMENTS,                  "H2Tag");
        LEAF_IDS.put(PDFUACheckpoint.H3_STRUCTURE_ELEMENTS,                  "H3Tag");
        LEAF_IDS.put(PDFUACheckpoint.H4_STRUCTURE_ELEMENTS,                  "H4Tag");
        LEAF_IDS.put(PDFUACheckpoint.H5_STRUCTURE_ELEMENTS,                  "H5Tag");
        LEAF_IDS.put(PDFUACheckpoint.H6_STRUCTURE_ELEMENTS,                  "H6Tag");
        LEAF_IDS.put(PDFUACheckpoint.P_STRUCTURE_ELEMENTS,                   "PTag");
        LEAF_IDS.put(PDFUACheckpoint.L_STRUCTURE_ELEMENTS,                   "LTag");
        LEAF_IDS.put(PDFUACheckpoint.LI_STRUCTURE_ELEMENTS,                  "LITag");
        LEAF_IDS.put(PDFUACheckpoint.Lbl_STRUCTURE_ELEMENTS,                 "LblTag");
        LEAF_IDS.put(PDFUACheckpoint.LBODY_STRUCTURE_ELEMENTS,               "LBodyTag");
        LEAF_IDS.put(PDFUACheckpoint.TABLE_STRUCTURE_ELEMENTS,               "TableTag");
        LEAF_IDS.put(PDFUACheckpoint.TR_STRUCTURE_ELEMENTS,                  "TRTag");
        LEAF_IDS.put(PDFUACheckpoint.TH_STRUCTURE_ELEMENTS,                  "THTag");
        LEAF_IDS.put(PDFUACheckpoint.TD_STRUCTURE_ELEMENTS,                  "TDTag");
        LEAF_IDS.put(PDFUACheckpoint.THEAD_STRUCTURE_ELEMENTS,               "THeadTag");
        LEAF_IDS.put(PDFUACheckpoint.TBODY_STRUCTURE_ELEMENTS,               "TBodyTag");
        LEAF_IDS.put(PDFUACheckpoint.TFOOT_STRUCTURE_ELEMENTS,               "TFootTag");
        LEAF_IDS.put(PDFUACheckpoint.SPAN_STRUCTURE_ELEMENTS,                "SpanTag");
        LEAF_IDS.put(PDFUACheckpoint.QUOTE_STRUCTURE_ELEMENTS,               "QuoteTag");
        LEAF_IDS.put(PDFUACheckpoint.NOTE_STRUCTURE_ELEMENTS,                "NoteTag");
        LEAF_IDS.put(PDFUACheckpoint.REFERENCE_STRUCTURE_ELEMENTS,           "ReferenceTag");
        LEAF_IDS.put(PDFUACheckpoint.BIBENTRY_STRUCTURE_ELEMENTS,            "BibEntryTag");
        LEAF_IDS.put(PDFUACheckpoint.CODE_STRUCTURE_ELEMENTS,                "CodeTag");
        LEAF_IDS.put(PDFUACheckpoint.LINK_STRUCTURE_ELEMENTS,                "LinkTag");
        LEAF_IDS.put(PDFUACheckpoint.ANNOT_STRUCTURE_ELEMENTS,               "AnnotTag");
        LEAF_IDS.put(PDFUACheckpoint.RUBY_STRUCTURE_ELEMENTS,                "RubyTag");
        LEAF_IDS.put(PDFUACheckpoint.RB_STRUCTURE_ELEMENTS,                  "RBTag");
        LEAF_IDS.put(PDFUACheckpoint.RT_STRUCTURE_ELEMENTS,                  "RTTag");
        LEAF_IDS.put(PDFUACheckpoint.RP_STRUCTURE_ELEMENTS,                  "RPTag");
        LEAF_IDS.put(PDFUACheckpoint.WARICHU_STRUCTURE_ELEMENTS,             "WarichuTag");
        LEAF_IDS.put(PDFUACheckpoint.WP_STRUCTURE_ELEMENTS,                  "WPTag");
        LEAF_IDS.put(PDFUACheckpoint.WT_STRUCTURE_ELEMENTS,                  "WTTag");
        LEAF_IDS.put(PDFUACheckpoint.FIGURE_STRUCTURE_ELEMENTS,              "FigureTag");
        LEAF_IDS.put(PDFUACheckpoint.FORMULA_STRUCTURE_ELEMENTS,             "FormulaTag");
        LEAF_IDS.put(PDFUACheckpoint.FORM_STRUCTURE_ELEMENTS,                "FormTag");
        LEAF_IDS.put(PDFUACheckpoint.CONTENT_PRESENT,                        "MarkedContentIsInLegalPosition");

        // Logical Structure — Role mapping
        LEAF_IDS.put(PDFUACheckpoint.ROLE_MAPPING_FOR_STANDARD_STRUCTURE,    "StandardStructureTypeIsNotRemapped");
        LEAF_IDS.put(PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE,"NonStandardStructureTypeIsRemapped");
        LEAF_IDS.put(PDFUACheckpoint.CIRCULAR_ROLE_MAPPING,                  "NoCircularMappings");

        // Logical Structure — Alternative Descriptions
        LEAF_IDS.put(PDFUACheckpoint.ALTERNATIVE_TEXT_FOR_FIGURE,            "FigureHasAltText");
        LEAF_IDS.put(PDFUACheckpoint.ALTERNATIVE_TEXT_FOR_FORMULA,           "FormulaHasAltText");
        LEAF_IDS.put(PDFUACheckpoint.ALTERNATIVE_NAMES_FORM_FIELDS,          "FormFieldHasTUKey");
        LEAF_IDS.put(PDFUACheckpoint.ALTERNATIVE_DESCRIPTION_FOR_ANNOT,      "AnnotationHasAltText");

        // Metadata and Settings — Metadata
        LEAF_IDS.put(PDFUACheckpoint.XMP_METADATA,                           "MetadataInCatalogExists");
        LEAF_IDS.put(PDFUACheckpoint.PDF_UA_IDENTIFIER,                      "DocumentContainsPDFUAIdentification");
        LEAF_IDS.put(PDFUACheckpoint.TITLE_XMP_METADATA,                     "DocumentContainsTitle");

        // Metadata and Settings — Document settings
        LEAF_IDS.put(PDFUACheckpoint.DISPLAY_DOCUMENT_TITLE,                 "DisplayDocTitleIsTrue");
        LEAF_IDS.put(PDFUACheckpoint.TAG_SUSPECTS,                           "SuspectsIsFalse");
        LEAF_IDS.put(PDFUACheckpoint.MARK_TAGGED_DOCUMENT,                   "MarkedIsTrue");
        LEAF_IDS.put(PDFUACheckpoint.DYNAMIC_XFA_FORM,                       "XFAIsNotDynamic");
        LEAF_IDS.put(PDFUACheckpoint.SECURITY_SETTINGS,                      "AccessibleEncyptionSettings");
        LEAF_IDS.put(PDFUACheckpoint.TAB_ORDER_PAGES,                        "PagesWithAnnotsHaveTabOrderS");

        // WCAG-only checkpoint (feeds WCAG 1.4.3 leaf)
        LEAF_IDS.put(PDFUACheckpoint.CONTRAST_OF_TEXT,                       "TextContentHasValidContrast");

        // Quality-only checkpoints — no PAC counterpart; synthetic stable IDs.
        LEAF_IDS.put(PDFUACheckpoint.Q_DOC_TITLE_VALIDITY,                   "QualityDocumentTitleValidity");
        LEAF_IDS.put(PDFUACheckpoint.Q_ARTIFACTED_ON_BODY,                   "QualityArtifactedContentOnPageBody");
        LEAF_IDS.put(PDFUACheckpoint.Q_TAGGED_TEXT_WHITESPACE,               "QualityTaggedTextIsOnlyWhitespace");
        LEAF_IDS.put(PDFUACheckpoint.Q_TAGGED_OUTSIDE_PAGE,                  "QualityTaggedContentOutsidePageBoundary");
        LEAF_IDS.put(PDFUACheckpoint.Q_PRESENCE_HEADINGS,                    "QualityPresenceOfHeadings");
        LEAF_IDS.put(PDFUACheckpoint.Q_PRESENCE_BOOKMARKS,                   "QualityPresenceOfBookmarks");
        LEAF_IDS.put(PDFUACheckpoint.Q_TOCI_CONTAIN_LINK,                    "QualityTociContainsLink");
        LEAF_IDS.put(PDFUACheckpoint.Q_TOCI_LINKED_TO_HEADINGS,              "QualityTociLinkedToHeadings");
        LEAF_IDS.put(PDFUACheckpoint.Q_ALT_TEXT_VALIDITY,                    "QualityAltTextValidity");
        LEAF_IDS.put(PDFUACheckpoint.Q_ALT_ON_TEXT_ELEMENTS,                 "QualityAltTextOnTextElements");
        LEAF_IDS.put(PDFUACheckpoint.Q_LINK_COMPLETENESS,                    "QualityLinkCompleteness");
        LEAF_IDS.put(PDFUACheckpoint.Q_LI_FORMAL_CORRECTNESS,                "QualityLiFormalCorrectness");
        LEAF_IDS.put(PDFUACheckpoint.Q_TABLE_COMPLETENESS,                   "QualityTableCompleteness");
        LEAF_IDS.put(PDFUACheckpoint.Q_NOTE_REFERENCED,                      "QualityNoteReferenced");
        LEAF_IDS.put(PDFUACheckpoint.Q_NOTE_CONTAINS_LBL,                    "QualityNoteContainsLbl");
        LEAF_IDS.put(PDFUACheckpoint.Q_P_CONTAINS_NOTE,                      "QualityParagraphContainsNote");
    }

    // ================================================================
    // PDF/UA branch IDs — keyed by joined path.
    // ================================================================
    private static final Map<String, String> PDFUA_BRANCH_IDS = new LinkedHashMap<>();
    static {
        // Categories under PDF/UA root.
        PDFUA_BRANCH_IDS.put("Basic requirements",                                "PDFUABasicRequirements");
        PDFUA_BRANCH_IDS.put("Logical Structure",                                 "PDFUAStructure");
        PDFUA_BRANCH_IDS.put("Metadata and Settings",                             "PDFUASettingsAndMetadata");

        // Sub-categories under Basic requirements.
        PDFUA_BRANCH_IDS.put("Basic requirements|PDF Syntax (ISO 32000-1)",       "ISO32000-1");
        PDFUA_BRANCH_IDS.put("Basic requirements|Fonts",                          "PDFUAFont");
        PDFUA_BRANCH_IDS.put("Basic requirements|Content",                        "PDFUAContent");
        PDFUA_BRANCH_IDS.put("Basic requirements|Embedded Files",                 "PDFUAEmbeddedFiles");
        PDFUA_BRANCH_IDS.put("Basic requirements|Natural language",               "PDFUANaturalLanguage");

        // Optional Content is a group under Basic requirements → Content.
        PDFUA_BRANCH_IDS.put("Basic requirements|Content|Optional Content",       "PDFUAOptionalContent");

        // Sub-categories under Logical Structure.
        PDFUA_BRANCH_IDS.put("Logical Structure|Structure Elements",              "PDFUAStructureElements");
        PDFUA_BRANCH_IDS.put("Logical Structure|Structure tree",                  "PDFUATagging");
        PDFUA_BRANCH_IDS.put("Logical Structure|Role mapping",                    "PDFUARoleMap");
        PDFUA_BRANCH_IDS.put("Logical Structure|Alternative Descriptions",        "PDFUAAlternativeText");

        // Groups under Logical Structure → Structure Elements.
        PDFUA_BRANCH_IDS.put("Logical Structure|Structure Elements|Headings",     "PDFUAHeading");
        PDFUA_BRANCH_IDS.put("Logical Structure|Structure Elements|Notes",        "PDFUANotes");
        PDFUA_BRANCH_IDS.put("Logical Structure|Structure Elements|Annotations",  "PDFUAAnnotations");
        PDFUA_BRANCH_IDS.put("Logical Structure|Structure Elements|Figures",      "PDFUAFigure");
        PDFUA_BRANCH_IDS.put("Logical Structure|Structure Elements|Tables",       "PDFUATable");

        // Sub-categories under Metadata and Settings.
        // Note: "PDFUAMetadatata" is PAC's original spelling — kept verbatim.
        PDFUA_BRANCH_IDS.put("Metadata and Settings|Metadata",                    "PDFUAMetadatata");
        PDFUA_BRANCH_IDS.put("Metadata and Settings|Document settings",           "PDFUADocumentSettings");
    }

    // ================================================================
    // WCAG branch IDs — keyed by the WCAG label string used in WCAGCriterion.
    // Principles / guidelines / criteria all resolve here.
    // ================================================================
    private static final Map<String, String> WCAG_BRANCH_IDS = new LinkedHashMap<>();
    static {
        // Principles
        WCAG_BRANCH_IDS.put("1 Perceivable",                                      "WCAG2PerceivableCheckSet");
        WCAG_BRANCH_IDS.put("2 Operable",                                         "WCAG2OperableCheckSet");
        WCAG_BRANCH_IDS.put("3 Understandable",                                   "WCAG2UnderstandableCheckSet");
        WCAG_BRANCH_IDS.put("4 Robust",                                           "WCAG2RobustCheckSet");

        // Guidelines
        WCAG_BRANCH_IDS.put("1.1 Text Alternatives",                              "WCAG2TextAlternativesCheckSet");
        WCAG_BRANCH_IDS.put("1.2 Time-based Media",                               "WCAG2TimeBasedMediaCheckSet");
        WCAG_BRANCH_IDS.put("1.3 Adaptable",                                      "WCAG2AdaptableCheckSet");
        WCAG_BRANCH_IDS.put("1.4 Distinguishable",                                "WCAG2DistinguishableCheckSet");
        WCAG_BRANCH_IDS.put("2.1 Keyboard Accessible",                            "WCAG2KeyboardAccessibleCheckSet");
        WCAG_BRANCH_IDS.put("2.2 Enough Time",                                    "WCAG2EnoughTimeCheckSet");
        WCAG_BRANCH_IDS.put("2.3 Seizures and Physical Reactions",                "WCAG2SeizuresAndPhysicalReactionsCheckSet");
        WCAG_BRANCH_IDS.put("2.4 Navigable",                                      "WCAG2NavigableCheckSet");
        WCAG_BRANCH_IDS.put("2.5 Input Modalities",                               "WCAG2InputModalitiesCheckSet");
        WCAG_BRANCH_IDS.put("3.1 Readable",                                       "WCAG2ReadableCheckSet");
        WCAG_BRANCH_IDS.put("3.2 Predictable",                                    "WCAG2PredictableCheckSet");
        WCAG_BRANCH_IDS.put("3.3 Input Assistance",                               "WCAG2InputAssistanceCheckSet");
        WCAG_BRANCH_IDS.put("4.1 Compatible",                                     "WCAG2CompatibleCheckSet");

        // Criteria (rendered as leaves when the criterion has no named sub-leaves).
        WCAG_BRANCH_IDS.put("1.1.1 Non-text Content",                             "WCAG2NonTextContentCheckSet");
        WCAG_BRANCH_IDS.put("1.2.1 Audio-only and Video-only (Prerecorded)",      "WCAG2AudioOnlyAndVideoOnlyCheckSet");
        WCAG_BRANCH_IDS.put("1.2.2 Captions (Prerecorded)",                       "WCAG2CaptionsPrerecordedCheckSet");
        WCAG_BRANCH_IDS.put("1.2.3 Audio Description or Media Alternative (Prerecorded)", "WCAG2AudioDescriptionOrMediaAlternativeCheckSet");
        WCAG_BRANCH_IDS.put("1.2.4 Captions (Live)",                              "WCAG2CaptionsLiveCheckSet");
        WCAG_BRANCH_IDS.put("1.2.5 Audio Description (Prerecorded)",              "WCAG2AudioDescriptionPrerecordedCheckSet");
        WCAG_BRANCH_IDS.put("1.3.1 Info and Relationships",                       "WCAG2InfoAndRelationshipsCheckSet");
        WCAG_BRANCH_IDS.put("1.3.2 Meaningful Sequence",                          "WCAG2MeaningfulSequenceCheckSet");
        WCAG_BRANCH_IDS.put("1.3.3 Sensory Characteristics",                      "WCAG2SensoryCharacteristicsCheckSet");
        WCAG_BRANCH_IDS.put("1.3.4 Orientation",                                  "WCAG2OrientationCheckSet");
        WCAG_BRANCH_IDS.put("1.3.5 Identify Input Purpose",                       "WCAG2IdentifyInputPurposeCheckSet");
        WCAG_BRANCH_IDS.put("1.4.1 Use of Color",                                 "WCAG2UseOfColorCheckSet");
        WCAG_BRANCH_IDS.put("1.4.2 Audio Control",                                "WCAG2AudioControlCheckSet");
        WCAG_BRANCH_IDS.put("1.4.3 Contrast (Minimum)",                           "WCAG2ContrastMinimumCheckSet");
        WCAG_BRANCH_IDS.put("1.4.4 Resize text",                                  "WCAG2ResizeTextCheckSet");
        WCAG_BRANCH_IDS.put("1.4.5 Images of Text",                               "WCAG2ImagesOfTextCheckSet");
        WCAG_BRANCH_IDS.put("1.4.10 Reflow",                                      "WCAG2ReflowCheckSet");
        WCAG_BRANCH_IDS.put("1.4.11 Non-text Contrast",                           "WCAG2NonTextContrastCheckSet");
        WCAG_BRANCH_IDS.put("1.4.12 Text Spacing",                                "WCAG2TextSpacingCheckSet");
        WCAG_BRANCH_IDS.put("1.4.13 Content on Hover or Focus",                   "WCAG2ContentOnHoverOrFocusCheckSet");
        WCAG_BRANCH_IDS.put("2.1.1 Keyboard",                                     "WCAG2KeyboardCheckSet");
        WCAG_BRANCH_IDS.put("2.1.2 No Keyboard Trap",                             "WCAG2NoKeyboardTrapCheckSet");
        WCAG_BRANCH_IDS.put("2.1.4 Character Key Shortcuts",                      "WCAG2CharacterKeyShortcutsCheckSet");
        WCAG_BRANCH_IDS.put("2.2.1 Timing Adjustable",                            "WCAG2TimingAdjustableCheckSet");
        WCAG_BRANCH_IDS.put("2.2.2 Pause, Stop, Hide",                            "WCAG2PauseStopHideCheckSet");
        WCAG_BRANCH_IDS.put("2.3.1 Three Flashes or Below Threshold",             "WCAG2ThreeFlashesOrBelowThresholdCheckSet");
        WCAG_BRANCH_IDS.put("2.4.1 Bypass Blocks",                                "WCAG2BypassBlocksCheckSet");
        WCAG_BRANCH_IDS.put("2.4.2 Page Titled",                                  "WCAG2PageTitledCheckSet");
        WCAG_BRANCH_IDS.put("2.4.3 Focus Order",                                  "WCAG2FocusOrderCheckSet");
        WCAG_BRANCH_IDS.put("2.4.4 Link Purpose (In Context)",                    "WCAG2LinkPurposeInContextCheckSet");
        WCAG_BRANCH_IDS.put("2.4.5 Multiple Ways",                                "WCAG2MultipleWaysCheckSet");
        WCAG_BRANCH_IDS.put("2.4.6 Headings and Labels",                          "WCAG2HeadingsAndLabelsCheckSet");
        WCAG_BRANCH_IDS.put("2.4.7 Focus Visible",                                "WCAG2FocusVisibleCheckSet");
        WCAG_BRANCH_IDS.put("2.5.1 Pointer Gestures",                             "WCAG2PointerGesturesCheckSet");
        WCAG_BRANCH_IDS.put("2.5.2 Pointer Cancellation",                         "WCAG2PointerCancellationCheckSet");
        WCAG_BRANCH_IDS.put("2.5.3 Label in Name",                                "WCAG2LabelInNameCheckSet");
        WCAG_BRANCH_IDS.put("2.5.4 Motion Actuation",                             "WCAG2MotionActuationCheckSet");
        WCAG_BRANCH_IDS.put("3.1.1 Language of Page",                             "WCAG2LanguageOfPageCheckSet");
        WCAG_BRANCH_IDS.put("3.1.2 Language of Parts",                            "WCAG2LanguageOfPartsCheckSet");
        WCAG_BRANCH_IDS.put("3.2.1 On Focus",                                     "WCAG2OnFocusCheckSet");
        WCAG_BRANCH_IDS.put("3.2.2 On Input",                                     "WCAG2OnInputCheckSet");
        WCAG_BRANCH_IDS.put("3.2.3 Consistent Navigation",                        "WCAG2ConsistentNavigationCheckSet");
        WCAG_BRANCH_IDS.put("3.2.4 Consistent Identification",                    "WCAG2ConsistentIdentificationCheckSet");
        WCAG_BRANCH_IDS.put("3.3.1 Error Identification",                         "WCAG2ErrorIdentificationCheckSet");
        WCAG_BRANCH_IDS.put("3.3.2 Labels or Instructions",                       "WCAG2LabelsOrInstructionsCheckSet");
        WCAG_BRANCH_IDS.put("3.3.3 Error Suggestion",                             "WCAG2ErrorSuggestionCheckSet");
        WCAG_BRANCH_IDS.put("3.3.4 Error Prevention (Legal, Financial, Data)",    "WCAG2ErrorPreventionLegalFinancialDataCheckSet");
        WCAG_BRANCH_IDS.put("4.1.1 Parsing",                                      "WCAG2ParsingCheckSet");
        WCAG_BRANCH_IDS.put("4.1.2 Name, Role, Value",                            "WCAG2NameRoleValueCheckSet");
        WCAG_BRANCH_IDS.put("4.1.3 Status Messages",                              "WCAG2StatusMessagesCheckSet");
    }

    /** PAC checkId for the leaf tied to a checkpoint. Never null for defined enum values. */
    public static String forLeaf(PDFUACheckpoint cp) {
        String id = LEAF_IDS.get(cp);
        return id != null ? id : cp.name();
    }

    /** PAC checkId for a PDF/UA internal branch node (category, subCategory, or group). */
    public static String forPdfUaBranch(String... pathSegments) {
        return PDFUA_BRANCH_IDS.get(joinPath(pathSegments));
    }

    /** PAC checkId for a WCAG hierarchy label (principle, guideline, or criterion). */
    public static String forWcagBranch(String label) {
        return WCAG_BRANCH_IDS.get(label);
    }

    private static String joinPath(String... segments) {
        StringBuilder sb = new StringBuilder();
        for (String s : segments) {
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append('|');
            sb.append(s);
        }
        return sb.toString();
    }
}
