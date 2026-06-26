package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.netralabs.basic.fonts.FontUtils.encodingOf;
import static com.netralabs.basic.fonts.FontUtils.forEachUniqueFont;
import static com.netralabs.basic.fonts.FontUtils.isPredefinedOrEmbeddedCMap;
import static com.netralabs.domain.PDFUACheckpoint.PREDEFINED_CMAPS;

public class ValidatePredefinedOrEmbeddedCMaps implements Rule {

    private static final PDFUACheckpoint CHECKPOINT = PREDEFINED_CMAPS;

    /** Predefined CMap names per ISO 32000-1, 9.7.5.2, Table 118. */
    private static final Set<String> PREDEFINED_CMAP_NAMES = Set.of(
            "Identity-H", "Identity-V",
            "GB-EUC-H", "GB-EUC-V", "GBpc-EUC-H", "GBpc-EUC-V",
            "GBK-EUC-H", "GBK-EUC-V", "GBK2K-H", "GBK2K-V",
            "UniGB-UCS2-H", "UniGB-UCS2-V", "UniGB-UTF16-H", "UniGB-UTF16-V",
            "B5pc-H", "B5pc-V", "HKscs-B5-H", "HKscs-B5-V",
            "ETen-B5-H", "ETen-B5-V", "ETenms-B5-H", "ETenms-B5-V",
            "CNS-EUC-H", "CNS-EUC-V",
            "UniCNS-UCS2-H", "UniCNS-UCS2-V", "UniCNS-UTF16-H", "UniCNS-UTF16-V",
            "83pv-RKSJ-H", "90ms-RKSJ-H", "90ms-RKSJ-V", "90msp-RKSJ-H", "90msp-RKSJ-V",
            "90pv-RKSJ-H", "Add-RKSJ-H", "Add-RKSJ-V",
            "EUC-H", "EUC-V", "Ext-RKSJ-H", "Ext-RKSJ-V",
            "H", "V",
            "UniJIS-UCS2-H", "UniJIS-UCS2-V",
            "UniJIS-UCS2-HW-H", "UniJIS-UCS2-HW-V",
            "UniJIS-UTF16-H", "UniJIS-UTF16-V",
            "KSC-EUC-H", "KSC-EUC-V", "KSCms-UHC-H", "KSCms-UHC-V",
            "KSCms-UHC-HW-H", "KSCms-UHC-HW-V", "KSCpc-EUC-H",
            "UniKS-UCS2-H", "UniKS-UCS2-V", "UniKS-UTF16-H", "UniKS-UTF16-V"
    );

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        forEachUniqueFont(pdf, (fname, font, page) -> {
            if (!PdfName.Type0.equals(font.getAsName(PdfName.Subtype))) return;

            PdfObject enc = encodingOf(font);
            if (enc == null) {
                out.add(new FindingDTO(Severity.ERROR, CHECKPOINT, page, null));
                return;
            }
            // Accept predefined CMap names (per ISO 32000-1 Table 118) and embedded CMap streams.
            if (enc.isName() && PREDEFINED_CMAP_NAMES.contains(((PdfName) enc).getValue())) return;
            if (isPredefinedOrEmbeddedCMap(enc)) return;

            out.add(new FindingDTO(Severity.ERROR, CHECKPOINT, page, null));
        });
        return out;
    }
}
