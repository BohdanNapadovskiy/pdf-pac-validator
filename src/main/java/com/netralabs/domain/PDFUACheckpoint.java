package com.netralabs.domain;

import com.netralabs.Rule;
import com.netralabs.basic.content.ValidateArtifactsInsideTagged;
import com.netralabs.basic.content.ValidateOCConfigAS;
import com.netralabs.basic.content.ValidateOCConfigName;
import com.netralabs.basic.content.ValidateReferencedExternalObjects;
import com.netralabs.basic.content.ValidateTaggedCoverage;
import com.netralabs.basic.content.ValidateTaggedInsideArtifacts;
import com.netralabs.basic.content.ValidateUnicodeMapping;
import com.netralabs.basic.emebededfiles.ValidateFileSpecFAndUF;
import com.netralabs.basic.fonts.*;
import com.netralabs.basic.naturallanguage.*;
import com.netralabs.basic.pdfsyntax.CorePdfSyntaxCheck;
import com.netralabs.basic.pdfsyntax.ValidateLogicalStructureSyntax;
import com.netralabs.basic.pdfsyntax.ValidateParentsOfStructureElements;
import com.netralabs.basic.pdfsyntax.ValidateStructuralParentTree;
import com.netralabs.logicalstructure.structureelements.StructElementByRoleRule;
import com.netralabs.logicalstructure.structureelements.annotations.ValidateAnnotationNesting;
import com.netralabs.logicalstructure.structureelements.figures.ValidateFigureBoundingBox;
import com.netralabs.logicalstructure.structureelements.tables.ValidateTableHeaderCellAssignments;
import com.netralabs.logicalstructure.structureelements.tables.ValidateTableRegularity;
import com.netralabs.logicalstructure.structureelements.headings.ValidateFirstHeadingLevel;
import com.netralabs.logicalstructure.structureelements.headings.ValidateHeadingInsideStructureNode;
import com.netralabs.logicalstructure.structureelements.headings.ValidateNestingOfHeadingLevels;
import com.netralabs.logicalstructure.structureelements.headings.ValidateUseOfEitherHOrHn;
import com.netralabs.logicalstructure.structureelements.notes.ValidateNoteIdPresence;
import com.netralabs.logicalstructure.structureelements.notes.ValidateNoteIdUniqueness;
import com.netralabs.metadata.DisplayDocTitleIdentifier;
import com.netralabs.metadata.DynamicXfaFormIdentifier;
import com.netralabs.metadata.MarkedTaggedDocumentIdentifier;
import com.netralabs.metadata.PdfUAIdentifier;
import com.netralabs.metadata.SecuritySettingIdentifier;
import com.netralabs.metadata.TabOrderByPageIdentifier;
import com.netralabs.metadata.TagSuspectsIdentifier;
import com.netralabs.metadata.TitleInXMPIdentifier;
import com.netralabs.metadata.XMPXIdentifier;
import lombok.Getter;

import java.util.EnumSet;
import java.util.function.Supplier;

@Getter
public enum PDFUACheckpoint {

