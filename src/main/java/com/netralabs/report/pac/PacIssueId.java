package com.netralabs.report.pac;

import com.netralabs.domain.PDFUACheckpoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PAC-canonical {@code issueId} strings for finding-emitting checkpoints.
 * <p>
 * PAC groups failing findings under a per-checkpoint issue code with a
 * short human-readable caption (e.g. {@code WidgetAnnotationsInFormTag-NotUsed}
 * → "\"Widget\" annotation not nested inside a \"Form\" structure element").
 * Only a handful of issue codes appear in PAC's reference export; for
 * checkpoints without a known PAC issueId we synthesise
 * {@code <checkId>-Issue} and fall back to the checkpoint's errorMessage
 * (or a generic caption) so the finding is still uniquely addressable.
 */
public final class PacIssueId {

    private PacIssueId() {}

    public record IssueCode(String id, String caption) {}

    private static final Map<PDFUACheckpoint, IssueCode> KNOWN = new LinkedHashMap<>();
    static {
        KNOWN.put(PDFUACheckpoint.STRUCTURE_PARENT_TREE, new IssueCode(
                "StructuralParentTree-InconsistentEntry", "Inconsistent entry found"));
        KNOWN.put(PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS, new IssueCode(
                "WidgetAnnotationsInFormTag-NotUsed",
                "\"Widget\" annotation not nested inside a \"Form\" structure element"));
        KNOWN.put(PDFUACheckpoint.NESTING_LINK_ANNOTATIONS, new IssueCode(
                "LinkAnnotationsInLinkTag-NotUsed",
                "\"Link\" annotation is not nested inside a \"Link\" structure element"));
        KNOWN.put(PDFUACheckpoint.TABLE_REGULARITY, new IssueCode(
                "TablesAreRegular-IrregularRow", "Irregular table row"));
        KNOWN.put(PDFUACheckpoint.CONTRAST_OF_TEXT, new IssueCode(
                "TextContentHasValidContrast-NotEnoughContrast", "Text with insufficient contrast"));
        // FigureTag has a PAC issue code for a specific inappropriate-use warning;
        // native rules for figure structure emit their own findings, so we route them
        // through the same code when the message matches.
        KNOWN.put(PDFUACheckpoint.FIGURE_STRUCTURE_ELEMENTS, new IssueCode(
                "FigureTag-PossibleInappropriateUseParagraph",
                "Possibly inappropriate use of a \"Figure\" structure element"));
        KNOWN.put(PDFUACheckpoint.FONT_EMBEDDING, new IssueCode(
                "FontsAreEmbedded-FontNotEmbedded", "Font not embedded"));
        KNOWN.put(PDFUACheckpoint.ALTERNATIVE_DESCRIPTION_FOR_ANNOT, new IssueCode(
                "AnnotationHasAltText-ContentsIsWhiteSpace",
                "Annotation contents is white space"));
    }

    /**
     * PAC-known issue code for a checkpoint, or a synthetic {@code <checkId>-Issue}
     * with a fallback caption if PAC does not define one.
     */
    public static IssueCode forCheckpoint(PDFUACheckpoint cp, String fallbackCaption) {
        IssueCode known = KNOWN.get(cp);
        if (known != null) return known;
        String checkId = PacCheckId.forLeaf(cp);
        String caption = (fallbackCaption == null || fallbackCaption.isEmpty())
                ? cp.getElement()
                : fallbackCaption;
        return new IssueCode(checkId + "-Issue", caption);
    }
}
