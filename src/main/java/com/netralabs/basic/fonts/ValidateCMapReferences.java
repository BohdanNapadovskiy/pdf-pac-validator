package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.netralabs.basic.fonts.FontUtils.forEachUniqueFont;
import static com.netralabs.domain.PDFUACheckpoint.REFERENCE_CMAP;

@Slf4j
public class ValidateCMapReferences implements Rule {

    private static final Set<String> PREDEFINED_CMAPS = Set.of(
            "Identity-H", "Identity-V", "GB-EUC-H", "GB-EUC-V",
            "UniGB-UCS2-H", "UniGB-UCS2-V", "UniJIS-UCS2-H", "UniJIS-UCS2-V",
            "UniKS-UCS2-H", "UniKS-UCS2-V", "KSC-EUC-H", "KSC-EUC-V"
    );

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) return;
            PdfObject enc = font.get(PdfName.Encoding);
            if (enc == null || !enc.isStream()) return;

            PdfDictionary cmapStream = (PdfDictionary) enc;
            PdfName useCMap = cmapStream.getAsName(new PdfName("UseCMap"));
            if (useCMap == null) return;

            String name = useCMap.getValue();
            if (PREDEFINED_CMAPS.contains(name)) return;
            if (findCMapStreamByName(pdf, name) == null) {
                log.error("CMap references unknown /UseCMap {} (font {} page {})", name, fname, page);
                out.add(new FindingDTO(Severity.ERROR, REFERENCE_CMAP, page, null));
            }
        });
        return out;
    }

    private PdfDictionary findCMapStreamByName(PdfDocument pdf, String name) {
        for (int i = 1; i <= pdf.getNumberOfPdfObjects(); i++) {
            PdfObject obj = pdf.getPdfObject(i);
            if (obj != null && obj.isStream()) {
                PdfStream stream = (PdfStream) obj;
                if (PdfName.CMapName.equals(stream.getAsName(PdfName.Type))) {
                    PdfName cmapName = stream.getAsName(PdfName.CMapName);
                    if (cmapName != null && name.equals(cmapName.getValue())) {
                        return stream;
                    }
                }
            }
        }
        return null;
    }
}
