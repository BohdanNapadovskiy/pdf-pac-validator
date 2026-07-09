package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.netralabs.basic.fonts.FontUtils.firstDescendantCidFont;
import static com.netralabs.basic.fonts.FontUtils.forEachUniqueFont;
import static com.netralabs.domain.PDFUACheckpoint.FONT_EMBEDDING;

@Slf4j
public class ValidateFontsEmbedding implements Rule {

    /**
     * The fourteen standard Latin Type 1 fonts (ISO 32000-1 §9.6.2.2 / Table 137).
     * PDF viewers provide these fonts natively, so they need not be embedded and
     * PAC treats them as N/A on the "Font embedding" row.
     */
    private static final Set<String> STANDARD_14 = Set.of(
            "Times-Roman", "Times-Bold", "Times-Italic", "Times-BoldItalic",
            "Helvetica", "Helvetica-Bold", "Helvetica-Oblique", "Helvetica-BoldOblique",
            "Courier", "Courier-Bold", "Courier-Oblique", "Courier-BoldOblique",
            "Symbol", "ZapfDingbats"
    );

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (isStandard14(font)) {
                log.debug("Font {} is Standard 14, skipping embedding check", fname.getValue());
                return;
            }
            // Type 3 fonts define glyphs inline via /CharProcs procedures — they
            // are self-embedded by construction and carry no separate FontFile.
            if (PdfName.Type3.equals(font.getAsName(PdfName.Subtype))) {
                log.info("Font {} is Type3 (self-embedded via CharProcs, first seen on page {})",
                        fname.getValue(), page);
                out.add(new FindingDTO(Severity.PASSED, FONT_EMBEDDING, page, null));
                return;
            }
            PdfDictionary fd = fontDescriptorOf(font);
            if (isEmbedded(fd)) {
                log.info("Font {} embedded (first seen on page {})", fname.getValue(), page);
                out.add(new FindingDTO(Severity.PASSED, FONT_EMBEDDING, page, null));
            } else {
                log.error("Font {} not embedded (first seen on page {})", fname.getValue(), page);
                out.add(new FindingDTO(Severity.ERROR, FONT_EMBEDDING, page, null, "Font not embedded"));
            }
        });
        return out;
    }

    private static boolean isStandard14(PdfDictionary font) {
        PdfName base = font.getAsName(PdfName.BaseFont);
        if (base == null) return false;
        // Strip subset prefix "AAAAAA+" if any — Standard 14 aren't normally subset,
        // but tolerate the case defensively.
        String name = base.getValue();
        int plus = name.indexOf('+');
        if (plus >= 0 && plus == 6) name = name.substring(plus + 1);
        return STANDARD_14.contains(name);
    }

    /**
     * For Type 0 fonts the FontDescriptor lives on the descendant CIDFont, not the outer
     * Type 0 wrapper. Look there first; fall back to the wrapper's FontDescriptor.
     */
    private static PdfDictionary fontDescriptorOf(PdfDictionary font) {
        if (PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) {
            PdfDictionary cid = firstDescendantCidFont(font);
            if (cid != null) {
                PdfDictionary fd = cid.getAsDictionary(PdfName.FontDescriptor);
                if (fd != null) return fd;
            }
        }
        return font.getAsDictionary(PdfName.FontDescriptor);
    }

    private static boolean isEmbedded(PdfDictionary fd) {
        if (fd == null) return false;
        return fd.containsKey(PdfName.FontFile)
                || fd.containsKey(PdfName.FontFile2)
                || fd.containsKey(PdfName.FontFile3);
    }
}
