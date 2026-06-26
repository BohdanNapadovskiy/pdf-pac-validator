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

import static com.netralabs.basic.fonts.FontUtils.firstDescendantCidFont;
import static com.netralabs.basic.fonts.FontUtils.forEachUniqueFont;
import static com.netralabs.domain.PDFUACheckpoint.FONT_EMBEDDING;

@Slf4j
public class ValidateFontsEmbedding implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
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
