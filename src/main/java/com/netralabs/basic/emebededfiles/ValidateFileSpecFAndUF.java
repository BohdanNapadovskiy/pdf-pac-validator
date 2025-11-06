package com.netralabs.basic.emebededfiles;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static com.netralabs.basic.emebededfiles.FileSpecUtils.collectAllFileSpecs;
import static com.netralabs.domain.PDFUACheckpoint.F_UF_FILE_SPECIFICATION;

public class ValidateFileSpecFAndUF implements Rule {

    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        List<PdfDictionary> fileSpecs = collectAllFileSpecs(pdf);
        if (fileSpecs.isEmpty()) {
            out.add(new FindingDTO(Severity.IGNORED, F_UF_FILE_SPECIFICATION, 0, null));
            return out;
        }

        int ok = 0, missingF = 0, missingUF = 0, empty = 0, mismatch = 0;

        for (PdfDictionary fs : fileSpecs) {
            PdfString F = fs.getAsString(PdfName.F);
            PdfString UF = fs.getAsString(new PdfName("UF"));

            boolean hasF = F != null && !F.getValue().isBlank();
            boolean hasUF = UF != null && !UF.getValue().isBlank();

            if (!hasF) missingF++;
            if (!hasUF) missingUF++;

            if (hasF && hasUF) {
                String fAscii = F.getValue();
                String ufUni = UF.getValue();

                if (!equalsFileNameLoose(fAscii, ufUni)) mismatch++;
                else ok++;
            } else if (hasF || hasUF) {
                empty++;
            }
        }

        if (ok > 0) {
            out.add(new FindingDTO(Severity.PASSED, F_UF_FILE_SPECIFICATION, 0, null));
        }
        if (missingF > 0 || missingUF > 0 || empty > 0 || mismatch > 0) {
            out.add(new FindingDTO(Severity.ERROR, F_UF_FILE_SPECIFICATION, 0, null));
        }
        return out;
    }

    private static boolean equalsFileNameLoose(String f, String uf) {
        String bf = baseName(f).toLowerCase(Locale.ROOT);
        String bu = baseName(uf).toLowerCase(Locale.ROOT);
        return bf.equals(bu);
    }

    private static String baseName(String path) {
        if (path == null) return "";
        String s = path.replace('\\', '/'); // normalize separators
        int idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }
}
