package com.netralabs.quality;

import com.netralabs.domain.PDFUACheckpoint;
import lombok.Getter;

import java.util.List;

/**
 * PAC "Quality" section — flat list of heuristic checks that sit alongside
 * PDF/UA and WCAG. Labels are PAC-canonical (verified against the 16-row
 * reference screenshot).
 * <p>
 * Each constant declares its source {@link PDFUACheckpoint}(s); findings on
 * those checkpoints roll up into the Quality leaf via the same status policy
 * as {@code WCAGReportBuilder.evaluateLeaf}.
 * <p>
 * Sources with {@code reportName="Quality"} are dedicated to this report;
 * cross-taxonomy sources (e.g. {@code TABLE_REGULARITY}, {@code H_STRUCTURE_ELEMENTS})
 * feed both PDF/UA and Quality — same finding, both trees.
 */
@Getter
public enum QualityCriterion {

    DOC_TITLE_VALIDITY(
            "Validity of document title",
            PDFUACheckpoint.Q_DOC_TITLE_VALIDITY),
    ARTIFACTED_ON_BODY(
            "Artifacted content on page body",
            PDFUACheckpoint.Q_ARTIFACTED_ON_BODY),
    TAGGED_TEXT_WHITESPACE(
            "Tagged text consists of only whitespace",
            PDFUACheckpoint.Q_TAGGED_TEXT_WHITESPACE),
    TAGGED_OUTSIDE_PAGE(
            "Tagged content exists outside of the page boundary",
            PDFUACheckpoint.Q_TAGGED_OUTSIDE_PAGE),
    PRESENCE_HEADINGS(
            "Presence of headings",
            PDFUACheckpoint.Q_PRESENCE_HEADINGS),
    PRESENCE_BOOKMARKS(
            "Presence of bookmarks (document outline) if there are headings",
            PDFUACheckpoint.Q_PRESENCE_BOOKMARKS),
    TOCI_CONTAIN_LINK(
            "\"TOCI\" elements contain \"Link\" elements",
            PDFUACheckpoint.Q_TOCI_CONTAIN_LINK),
    TOCI_LINKED_TO_HEADINGS(
            "\"TOCI\" elements are correctly linked to headings",
            PDFUACheckpoint.Q_TOCI_LINKED_TO_HEADINGS),
    ALT_TEXT_VALIDITY(
            "Validity of alternative texts",
            PDFUACheckpoint.Q_ALT_TEXT_VALIDITY),
    ALT_ON_TEXT_ELEMENTS(
            "Alternative text on text elements",
            PDFUACheckpoint.Q_ALT_ON_TEXT_ELEMENTS),
    LINK_COMPLETENESS(
            "Completeness of \"Link\" elements",
            PDFUACheckpoint.Q_LINK_COMPLETENESS),
    LI_FORMAL_CORRECTNESS(
            "Formal correctness of \"LI\" elements",
            PDFUACheckpoint.Q_LI_FORMAL_CORRECTNESS),
    TABLE_COMPLETENESS(
            "Completeness of \"Table\" elements",
            PDFUACheckpoint.Q_TABLE_COMPLETENESS),
    NOTE_REFERENCED(
            "\"Note\" elements are referenced",
            PDFUACheckpoint.Q_NOTE_REFERENCED),
    NOTE_CONTAINS_LBL(
            "\"Note\" elements contain \"Lbl\" elements",
            PDFUACheckpoint.Q_NOTE_CONTAINS_LBL),
    P_CONTAINS_NOTE(
            "\"P\" elements contain \"Note\" elements",
            PDFUACheckpoint.Q_P_CONTAINS_NOTE);

    private final String leaf;
    private final List<PDFUACheckpoint> sources;

    QualityCriterion(String leaf, PDFUACheckpoint... sources) {
        this.leaf = leaf;
        this.sources = List.of(sources);
    }
}