    PDF_SYNTAX(
            "PDF/UA",
            "Basic requirements",
            "PDF Syntax (ISO 32000-1)",
            "PDF syntax",
            "PDF syntax is not valid",
            CorePdfSyntaxCheck::new,
            Phase.DOCUMENT
    ),
    PARENTS_OF_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Basic requirements",
            "PDF Syntax (ISO 32000-1)",
            "Parents of structure elements",
            "",
            ValidateParentsOfStructureElements::new,
            Phase.DOCUMENT
    ),
    LOGICAL_STRUCTURE_SYNTAX(
            "PDF/UA",
            "Basic requirements",
            "PDF Syntax (ISO 32000-1)",
            "Logical structure syntax",
            "",
            ValidateLogicalStructureSyntax::new,
            Phase.DOCUMENT
    ),
    STRUCTURE_PARENT_TREE(
            "PDF/UA",
            "Basic requirements",
            "PDF Syntax (ISO 32000-1)",
            "Structural parent tree",
            "",
            ValidateStructuralParentTree::new,
            Phase.DOCUMENT
    ),


    REGISTRY_ENTRIES(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "\"Registry\" entries in Type 0 fonts",
            "",
            ValidateCIDSystemInfoRegistry::new,
            Phase.DOCUMENT
    ),
    ORDERING_ENTRIES(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "\"Ordering\" entries in Type 0 fonts",
            "",
            ValidateCIDSystemInfoOrdering::new,
            Phase.DOCUMENT
    ),
    SUPPLEMENT_ENTRIES(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "\"Supplement\" entries in Type 0 fonts",
            "",
            ValidateCIDSystemInfoSupplement::new,
            Phase.DOCUMENT
    ),
    CID_GID_MAPPING(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "\"CID\" to \"GID\" mapping of Type 2 CID fonts",
            "",
            ValidateCidToGidMapForType2::new,
            Phase.DOCUMENT
    ),
    PREDEFINED_CMAPS(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Predefined or embedded CMaps",
            "",
            ValidatePredefinedOrEmbeddedCMaps::new,
            Phase.DOCUMENT
    ),
    WMODE_ENTRY_IN_CMAP(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "\"WMode\" entry in CMap definition and CMap data",
            "",
            ValidateCMapWMode::new,
            Phase.DOCUMENT
    ),
    REFERENCE_CMAP(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "References inside CMaps to other CMaps",
            "",
            ValidateCMapReferences::new,
            Phase.DOCUMENT
    ),
    FONT_EMBEDDING(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Font embedding",
            "",
            ValidateFontsEmbedding::new,
            Phase.DOCUMENT
    ),
    ENCODING_ENTRY(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Encoding entry in non-symbolic TrueType font",
            "",
            ValidateTTNonSymbolicEncoding::new,
            Phase.DOCUMENT
    ),
    ENCODING_SYMBOLIC(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Encoding of symbolic TrueType fonts",
            "",
            ValidateTTSymbolicEncoding::new,
            Phase.DOCUMENT
    ),
    GLYPH_NAMES(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Glyph names in non-symbolic TrueType font",
            "",
            ValidateTTNonSymbolicGlyphNames::new,
            Phase.DOCUMENT
    ),


    TAGGED_CONTENT_ARTIFACTS(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Tagged content and artifacts",
            "Object is not tagged",
            ValidateTaggedCoverage::new,
            Phase.DOCUMENT
    ),
    ARTIFACT_INSIDE_TAGGED_CONTENT(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Artifacts inside tagged content",
            "",
            ValidateArtifactsInsideTagged::new,
            Phase.DOCUMENT
    ),
    TAGGED_CONTENT_INSIDE_ARTIFACT(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Tagged content inside artifacts",
            "",
            ValidateTaggedInsideArtifacts::new,
            Phase.DOCUMENT
    ),
    MAPPING_OF_CHARACTER_TO_UNICODE(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Mapping of characters to Unicode",
            "",
            ValidateUnicodeMapping::new,
            Phase.DOCUMENT
    ),
    REFERENCED_EXTERNAL_OBJECT(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Referenced external objects",
            "",
            ValidateReferencedExternalObjects::new,
            Phase.PAGE
    ),
    NAME_ENTRY_OCCD(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Optional Content",
            "Name entry in OCCDs (optional content configuration dictionaries)",
            "Name entry in OCCDs",
            ValidateOCConfigName::new,
            Phase.DOCUMENT
    ),
    AS_ENTRY_OCCD(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Optional Content",
            "AS entry in OCCDs (optional content configuration dictionaries)",
            "AS entry in OCCDs",
            ValidateOCConfigAS::new,
            Phase.DOCUMENT
    ),

    F_UF_FILE_SPECIFICATION(
            "PDF/UA",
            "Basic requirements",
            "Embedded Files",
            "\"F\" and \"UF\" entries in file specifications",
            "",
            ValidateFileSpecFAndUF::new,
            Phase.DOCUMENT
    ),

    CORRECTNESS_LANGUAGE_ATR(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Correctness of language attribute",
            "Document language metadata contains a syntax error",
            ValidateLangAttributeCorrectness::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_TEXT_OBJECT(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Natural language of text objects",
            "Natural language for text object cannot be determined",
            ValidateLangOfTextObjects::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_ALTERNATIVE_TEXT(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Natural language of alternative text",
            "Natural language of alternative text cannot be determined",
            ValidateLangOfAltText::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_ACTUAL_TEXT(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Natural language of actual text",
            "Natural language of actual text cannot be determined",
            ValidateLangOfActualText::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_EXPANSION_TEXT(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Natural language of expansion text",
            "Natural language of expansion text cannot be determined",
            ValidateLangOfExpansionText::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_BOOKMARK(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Natural language of bookmarks (document outline)",
            "Natural language of bookmark cannot be determined",
            ValidateLangOfBookmarks::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_CONTENTS(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Natural language of \"Contents\" entries in annotations",
            "",
            ValidateLangOfAnnotationContents::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_ALTERNATE_NAMES_FORM_FIELD(
            "PDF/UA",
            "Basic requirements",
            "Natural language",
            "Natural language of alternate names of form fields",
            "",
            ValidateLangOfFormFieldAltNames::new,
            Phase.DOCUMENT
    ),


    USE_OF_EITHER(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Headings",
            "Use of either \"H\" or \"Hn\" structure elements",
            "",
            ValidateUseOfEitherHOrHn::new,
            Phase.DOCUMENT
    ),
    FIRST_HEADING_LEVEL(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Headings",
            "First heading level",
            "",
            ValidateFirstHeadingLevel::new,
            Phase.DOCUMENT
    ),
    NESTING_HEADING_LEVEL(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Headings",
            "Nesting of heading levels",
            "",
            ValidateNestingOfHeadingLevels::new,
            Phase.DOCUMENT
    ),
    H_STRUCTURE_ELEMENTS_WITHIN(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Headings",
            "\"H\" structure elements within a structure node",
            "",
            ValidateHeadingInsideStructureNode::new,
            Phase.DOCUMENT
    ),
    ID_NOTE(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Notes",
            "IDs of \"Note\" structure elements",
            "ID missing in Note structure element",
            ValidateNoteIdPresence::new,
            Phase.DOCUMENT
    ),
    UNIQUE_ID_ENTRIES(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Notes",
            "Unique\" ID\" entries in Note structure elements",
            "",
            ValidateNoteIdUniqueness::new,
            Phase.DOCUMENT
    ),
    TRAP_NET_ANNOTATIONS(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Annotations",
            "\"TrapNet\" annotations",
            "",
            null,
            null
    ),
    NESTING_WIDGET_ANNOTATIONS(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Annotations",
            "Nesting of \"Widget\" annotations inside a \"Form\" structure elements",
            "",
            ValidateAnnotationNesting::new,
            Phase.DOCUMENT
    ),
    NESTING_LINK_ANNOTATIONS(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Annotations",
            "Nesting of \"Link\" annotations inside \"Link\" structure elements",
            "",
            ValidateAnnotationNesting::new,
            Phase.DOCUMENT
    ),
    NESTING_ANNOTATIONS_ANNOT(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Annotations",
            "Nesting of annotations in Annot structure elements",
            "",
            ValidateAnnotationNesting::new,
            Phase.DOCUMENT
    ),
    PRINTER_MARK_ANNOTATIONS(
            "PDF/UA", "Logical Structure", "Structure Elements", "Annotations", "\"PrinterMark\" annotations", "",
            null,
            null
    ),
    BOUNDED_BOXES(
            "PDF/UA", "Logical Structure", "Structure Elements", "Figures", "Bounding boxes", "",
            ValidateFigureBoundingBox::new,
            Phase.DOCUMENT
    ),
    TABLE_REGULARITY(
            "PDF/UA", "Logical Structure", "Structure Elements", "Tables", "Table regularity", "Irregular table row",
            ValidateTableRegularity::new,
            Phase.DOCUMENT
    ),
    TABLE_HEADER_CELL_ASSIGNMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure Elements",
            "Tables",
            "Table header cell assignments",
            "Table Header Cell Has No Associated Sub Cells",
            ValidateTableHeaderCellAssignments::new,
            Phase.DOCUMENT
    ),


    DOCUMENT_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Document\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    PART_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Part\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    ART_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Art\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    SECT_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Sect\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    DIV_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Div\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    BLOCKQUOTE_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "\"BlockQuote\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    CAPTION_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Caption\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TOC_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"TOC\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TOCI_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"TOCI\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    INDEX_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Index\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    PRIVATE_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Private\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    H_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"H\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    H1_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"H1\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    H2_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"H2\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    H3_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"H3\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    H4_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"H4\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    H5_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"H5\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    H6_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"H6\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    P_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"P\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    L_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"L\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    LI_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"LI\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    Lbl_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Lbl\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    LBODY_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"LBody\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TABLE_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Table\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TR_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"TR\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TH_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"TH\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TD_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"TD\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    THEAD_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"THead\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TBODY_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"TBody\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    TFOOT_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"TFoot\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    SPAN_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Span\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    QUOTE_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Quote\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    NOTE_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Note\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    REFERENCE_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Reference\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    BIBENTRY_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"BibEntry\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    CODE_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Code\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    LINK_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Link\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    ANNOT_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Annot\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    RUBY_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Ruby\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    RB_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"RB\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    RT_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"RT\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    RP_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"RP\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    WARICHU_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Warichu\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    WP_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"WP\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    WT_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"WT\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    FIGURE_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Figure\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    FORMULA_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Formula\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    FORM_STRUCTURE_ELEMENTS(
            "PDF/UA", "Logical Structure", "Structure tree", "\"Form\" structure elements", "",
            StructElementByRoleRule::new, Phase.DOCUMENT
    ),
    CONTENT_PRESENT(
            "PDF/UA", "Logical Structure", "Structure tree", "Content is present in admissible locations", "",
            null, null
    ),

    ROLE_MAPPING_FOR_STANDARD_STRUCTURE(
            "PDF/UA",
            "Logical Structure",
            "Role mapping",
            "Role mapping for standard structure types",
            "",
            com.netralabs.logicalstructure.rolemapping.RoleMapValidatorRule::new,
            Phase.DOCUMENT
    ),
    ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE(
            "PDF/UA",
            "Logical Structure",
            "Role mapping",
            "Role mapping of non-standard structure types",
            "",
            com.netralabs.logicalstructure.rolemapping.RoleMapValidatorRule::new,
            Phase.DOCUMENT
    ),
    CIRCULAR_ROLE_MAPPING(
            "PDF/UA", "Logical Structure", "Role mapping", "Circular role mapping", "",
            com.netralabs.logicalstructure.rolemapping.RoleMapValidatorRule::new,
            Phase.DOCUMENT
    ),

    ALTERNATIVE_TEXT_FOR_FIGURE(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternative text for \"Figure\" structure elements",
            "",
            com.netralabs.logicalstructure.alternativedescriptions.AltTextForFigureRule::new,
            Phase.DOCUMENT
    ),
    ALTERNATIVE_TEXT_FOR_FORMULA(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternative text for \"Formula\" structure elements",
            "",
            null,
            null

    ),
    ALTERNATIVE_NAMES_FORM_FIELDS(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternate names for form fields",
            "",
            com.netralabs.logicalstructure.structureelements.annotations.ValidateFormFieldAltNames::new,
            Phase.DOCUMENT
    ),
    ALTERNATIVE_DESCRIPTION_FOR_ANNOT(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternative description for annotations",
            "",
            com.netralabs.logicalstructure.structureelements.annotations.ValidateAnnotationAltText::new,
            Phase.DOCUMENT
    ),

    //Metadata Part

    XMP_METADATA(
            "PDF/UA",
            "Metadata and Settings",
            "Metadata",
            "XMP Metadata",
            "XMP metadata missing in document",
            XMPXIdentifier::new,
            Phase.DOCUMENT

    ),

    PDF_UA_IDENTIFIER(
            "PDF/UA", "Metadata and Settings", "Metadata", "PDF/UA identifier", "PDF/UA identifier missing",
            PdfUAIdentifier::new,
            Phase.DOCUMENT
    ),

    TITLE_XMP_METADATA(
            "PDF/UA", "Metadata and Settings", "Metadata", "Title in XMP metadata", "Title missing in document XMP metadata",
            TitleInXMPIdentifier::new,
            Phase.DOCUMENT
    ),

    DISPLAY_DOCUMENT_TITLE(
            "PDF/UA",
            "Metadata and Settings",
            "Document settings",
            "Display of document title in window title",
            "'DisplayDocTitle' key is not set to true",
            DisplayDocTitleIdentifier::new,
            Phase.DOCUMENT
    ),

    TAG_SUSPECTS(
            "PDF/UA", "Metadata and Settings", "Document settings", "Tag suspects", "",
            TagSuspectsIdentifier::new,
            Phase.DOCUMENT
    ),

    MARK_TAGGED_DOCUMENT(
            "PDF/UA", "Metadata and Settings", "Document settings", "Mark for tagged documents", "",
            MarkedTaggedDocumentIdentifier::new,
            Phase.DOCUMENT
    ),

    DYNAMIC_XFA_FORM(
            "PDF/UA", "Metadata and Settings", "Document settings", "Dynamic XFA form", "",
            DynamicXfaFormIdentifier::new,
            Phase.DOCUMENT
    ),

    SECURITY_SETTINGS(
            "PDF/UA",
            "Metadata and Settings",
            "Document settings",
            "Security settings and document access by assistive technologies",
            "",
            SecuritySettingIdentifier::new,
            Phase.DOCUMENT
    ),

    // page-level example (runs once per page that has annotations)
    TAB_ORDER_PAGES(
            "PDF/UA",
            "Metadata and Settings",
            "Document settings",
            "Tab order for pages with annotations",
            "",
            TabOrderByPageIdentifier::new,
            Phase.PAGE
    ),

    // WCAG-only checkpoint — reportName="WCAG" so ReportBuilder omits it from the
    // PDF/UA report tree. Feeds WCAG 1.4.3 Contrast (Minimum) → "Contrast of text"
    // via WCAGCriterion.P_1_4_3_TEXT_CONTRAST source mapping.
    CONTRAST_OF_TEXT(
            "WCAG",
            "1 Perceivable",
            "1.4 Distinguishable",
            "Contrast of text",
            "",
            com.netralabs.wcag.contrast.ValidateContrastOfText::new,
            Phase.DOCUMENT
    ),

    // ================================================================
    // Quality-only checkpoints — reportName="Quality" so ReportBuilder
    // omits them from the PDF/UA report tree. Feed QualityCriterion
    // leaves via source mappings.
    // ================================================================
    Q_DOC_TITLE_VALIDITY(
            "Quality", "Quality", "Document", "Validity of document title", "",
            com.netralabs.quality.rules.ValidateDocumentTitle::new, Phase.DOCUMENT
    ),
    Q_ARTIFACTED_ON_BODY(
            "Quality", "Quality", "Content", "Artifacted content on page body", "",
            com.netralabs.quality.rules.ValidateArtifactedOnBody::new, Phase.DOCUMENT
    ),
    Q_TAGGED_TEXT_WHITESPACE(
            "Quality", "Quality", "Content", "Tagged text consists of only whitespace", "",
            com.netralabs.quality.rules.ValidateTaggedWhitespaceText::new, Phase.DOCUMENT
    ),
    Q_TAGGED_OUTSIDE_PAGE(
            "Quality", "Quality", "Content", "Tagged content exists outside of the page boundary", "",
            com.netralabs.quality.rules.ValidateTaggedOutsidePage::new, Phase.DOCUMENT
    ),
    Q_PRESENCE_HEADINGS(
            "Quality", "Quality", "Structure", "Presence of headings", "",
            com.netralabs.quality.rules.ValidatePresenceOfHeadings::new, Phase.DOCUMENT
    ),
    Q_PRESENCE_BOOKMARKS(
            "Quality", "Quality", "Structure", "Presence of bookmarks (document outline) if there are headings", "",
            com.netralabs.quality.rules.ValidatePresenceOfBookmarks::new, Phase.DOCUMENT
    ),
    Q_TOCI_CONTAIN_LINK(
            "Quality", "Quality", "TOC", "\"TOCI\" elements contain \"Link\" elements", "",
            com.netralabs.quality.rules.ValidateTociContainLink::new, Phase.DOCUMENT
    ),
    Q_TOCI_LINKED_TO_HEADINGS(
            "Quality", "Quality", "TOC", "\"TOCI\" elements are correctly linked to headings", "",
            com.netralabs.quality.rules.ValidateTociLinkedToHeadings::new, Phase.DOCUMENT
    ),
    Q_ALT_TEXT_VALIDITY(
            "Quality", "Quality", "Alt", "Validity of alternative texts", "",
            com.netralabs.quality.rules.ValidateAltTextValidity::new, Phase.DOCUMENT
    ),
    Q_ALT_ON_TEXT_ELEMENTS(
            "Quality", "Quality", "Alt", "Alternative text on text elements", "",
            com.netralabs.quality.rules.ValidateAltOnTextElements::new, Phase.DOCUMENT
    ),
    Q_LINK_COMPLETENESS(
            "Quality", "Quality", "Structure", "Completeness of \"Link\" elements", "",
            com.netralabs.quality.rules.ValidateLinkCompleteness::new, Phase.DOCUMENT
    ),
    Q_LI_FORMAL_CORRECTNESS(
            "Quality", "Quality", "Structure", "Formal correctness of \"LI\" elements", "",
            com.netralabs.quality.rules.ValidateLiFormalCorrectness::new, Phase.DOCUMENT
    ),
    Q_TABLE_COMPLETENESS(
            "Quality", "Quality", "Structure", "Completeness of \"Table\" elements", "",
            com.netralabs.quality.rules.ValidateTableCompleteness::new, Phase.DOCUMENT
    ),
    Q_NOTE_REFERENCED(
            "Quality", "Quality", "Notes", "\"Note\" elements are referenced", "",
            com.netralabs.quality.rules.ValidateNoteReferenced::new, Phase.DOCUMENT
    ),
    Q_NOTE_CONTAINS_LBL(
            "Quality", "Quality", "Notes", "\"Note\" elements contain \"Lbl\" elements", "",
            com.netralabs.quality.rules.ValidateNoteContainsLbl::new, Phase.DOCUMENT
    ),
    Q_P_CONTAINS_NOTE(
            "Quality", "Quality", "Notes", "\"P\" elements contain \"Note\" elements", "",
            com.netralabs.quality.rules.ValidatePContainsNote::new, Phase.DOCUMENT
    );


    private final String reportName;
    private final String category;
    private final String subCategory;
    private final String group;
    private final String element;
    private final String errorMessage;
    private final Supplier<? extends Rule> factory;
    private final EnumSet<Phase> phases;

    PDFUACheckpoint(
            String reportName,
            String category,
            String subCategory,
            String element,
            String errorMessage,
            Supplier<? extends Rule> f, Phase firstPhase, Phase... more

    ) {
        this(reportName, category, subCategory, null, element, errorMessage, f, firstPhase, more);
    }

    PDFUACheckpoint(
            String reportName,
            String category,
            String subCategory,
            String group,
            String element,
            String errorMessage,
            Supplier<? extends Rule> f, Phase firstPhase, Phase... more

    ) {

        this.reportName = reportName;
        this.category = category;
        this.subCategory = subCategory;
        this.group = group;
        this.element = element;
        this.errorMessage = errorMessage;
        this.factory = f;
        this.phases = (firstPhase == null)
                ? EnumSet.noneOf(Phase.class)
                : EnumSet.of(firstPhase, more);


    }


}
