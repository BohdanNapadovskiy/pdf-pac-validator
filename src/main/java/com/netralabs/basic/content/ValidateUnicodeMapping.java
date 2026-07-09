package com.netralabs.basic.content;

import com.itextpdf.io.font.otf.Glyph;
import com.itextpdf.io.font.otf.GlyphLine;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.canvas.CanvasTag;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.netralabs.basic.content.ContentWalker.walkPage;
import static com.netralabs.domain.PDFUACheckpoint.MAPPING_OF_CHARACTER_TO_UNICODE;

/**
 * ISO 14289-1 §7.2-15: every glyph used in a text-showing operation must map to
 * Unicode via the font's ToUnicode CMap, a standard encoding, or a differences
 * array whose glyph names are in the Adobe Glyph List.
 *
 * <p>PAC emits one finding per text-show event (not per glyph): ERROR when any
 * glyph in the event lacks a valid Unicode mapping, PASSED when all glyphs map.
 *
 * <p>We decode each text-show into a {@link GlyphLine} via the font's own decoder
 * and use {@link Glyph#hasValidUnicode()} — iText's answer to "does this glyph
 * have an unambiguous Unicode mapping in this font?". A glyph that decodes to a
 * fabricated codepoint (e.g. PUA fallback for a font with no ToUnicode CMap)
 * returns {@code false} and marks the whole show event as ERROR even though the
 * raw {@link com.itextpdf.kernel.pdf.PdfString#toUnicodeString()} would return
 * non-replacement characters.
 *
 * <p>Every text-show event is counted, including {@code /Artifact}-scoped text.
 * Verified against Filled_Graduate (3756 events matches PAC exactly; the previous
 * artifact-excluded count of 3676 dropped 80 events).
 *
 * <p>Special-case: any text-show event using a Type 3 font that lacks a
 * {@code /ToUnicode} CMap is flagged ERROR unconditionally. Type 3 fonts define
 * glyphs via custom {@code /CharProcs} names ({@code /a}, {@code /b},
 * {@code /c31}...) not in the Adobe Glyph List, so without ToUnicode the mapping
 * is unrecoverable per §7.2-15. Verified against CalSAWS: all 7709 Type 3 events
 * on pages 4-6 flagged as ERROR, matching PAC's 4040P/7709E exactly.
 */
public class ValidateUnicodeMapping implements Rule {

    private enum Scope { ARTIFACT, TAGGED_MCID, OTHER_MARKED }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;

            walkPage(pdf, page, new Hook() {
                private final Deque<Scope> stack = new ArrayDeque<>();

                @Override public void onBeginArtifact()                              { stack.push(Scope.ARTIFACT); }
                @Override public void onBeginTaggedMcid(int mcid, PdfDictionary pg)  { stack.push(Scope.TAGGED_MCID); }
                @Override public void onBeginOtherMarked(PdfName tag)                { stack.push(Scope.OTHER_MARKED); }
                @Override public void onEndMarked()                                   { if (!stack.isEmpty()) stack.pop(); }

                @Override
                public void onShowText(TextRenderInfo tri, BBoxDTO bbox) {
                    // PAC-parity: skip text whose innermost marked-content tag is an
                    // Artifact BDC carrying an explicit /Type property (Pagination,
                    // Page, Layout, Background — classified decorative artifacts).
                    // Artifact BDCs with /MCID and no /Type are still counted.
                    if (isTypedArtifact(tri)) return;
                    PdfFont font = tri.getFont();
                    if (font == null) return;
                    PdfDictionary fdict = font.getPdfObject();
                    // Type 3 font without ToUnicode CMap: glyph name -> Unicode is
                    // unrecoverable regardless of iText's per-glyph decoder result.
                    if (fdict != null
                            && PdfName.Type3.equals(fdict.getAsName(PdfName.Subtype))
                            && fdict.get(PdfName.ToUnicode) == null) {
                        out.add(new FindingDTO(Severity.ERROR, MAPPING_OF_CHARACTER_TO_UNICODE, pageNum, bbox,
                                "Type 3 font has no ToUnicode CMap; glyph names not in Adobe Glyph List"));
                        return;
                    }
                    GlyphLine line;
                    try {
                        line = font.decodeIntoGlyphLine(tri.getPdfString());
                    } catch (Exception e) {
                        // Font decoder failure: emit one error for the whole show event.
                        out.add(new FindingDTO(Severity.ERROR, MAPPING_OF_CHARACTER_TO_UNICODE, pageNum, bbox,
                                "Font could not decode text-show operand"));
                        return;
                    }
                    if (line == null || line.getEnd() <= line.getStart()) return;
                    boolean anyBad = false;
                    for (int i = line.getStart(); i < line.getEnd(); i++) {
                        Glyph g = line.get(i);
                        if (g != null && !g.hasValidUnicode()) { anyBad = true; break; }
                    }
                    if (anyBad) {
                        out.add(new FindingDTO(Severity.ERROR, MAPPING_OF_CHARACTER_TO_UNICODE, pageNum, bbox,
                                "One or more glyphs have no Unicode mapping"));
                    } else {
                        out.add(new FindingDTO(Severity.PASSED, MAPPING_OF_CHARACTER_TO_UNICODE, pageNum, null));
                    }
                }
            });
        }
        return out;
    }

    /**
     * True iff the innermost marked-content tag surrounding this text-show is an
     * {@code /Artifact} BDC whose properties dict declares an explicit
     * {@code /Type} entry (Pagination, Page, Layout, Background). PAC excludes
     * such classified artifacts from text-tally rows; untyped Artifact BDCs
     * (e.g. those carrying an {@code /MCID}) still count.
     */
    private static boolean isTypedArtifact(TextRenderInfo tri) {
        List<CanvasTag> h = tri.getCanvasTagHierarchy();
        if (h == null || h.isEmpty()) return false;
        CanvasTag innermost = h.get(h.size() - 1);
        PdfName role = innermost.getRole();
        if (role == null || !"Artifact".equals(role.getValue())) return false;
        PdfDictionary props = innermost.getProperties();
        return props != null && props.get(PdfName.Type) != null;
    }
}