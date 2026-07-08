package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.AS_ENTRY_OCCD;

/**
 * ISO 14289-1:2014 §7.11-1 — Optional Content Configuration Dictionaries shall
 * <em>not</em> contain the {@code /AS} entry.
 *
 * <p>Absence is the compliant state and yields no finding (leaf rolls up to
 * NA), matching PAC. Presence of {@code /AS} → ERROR.
 */
public class ValidateOCConfigAS implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        PdfDictionary catalog = pdf.getCatalog().getPdfObject();
        PdfDictionary ocProps = catalog.getAsDictionary(new PdfName("OCProperties"));
        if (ocProps == null) return out;

        PdfDictionary d = ocProps.getAsDictionary(PdfName.D);
        if (d != null) checkAS(out, d);

        PdfArray configs = ocProps.getAsArray(new PdfName("Configs"));
        if (configs != null) {
            for (int i = 0; i < configs.size(); i++) {
                PdfDictionary c = configs.getAsDictionary(i);
                if (c != null) checkAS(out, c);
            }
        }
        return out;
    }

    private static void checkAS(List<FindingDTO> out, PdfDictionary occd) {
        if (occd.get(new PdfName("AS")) == null) return; // compliant → no finding (NA)
        out.add(new FindingDTO(Severity.ERROR, AS_ENTRY_OCCD, 0, null));
    }
}