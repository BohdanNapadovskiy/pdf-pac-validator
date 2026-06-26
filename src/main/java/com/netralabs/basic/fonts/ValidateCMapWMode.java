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
import static com.netralabs.domain.PDFUACheckpoint.WMODE_ENTRY_IN_CMAP;

@Slf4j
public class ValidateCMapWMode implements Rule {

    private static final Set<String> PREDEFINED_CMAPS = Set.of(
            "Identity-H", "Identity-V",
            "UniJIS-UCS2-H", "UniJIS-UCS2-V",
            "UniKS-UCS2-H", "UniKS-UCS2-V",
            "UniGB-UCS2-H", "UniGB-UCS2-V",
            "KSC-EUC-H", "KSC-EUC-V", "GB-EUC-H", "GB-EUC-V"
    );

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) return;
            PdfObject enc = font.get(PdfName.Encoding);
            if (enc == null) return;

            PdfDictionary cidFont = firstDescendantCidFont(font);

            if (enc.isName()) {
                String name = ((PdfName) enc).getValue();
                if (!name.endsWith("-V")) return; // horizontal CMap, nothing to check
                if (!hasVerticalMetrics(cidFont)) {
                    out.add(new FindingDTO(Severity.ERROR, WMODE_ENTRY_IN_CMAP, page, null));
                }
                return;
            }

            if (!enc.isStream()) {
                out.add(new FindingDTO(Severity.ERROR, WMODE_ENTRY_IN_CMAP, page, null));
                return;
            }

            PdfDictionary cmap = (PdfDictionary) enc;
            Integer wmode = getWMode(cmap);
            int effective = (wmode == null) ? 0 : wmode;
            if (effective == 0) return; // horizontal: nothing to validate
            if (effective != 1) {
                out.add(new FindingDTO(Severity.ERROR, WMODE_ENTRY_IN_CMAP, page, null));
                return;
            }
            if (!hasVerticalMetrics(cidFont)) {
                out.add(new FindingDTO(Severity.ERROR, WMODE_ENTRY_IN_CMAP, page, null));
                return;
            }
            PdfName useCMapName = cmap.getAsName(new PdfName("UseCMap"));
            if (useCMapName == null) return;

            String ref = useCMapName.getValue();
            if (PREDEFINED_CMAPS.contains(ref)) {
                int refExpected = ref.endsWith("-V") ? 1 : 0;
                if (refExpected != effective) {
                    out.add(new FindingDTO(Severity.ERROR, WMODE_ENTRY_IN_CMAP, page, null));
                }
                return;
            }
            PdfDictionary other = findCMapStreamByName(pdf, ref);
            if (other == null) return; // missing CMap handled by CMapReferences rule
            int otherW = (getWMode(other) == null) ? 0 : getWMode(other);
            if (otherW != effective) {
                out.add(new FindingDTO(Severity.ERROR, WMODE_ENTRY_IN_CMAP, page, null));
            }
        });
        return out;
    }

    private static PdfDictionary firstDescendantCidFont(PdfDictionary type0) {
        if (type0 == null) return null;
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
