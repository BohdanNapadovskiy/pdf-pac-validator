package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.netralabs.domain.PDFUACheckpoint.AS_ENTRY_OCCD;

public class ValidateOCConfigAS implements Rule {

    private static final Set<String> ALLOWED_EVENTS = Set.of("View", "Print", "Export");

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        PdfDictionary catalog = pdf.getCatalog().getPdfObject();
        PdfDictionary ocProps = catalog.getAsDictionary(new PdfName("OCProperties"));
        if (ocProps == null) return out; // skip

        // Default config
        PdfDictionary d = ocProps.getAsDictionary(PdfName.D);
        checkAS(out, d, "Default OCCD");

        // Additional configs
        PdfArray configs = ocProps.getAsArray(new PdfName("Configs"));
        if (configs != null) {
            for (int i = 0; i < configs.size(); i++) {
                PdfDictionary c = configs.getAsDictionary(i);
                checkAS(out, c, "OCCD #" + (i + 1));
            }
        }
        return out;
    }

    private void checkAS(List<FindingDTO> out, PdfDictionary occd, String label) {
        if (occd == null) return;
        PdfArray asArr = occd.getAsArray(new PdfName("AS"));
        if (asArr == null) {
            out.add(new FindingDTO(Severity.ERROR, AS_ENTRY_OCCD, 0, null));
            return;
        }
        boolean ok = true;
        for (int i = 0; i < asArr.size(); i++) {
            PdfDictionary rule = asArr.getAsDictionary(i);
            if (rule == null) {
                ok = false;
                break;
            }

            // /Event
            PdfName ev = rule.getAsName(new PdfName("Event"));
            if (ev == null || !ALLOWED_EVENTS.contains(ev.getValue())) {
                ok = false;
                break;
            }

            // /Category: array of names
            PdfArray cat = rule.getAsArray(new PdfName("Category"));
            if (cat == null || cat.isEmpty()) {
                ok = false;
                break;
            }
            for (int j = 0; j < cat.size(); j++) {
                if (!cat.get(j).isName()) {
                    ok = false;
                    break;
                }
            }
            if (!ok) break;

            // /OCGs: array or single ref/dict
            PdfObject ocgs = rule.get(new PdfName("OCGs"));
            if (ocgs == null) {
                ok = false;
                break;
            }
            if (ocgs.isArray()) {
                PdfArray arr = (PdfArray) ocgs;
                if (arr.isEmpty()) {
                    ok = false;
                    break;
                }
                // elements can be dicts (OCG) or indrefs; we tolerate both
            } else if (!(ocgs.isDictionary() || ocgs.isIndirectReference())) {
                ok = false;
                break;
            }
        }

        if (ok) {
            out.add(new FindingDTO(Severity.PASSED, AS_ENTRY_OCCD, 0, null));
        } else {
            out.add(new FindingDTO(Severity.ERROR, AS_ENTRY_OCCD, 0, null));
        }
    }
}
