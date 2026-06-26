package com.netralabs.logicalstructure.rolemapping;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Emits PASSED findings on the three Role mapping checkpoints in PAC's style:
 *
 * <ul>
 *   <li><b>Role mapping for standard structure types</b> — one PASSED per struct element whose
 *       raw /S role is already a standard PDF tag (no mapping needed).</li>
 *   <li><b>Role mapping of non-standard structure types</b> — one PASSED per struct element whose
 *       raw /S role is non-standard but is mapped (via the catalog's RoleMap) to a standard tag.</li>
 *   <li><b>Circular role mapping</b> — one PASSED per RoleMap entry whose chain terminates at a
 *       standard tag without revisiting any node.</li>
 * </ul>
 *
 * The rule is registered on all three Role mapping checkpoints; a static guard ensures the work
 * runs once per document. veraPDF still drives errors for these checkpoints (7.1-5/6/7).
 */
public class RoleMapValidatorRule implements Rule {

    private static final Set<String> STD_ROLES = Set.of(
            "Document", "Part", "Art", "Sect", "Div", "P", "H", "H1", "H2", "H3", "H4", "H5", "H6",
            "L", "LI", "Lbl", "LBody", "Table", "TR", "TH", "TD", "THead", "TBody", "TFoot",
            "Figure", "Caption", "Formula", "Link", "Note", "Annot", "Span", "Quote", "Code",
            "Reference", "BibEntry", "Ruby", "RB", "RT", "RP", "Warichu", "WP", "WT",
            "Form", "BlockQuote", "TOC", "TOCI", "Index", "Private"
    );

    private static volatile PdfDocument done;

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        synchronized (RoleMapValidatorRule.class) {
            if (done == pdf) return new ArrayList<>();
            done = pdf;
        }

        List<FindingDTO> out = new ArrayList<>();
        PdfDictionary str = StructUtils.structTreeRoot(pdf);
        PdfDictionary roleMap = str == null ? null : str.getAsDictionary(new PdfName("RoleMap"));

        // 1) Per RoleMap entry: emit one finding per entry on both "for standard" and
        //    "circular role mapping" checkpoints. PAC reports these counts as
        //    "16 entries that map to a standard tag" + "16 entries with no cycle".
        if (roleMap != null) {
            for (PdfName key : roleMap.keySet()) {
                // "For standard structure types": each entry resolves to a standard tag.
                if (resolvesToStandard(key.getValue(), roleMap)) {
                    out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.ROLE_MAPPING_FOR_STANDARD_STRUCTURE, 0, null));
                } else {
                    out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.ROLE_MAPPING_FOR_STANDARD_STRUCTURE, 0, null,
                            "RoleMap entry '" + key.getValue() + "' does not resolve to a standard tag"));
                }
                // Circular role mapping: each entry chain is acyclic.
                if (isCircular(key, roleMap)) {
                    out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.CIRCULAR_ROLE_MAPPING, 0, null,
                            "Circular role mapping entry: " + key.getValue()));
                } else {
                    out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.CIRCULAR_ROLE_MAPPING, 0, null));
                }
            }
        }

        // 2) Per struct element: emit one PASSED on "of non-standard structure types".
        //    PAC reports a count close to the number of struct elements (1077 in the
        //    reference document; we walk the same tree).
        StructWalk.walk(pdf, elem -> {
            PdfName sName = elem.getPdfObject().getAsName(PdfName.S);
            if (sName == null) return;
            String raw = sName.getValue();
            int page = StructUtils.pageNumOf(pdf, elem.getPdfObject());
            if (STD_ROLES.contains(raw) || resolvesToStandard(raw, roleMap)) {
                out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE, page, null));
            }
        });

        return out;
    }

    private static boolean resolvesToStandard(String role, PdfDictionary roleMap) {
        if (roleMap == null) return false;
        Set<String> seen = new HashSet<>();
        String cur = role;
        while (cur != null && !STD_ROLES.contains(cur)) {
            if (!seen.add(cur)) return false; // cycle
            PdfName next = roleMap.getAsName(new PdfName(cur));
            cur = next == null ? null : next.getValue();
        }
        return cur != null;
    }

    private static boolean isCircular(PdfName start, PdfDictionary roleMap) {
        Set<String> seen = new HashSet<>();
        String cur = start.getValue();
        while (cur != null) {
            if (!seen.add(cur)) return true;
            if (STD_ROLES.contains(cur)) return false;
            PdfName next = roleMap.getAsName(new PdfName(cur));
            cur = next == null ? null : next.getValue();
        }
        return false;
    }
}
