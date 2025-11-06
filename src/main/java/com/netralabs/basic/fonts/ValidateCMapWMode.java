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

import static com.netralabs.domain.PDFUACheckpoint.WMODE_ENTRY_IN_CMAP;

@Slf4j
public class ValidateCMapWMode implements Rule {

    private static final Set<String> PREDEFINED_CMAPS = Set.of(
            "Identity-H","Identity-V",
            "UniJIS-UCS2-H","UniJIS-UCS2-V",
            "UniKS-UCS2-H","UniKS-UCS2-V",
            "UniGB-UCS2-H","UniGB-UCS2-V",
            "KSC-EUC-H","KSC-EUC-V","GB-EUC-H","GB-EUC-V"
    );

    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public boolean supportsRole(PdfName role) {
        return Rule.super.supportsRole(role);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            PdfDictionary resources = pdf.getPage(page).getResources().getPdfObject();
            if (resources == null) continue;
            PdfDictionary fonts = resources.getAsDictionary(PdfName.Font);
            if (fonts == null) continue;

            for (PdfName fname : fonts.keySet()) {
                PdfDictionary font = fonts.getAsDictionary(fname);
                if (font == null) continue;
                if (!PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) {
                    log.debug("Skipping WMode validation: not a Type0 font ({} on page {})", fname, page);
                    out.add(ignore(page, "CMap /WMode=0 (or missing)", fname));
                    continue;
                }

                PdfDictionary cidFont = firstDescendantCidFont(font);
                PdfObject enc = font.get(PdfName.Encoding);
                if (enc == null) {
                    log.debug("Skipping WMode validation: missing /Encoding for font {} on page {}", fname, page);
                    out.add(ignore(page, "CMap /WMode=0 (or missing)", fname));
                    continue;
                }
                if (enc.isName()) {
                    String encName = ((PdfName) enc).getValue();
                    if (!encName.endsWith("-V")) {
                        log.debug("Skipping WMode validation: predefined horizontal CMap '{}' (page {})", encName, page);
                        out.add(ignore(page, "CMap /WMode=0 (or missing)", fname));
                        continue;
                    }
                    if (!hasVerticalMetrics(cidFont)) {
                        out.add(err(page, "Predefined CMap '" + encName +
                                "' implies WMode=1 but CIDFont lacks /DW2 or /W2", fname));
                    } else {
                        out.add(pass(page, "Predefined vertical CMap '" + encName +
                                "' has matching vertical metrics", fname));
                    }
                    continue;
                }
                if (enc.isStream()) {
                    PdfDictionary cmap = (PdfDictionary) enc;
                    Integer wmode = getWMode(cmap); // null → default 0
                    int effective = (wmode == null) ? 0 : wmode;
                    if (wmode == null || effective == 0) {
                        log.debug("Skipping WMode validation: horizontal CMap (WMode=0 or missing) on page {}", page);
                        out.add(ignore(page, "CMap /WMode=0 (or missing)", fname));
                        continue;
                    }
                    if (effective != 1) {
                        out.add(err(page, "/WMode must be 0 or 1; found: " + effective, fname));
                        continue;
                    }
                    if (!hasVerticalMetrics(cidFont)) {
                        out.add(err(page, "CMap /WMode=1 but CIDFont lacks /DW2 or /W2", fname));
                        continue;
                    } else {
                        out.add(pass(page, "CMap /WMode=" + effective + " consistent with CIDFont metrics", fname));
                    }
                    PdfName useCMapName = cmap.getAsName(new PdfName("UseCMap"));
                    if (useCMapName == null) {
                        log.debug("Skipping /UseCMap check: no /UseCMap entry for font {} on page {}", fname, page);
                        out.add(ignore(page, "CMap /WMode=1 but /UseCMap missing", fname));
                        continue;
                    }

                    String ref = useCMapName.getValue();
                    if (PREDEFINED_CMAPS.contains(ref)) {
                        int refExpected = inferWModeFromName(ref);
                        if (refExpected != effective) {
                            out.add(err(page, "/UseCMap '" + ref + "' implies WMode=" + refExpected +
                                    " but current CMap has WMode=" + effective, fname));
                        } else {
                            out.add(pass(page, "/UseCMap '" + ref + "' matches WMode=" + effective, fname));
                        }
                        continue;
                    }
                    PdfDictionary other = findCMapStreamByName(pdf, ref);
                    if (other == null) {
                        log.debug("Skipping /UseCMap check: referenced CMap '{}' not found (handled by other rule)", ref);
                        out.add(ignore(page, "Embedded /UseCMap '" + ref + "' not found", fname));
                        continue;
                    }

                    int otherW = (getWMode(other) == null) ? 0 : getWMode(other);
                    if (otherW != effective) {
                        out.add(err(page, "Embedded /UseCMap '" + ref + "' has WMode=" + otherW +
                                " but current CMap has WMode=" + effective, fname));
                    } else {
                        out.add(pass(page, "Embedded /UseCMap '" + ref + "' matches WMode=" + effective, fname));
                    }
                    continue;
                }
                out.add(err(page, "/Encoding must be a name or a stream CMap", fname));
            }
        }
        return out;
    }


    // ------------------ helpers ------------------

    private static FindingDTO err(int page, String msg, PdfName fontResName) {
        log.error("WMode check failed on page {} (font {}): {}", page, fontResName.getValue(), msg);
        return new FindingDTO(Severity.ERROR, WMODE_ENTRY_IN_CMAP, page, null);
    }

    private FindingDTO ignore(int page, String s, PdfName fname) {
        log.info("WMode check ignored on page {} (font {}): {}", page, fname.getValue(), s);
        return new FindingDTO(Severity.IGNORED, WMODE_ENTRY_IN_CMAP, page, null);
    }


    private static FindingDTO pass(int page, String msg, PdfName fontResName) {
        log.info("WMode check passed on page {} (font {}): {}", page, fontResName.getValue(), msg);
        return new FindingDTO(Severity.PASSED, WMODE_ENTRY_IN_CMAP, page, null);
    }

    private static PdfDictionary firstDescendantCidFont(PdfDictionary type0) {
        PdfArray descendants = type0.getAsArray(PdfName.DescendantFonts);
        if (descendants == null || descendants.isEmpty()) return null;
        PdfObject o = descendants.get(0);
        return (o != null && o.isDictionary()) ? (PdfDictionary) o : null;
    }

    private static boolean hasVerticalMetrics(PdfDictionary cidFont) {
        if (cidFont == null) return false;
        return cidFont.containsKey(new PdfName("DW2")) || cidFont.containsKey(PdfName.W2);
    }

    private static Integer getWMode(PdfDictionary cmapStream) {
        PdfNumber n = cmapStream.getAsNumber(new PdfName("WMode"));
        return n != null ? n.intValue() : null;
    }

    private static int inferWModeFromName(String cmapName) {
        if (cmapName == null) return 0;
        return cmapName.endsWith("-V") ? 1 : 0;
    }

    private static PdfDictionary findCMapStreamByName(PdfDocument pdf, String name) {
        PdfName cmapType = new PdfName("CMap");
        for (int i = 1; i <= pdf.getNumberOfPdfObjects(); i++) {
            PdfObject obj = pdf.getPdfObject(i);
            if (obj != null && obj.isStream()) {
                PdfStream s = (PdfStream) obj;
                if (cmapType.equals(s.getAsName(PdfName.Type))) {
                    PdfName cmapName = s.getAsName(new PdfName("CMapName"));
                    if (cmapName != null && name.equals(cmapName.getValue())) {
                        return s;
                    }
                }
            }
        }
        return null;
    }
}
