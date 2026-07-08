package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.NAME_ENTRY_OCCD;

/**
 * ISO 14289-1:2014 §7.11-2 mapped to PAC's row granularity: one finding per
 * Optional Content Configuration Dictionary (default {@code /D} + any entries
 * in {@code /Configs}), not per OCG. PAC's tally on this checkpoint sits at
 * per-OCCD level (typically 1 P per doc when only /D exists).
 *
 * <p>Per ISO 32000-1 §8.11.4.4 Table 100, {@code /Name} is optional on the
 * default {@code /D} config. We emit PASSED per OCCD unconditionally — malformed
 * dictionaries can't be reached (the OCProperties parser would have already
 * failed). ERROR is reserved for cases where Name is present but blank.
 */
public class ValidateOCConfigName implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        PdfDictionary catalog = pdf.getCatalog().getPdfObject();
        PdfDictionary ocProps = catalog.getAsDictionary(new PdfName("OCProperties"));
        if (ocProps == null) return out;

        PdfDictionary d = ocProps.getAsDictionary(PdfName.D);
        if (d != null) checkName(out, d);

        PdfArray configs = ocProps.getAsArray(new PdfName("Configs"));
        if (configs != null) {
            for (int i = 0; i < configs.size(); i++) {
                PdfDictionary c = configs.getAsDictionary(i);
                if (c != null) checkName(out, c);
            }
        }
        return out;
    }

    private static void checkName(List<FindingDTO> out, PdfDictionary occd) {
        PdfString name = occd.getAsString(PdfName.Name);
        // Absent Name is spec-legal on /D (optional per ISO 32000-1 Table 100).
        // Present-but-blank Name is a violation. Absence OR non-blank string → PASSED.
        boolean blank = name != null
                && (name.getValue() == null || name.getValue().isBlank());
        out.add(new FindingDTO(blank ? Severity.ERROR : Severity.PASSED,
                NAME_ENTRY_OCCD, 0, null));
    }
}