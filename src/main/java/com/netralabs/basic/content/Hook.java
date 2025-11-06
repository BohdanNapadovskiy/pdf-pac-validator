package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;

public interface Hook {
    default void onBeginArtifact() {}
    default void onBeginTaggedMcid(int mcid, PdfDictionary pgDict) {}
    default void onBeginOtherMarked(PdfName tag) {}
    default void onEndMarked() {}
    default void onPainted() {}                  // any visible paint (text, path, image/form)
    default void onShowText(PdfString str) {}   // Tj, ', "
    default void onShowTextArray(PdfArray arr) {}
}
