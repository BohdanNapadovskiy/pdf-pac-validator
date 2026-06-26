package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.NAME_ENTRY_OCCD;

public class ValidateOCConfigName implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        PdfDictionary catalog = pdf.getCatalog().getPdfObject();
        PdfDictionary ocProps = catalog.getAsDictionary(new PdfName("OCProperties"));
        if (ocProps == null) return out; // skip (no optional content)

        // Default config
        PdfDictionary d = ocProps.getAsDictionary(PdfName.D);
        if (d != null) {
            addNameFinding(out, d, "Default OCCD");
        }

        // Additional configs
        PdfArray configs = ocProps.getAsArray(new PdfName("Configs"));
        if (configs != null) {
            for (int i = 0; i < configs.size(); i++) {
                PdfDictionary c = configs.getAsDictionary(i);
                if (c != null) addNameFinding(out, c, "OCCD #" + (i + 1));
            }
        }

        return out;
    }

    private void addNameFinding(List<FindingDTO> out, PdfDictionary occd, String label) {
        PdfString name = occd.getAsString(PdfName.Name);
        if (name == null || name.getValue() == null || name.getValue().isBlank()) {
            out.add(new FindingDTO(Severity.ERROR, NAME_ENTRY_OCCD, 0, null));
        } else {
            out.add(new FindingDTO(Severity.PASSED, NAME_ENTRY_OCCD, 0, null));
        }
    }
}
