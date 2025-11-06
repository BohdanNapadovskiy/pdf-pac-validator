package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.netralabs.domain.PDFUACheckpoint.REFERENCE_CMAP;

@Slf4j
public class ValidateCMapReferences implements Rule {
    private static final Set<String> PREDEFINED_CMAPS = Set.of(
            "Identity-H","Identity-V","GB-EUC-H","GB-EUC-V",
            "UniGB-UCS2-H","UniGB-UCS2-V","UniJIS-UCS2-H","UniJIS-UCS2-V",
            "UniKS-UCS2-H","UniKS-UCS2-V","KSC-EUC-H","KSC-EUC-V"
    );

    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        int totalPages = pdf.getNumberOfPages();
        for (int page = 1; page <= totalPages; page++) {
            PdfDictionary resources = pdf.getPage(page).getResources().getPdfObject();
            if (resources == null) continue;

            PdfDictionary fonts = resources.getAsDictionary(PdfName.Font);
            if (fonts == null) continue;

            for (PdfName fname : fonts.keySet()) {
                PdfDictionary font = fonts.getAsDictionary(fname);
                if (font == null) continue;
                PdfName subtype = font.getAsName(PdfName.Subtype);
                if (!PdfName.Type0.equals(subtype)) continue; // only Type 0 fonts have CMaps

                PdfObject enc = font.get(PdfName.Encoding);
                if (enc == null) continue;

                if (enc.isStream()) {
                    PdfDictionary cmapStream = (PdfDictionary) enc;
                    PdfName useCMap = cmapStream.getAsName(new PdfName("UseCMap"));

                    if (useCMap != null) {
                        String name = useCMap.getValue();
                        if (!PREDEFINED_CMAPS.contains(name)) {
                            // Verify that another embedded CMap stream with same name exists
                            PdfDictionary found = findCMapStreamByName(pdf, name);
                            if (found == null) {
                                log.error("CMap stream references unknown /UseCMap {} in page {}", name, page);
                                out.add(new FindingDTO(Severity.ERROR, REFERENCE_CMAP, page,
                                        null));
                            } else {
                                log.info("CMap /UseCMap {} found as embedded stream", name);
                            }
                        } else {
                            log.info("CMap /UseCMap {} is predefined", name);
                        }
                    }
                } else {
                    out.add(new FindingDTO(Severity.IGNORED, REFERENCE_CMAP, -1, null));
                }
            }
        }
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
