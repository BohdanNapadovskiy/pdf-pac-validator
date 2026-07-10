package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.netralabs.report.BBoxDTO;

public interface Hook {
    default void onBeginArtifact() {}
    default void onBeginTaggedMcid(int mcid, PdfDictionary pgDict) {}
    default void onBeginOtherMarked(PdfName tag) {}
    default void onEndMarked() {}

    /** Any visible paint (text, path, image, form). bbox is in PDF user-space (origin bottom-left). */
    default void onPainted(BBoxDTO bbox) {}

    /** Text-show. {@code str} is the raw PdfString from the operand; bbox covers the painted glyphs. */
    default void onShowText(PdfString str, BBoxDTO bbox) {}

    /**
     * Text-show with the full {@link TextRenderInfo} so consumers can inspect the font,
     * decoded glyphs and their Unicode mappings. Called after {@link #onShowText(PdfString, BBoxDTO)}
     * for the same event.
     */
    default void onShowText(TextRenderInfo tri, BBoxDTO bbox) {}
}
