package com.netralabs.domain;

import com.netralabs.Rule;
import com.netralabs.basic.content.*;
import com.netralabs.basic.emebededfiles.ValidateFileSpecFAndUF;
import com.netralabs.basic.fonts.*;
import com.netralabs.basic.naturallanguage.*;
import com.netralabs.basic.pdfsyntax.CorePdfSyntaxCheck;
import com.netralabs.basic.pdfsyntax.ValidateLogicalStructureSyntax;
import com.netralabs.basic.pdfsyntax.ValidateParentsOfStructureElements;
import com.netralabs.basic.pdfsyntax.ValidateStructuralParentTree;
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
            "PDF syntax (ISO 32000-1)",
            "PDF syntax",
            "PDF syntax is not valid",
            CorePdfSyntaxCheck::new,
            Phase.DOCUMENT
    ),
    PARENTS_OF_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Basic requirements",
            "PDF syntax (ISO 32000-1)",
            "Parent of structure elements",
            "",
            ValidateParentsOfStructureElements::new,
            Phase.DOCUMENT
    ),
    LOGICAL_STRUCTURE_SYNTAX(
            "PDF/UA",
            "Basic requirements",
            "PDF syntax (ISO 32000-1)",
            "Logical structure syntax",
            "",
            ValidateLogicalStructureSyntax::new,
            Phase.DOCUMENT
    ),
    STRUCTURE_PARENT_TREE(
            "PDF/UA",
            "Basic requirements",
            "PDF syntax (ISO 32000-1)",
            "Structure parent tree",
            "",
            ValidateStructuralParentTree::new,
            Phase.DOCUMENT
    ),


    REGISTRY_ENTRIES(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Registry entries in Type 0",
            "",
            ValidateCIDSystemInfoRegistry::new,
            Phase.DOCUMENT
    ),
    ORDERING_ENTRIES(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Ordering entries in Type 0",
            "",
            ValidateCIDSystemInfoOrdering::new,
            Phase.DOCUMENT
    ),
    SUPPLEMENT_ENTRIES(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Supplement entries in Type 0",
            "",
            ValidateCIDSystemInfoSupplement::new,
            Phase.DOCUMENT
    ),
    CID_GID_MAPPING(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "CID to GID mapping of Type 2 CID fonts",
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
            "WMode entry in CMap definition and CMap data",
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
            "Fonts embedding",
            "",
            ValidateFontsEmbedding::new,
            Phase.DOCUMENT
    ),
    ENCODING_ENTRY(
            "PDF/UA",
            "Basic requirements",
            "Fonts",
            "Encoding entry is non-symbolic TrueType font",
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
            "Glyph names is non-symbolic TrueType font",
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
            "Artifact inside tagged content",
            "",
            ValidateArtifactsInsideTagged::new,
            Phase.DOCUMENT
    ),
    TAGGED_CONTENT_INSIDE_ARTIFACT(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Tagged content inside artifact",
            "",
            ValidateTaggedInsideArtifacts::new,
            Phase.DOCUMENT
    ),
    MAPPING_OF_CHARACTER_TO_UNICODE(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Mapping of character to Unicode",
            "",
            ValidateUnicodeMapping::new,
            Phase.DOCUMENT
    ),
    REFERENCED_EXTERNAL_OBJECT(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Referenced external object",
            "",
            ValidateReferencedExternalObjects::new,
            null
    ),
    NAME_ENTRY_OCCD(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Optional content",
            "Name entry in OCCDs",
            ValidateOCConfigName::new,
            Phase.DOCUMENT
    ),
    AS_ENTRY_OCCD(
            "PDF/UA",
            "Basic requirements",
            "Content",
            "Optional content",
            "AS entry in OCCDs",
            ValidateOCConfigAS::new,
            Phase.DOCUMENT
    ),

    F_UF_FILE_SPECIFICATION(
            "PDF/UA",
            "Basic requirements",
            "Embedded files",
            "Glyph names is non-symbolic TrueType font",
            "",
            ValidateFileSpecFAndUF::new,
            Phase.DOCUMENT
    ),

    CORRECTNESS_LANGUAGE_ATR(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Correctness of language attribute",
            "Document language metadata contains the syntax error",
            ValidateLangAttributeCorrectness::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_TEXT_OBJECT(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Natural language text object",
            "Natural language cannot be determined",
            ValidateLangOfTextObjects::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_ALTERNATIVE_TEXT(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Natural language of alternative text",
            "Natural language of alternative text cannot be determined",
            ValidateLangOfAltText::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_ACTUAL_TEXT(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Natural language of actual text",
            "Natural language of actual text cannot be determined",
            ValidateLangOfActualText::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_EXPANSION_TEXT(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Natural language of expansion text",
            "Natural language of expansion text cannot be determined",
            ValidateLangOfExpansionText::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_BOOKMARK(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Natural language of bookmark",
            "Natural language of bookmark cannot be determined",
            ValidateLangOfBookmarks::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_CONTENTS(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Natural language of 'Contents' entries in annotation",
            "",
            ValidateLangOfAnnotationContents::new,
            Phase.DOCUMENT
    ),
    NATURAL_LANGUAGE_ALTERNATE_NAMES_FORM_FIELD(
            "PDF/UA",
            "Basic requirements",
            "Natural Language",
            "Natural language of alternate names in form field",
            "",
            ValidateLangOfFormFieldAltNames::new,
            Phase.DOCUMENT
    ),


    USE_OF_EITHER(
            "PDF/UA",
            "Logical Structure",
            "Headings",
            "Use of either 'H' or Hn structure elements",
            "",
            ValidateUseOfEitherHOrHn::new,
            Phase.DOCUMENT
    ),
    FIRST_HEADING_LEVEL(
            "PDF/UA",
            "Logical Structure",
            "Headings",
            "First heading level",
            "",
            ValidateFirstHeadingLevel::new,
            Phase.DOCUMENT
    ),
    NESTING_HEADING_LEVEL(
            "PDF/UA",
            "Logical Structure",
            "Headings",
            "Nesting of heading levels",
            "",
            ValidateNestingOfHeadingLevels::new,
            Phase.DOCUMENT
    ),
    H_STRUCTURE_ELEMENTS_WITHIN(
            "PDF/UA",
            "Logical Structure",
            "Headings",
            "'H' structure elements within a structure node",
            "",
            ValidateHeadingInsideStructureNode::new,
            Phase.DOCUMENT
    ),
    ID_NOTE(
            "PDF/UA",
            "Logical Structure",
            "Notes",
            "IDs of 'Note' structure element",
            "ID missing in Note structure element",
            ValidateNoteIdPresence::new,
            Phase.DOCUMENT
    ),
    UNIQUE_ID_ENTRIES(
            "PDF/UA",
            "Logical Structure",
            "Notes",
            "Unique 'ID' entries in Note structure element",
            "",
            ValidateNoteIdUniqueness::new,
            Phase.DOCUMENT
    ),
    TRAP_NET_ANNOTATIONS(
            "PDF/UA",
            "Logical Structure",
            "Annotation",
            "'TrapNet' annotations",
            "",
            null,
            null
    ),
    NESTING_WIDGET_ANNOTATIONS(
            "PDF/UA",
            "Logical Structure",
            "Annotation",
            "Nesting of 'Widget' annotations inside a 'Form' structure elements",
            "",
            null,
            null
    ),
    NESTING_LINK_ANNOTATIONS(
            "PDF/UA",
            "Logical Structure",
            "Annotation",
            "Nesting of 'Link' annotations inside 'Link' structure elements ",
            "",
            null,
            null
    ),
    NESTING_ANNOTATIONS_ANNOT(
            "PDF/UA",
            "Logical Structure",
            "Annotation",
            "Nesting of annotations in Annot structure elements",
            "",
            null,
            null
    ),
    PRINTER_MARK_ANNOTATIONS(
            "PDF/UA", "Logical Structure", "Annotation", "'PrinterMark' annotations", "",
            null,
            null
    ),
    BOUNDED_BOXES(
            "PDF/UA", "Logical Structure", "Figure", "Bounding boxes", "",
            null,
            null
    ),
    TABLE_REGULARITY(
            "PDF/UA", "Logical Structure", "Tables", "Table regularity", "Irregular table row",
            null,
            null
    ),
    TABLE_HEADER_CELL_ASSIGNMENTS(
            "PDF/UA",
            "Logical Structure",
            "Tables",
            "Tables header cell assignments",
            "Table Header Cell Has No Associated Sub Cells",
            null,
            null
    ),


    DOCUMENT_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "'Document' structure elements", "",
            null,
            null
    ),
    PART_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "'Part' structure elements", "",
            null,
            null
    ),
    ART_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "'Art' structure elements", "",
            null,
            null
    ),
    SECT_STRUCTURE_ELEMENT(
            "PDF/UA", "Logical Structure", "Structure tree", "'Sect' structure elements", "",
            null,
            null
    ),
    DIV_STRUCTURE_ELEMENT(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Div' structure elements",
            "",
            null,
            null
    ),
    BLOCKQUOTE_STRUCTURE_ELEMENT(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'BlockQuote' structure elements",
            "",
            null,
            null
    ),
    CAPTION_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Caption' structure elements",
            "",
            null,
            null
    ),
    TOC_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'TOC' structure elements",
            "",
            null,
            null
    ),
    TOCI_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'TOCI' structure elements",
            "",
            null,
            null
    ),
    INDEX_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "Index structure elements",
            "",
            null,
            null
    ),
    PRIVATE_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Private' structure elements",
            "",
            null,
            null
    ),
    H_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'H' structure elements",
            "",
            null,
            null
    ),
    H1_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'H1' structure elements",
            "",
            null,
            null
    ),
    H2_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'H2' structure elements",
            "",
            null,
            null
    ),
    H3_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'H3' structure elements",
            "",
            null,
            null
    ),
    H4_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'H4' structure elements",
            "",
            null,
            null
    ),
    H5_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'H5' structure elements",
            "",
            null,
            null
    ),
    H6_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure", "Structure tree", "'H6' structure elements",
            "",
            null,
            null
    ),
    P_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'P' structure elements",
            "",
            null,
            null
    ),
    L_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'L' structure elements",
            "",
            null,
            null
    ),
    LI_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'LI' structure elements",
            "",
            null,
            null
    ),
    Lbl_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Lbl' structure elements",
            "",
            null,
            null
    ),
    LBODY_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'LBody' structure elements",
            "",
            null,
            null
    ),
    TABLE_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Table' structure elements",
            "",
            null,
            null
    ),
    TR_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'TR' structure elements",
            "",
            null,
            null
    ),
    TH_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'TH' structure elements",
            "",
            null,
            null
    ),
    TD_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'TD' structure elements",
            "",
            null,
            null
    ),
    THEAD_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'THead' structure elements",
            "",
            null,
            null
    ),
    TBODY_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'TBody' structure elements",
            "",
            null,
            null
    ),
    TFOOT_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'TFoot' structure elements",
            "",
            null,
            null
    ),
    SPAN_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Span' structure elements",
            "",
            null,
            null
    ),
    QUOTE_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Quote' structure elements",
            "",
            null,
            null
    ),
    NOTE_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Note' structure elements",
            "",
            null,
            null
    ),
    REFERENCE_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Reference' structure elements",
            "",
            null,
            null
    ),
    BIBENTRY_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'BibEntry' structure elements'",
            "",
            null,
            null

    ),
    CODE_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Code' structure elements",
            "",
            null,
            null

    ),
    LINK_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Link' structure elements",
            "",
            null,
            null

    ),
    ANNOT_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Annot' structure elements",
            "",
            null,
            null

    ),
    RUBY_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Ruby' structure elements",
            "",
            null,
            null

    ),
    RB_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'RB' structure elements",
            "",
            null,
            null

    ),
    RT_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'RT' structure elements",
            "",
            null,
            null

    ),
    RP_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'RP' structure elements",
            "",
            null,
            null
    ),
    WARICHU_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Warichu' structure elements",
            "",
            null,
            null

    ),
    WP_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'WP' structure elements",
            "",
            null,
            null

    ),
    WT_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'WT' structure elements",
            "",
            null,
            null
    ),
    FIGURE_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Figure' structure elements",
            "",
            null,
            null
    ),
    FORMULA_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Formula' structure elements",
            "",
            null,
            null
    ),
    FORM_STRUCTURE_ELEMENTS(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "'Form' structure elements",
            "",
            null,
            null
    ),
    CONTENT_PRESENT(
            "PDF/UA",
            "Logical Structure",
            "Structure tree",
            "Content is present in admissible location",
            "",
            null,
            null
    ),

    ROLE_MAPPING_FOR_STANDARD_STRUCTURE(
            "PDF/UA",
            "Logical Structure",
            "Role mapping",
            "Role mapping for standard structure elements",
            "",
            null,
            null
    ),
    ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE(
            "PDF/UA",
            "Logical Structure",
            "Role mapping",
            "Role mapping for non-standard structure elements",
            "",
            null,
            null
    ),
    CIRCULAR_ROLE_MAPPING(
            "PDF/UA", "Logical Structure", "Role mapping", "Circular role mapping", "",
            null,
            null
    ),

    ALTERNATIVE_TEXT_FOR_FIGURE(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternative Descriptions for 'Figure' structure elements",
            "",
            null,
            null
//      AltTextForFigureRule::new,
//      Phase.STRUCT

    ),
    ALTERNATIVE_TEXT_FOR_FORMULA(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternative Descriptions for 'Formula' structure elements",
            "",
            null,
            null

    ),
    ALTERNATIVE_NAMES_FORM_FIELDS(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternative Descriptions for form fields",
            "",
            null,
            null
    ),
    ALTERNATIVE_DESCRIPTION_FOR_ANNOT(
            "PDF/UA",
            "Logical Structure",
            "Alternative Descriptions",
            "Alternative Descriptions for annotations",
            "",
            null,
            null
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
            "Security settings and documents access by assistive technologies",
            "",
            SecuritySettingIdentifier::new,
            Phase.DOCUMENT
    ),

    // page-level example (runs once per page that has annotations)
    TAB_ORDER_PAGES(
            "PDF/UA",
            "Metadata and Settings",
            "Document settings",
            "Tab order fro pages with annotations",
            "",
            TabOrderByPageIdentifier::new,
            Phase.PAGE

    );


    private final String reportName;
    private final String category;
    private final String subCategory;
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

        this.reportName = reportName;
        this.category = category;
        this.subCategory = subCategory;
        this.element = element;
        this.errorMessage = errorMessage;
        this.factory = f;
        this.phases = (firstPhase == null)
                ? EnumSet.noneOf(Phase.class)
                : EnumSet.of(firstPhase, more);


    }


}
