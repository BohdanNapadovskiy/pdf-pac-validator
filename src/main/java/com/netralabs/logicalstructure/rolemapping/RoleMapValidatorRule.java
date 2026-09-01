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
 * Emits findings on the three Role mapping checkpoints in PAC's style:
 *
 * <ul>
 *   <li><b>Role mapping for standard structure types</b> — one finding per RoleMap
 *       entry: PASSED if the entry's chain resolves to a standard tag, ERROR
 *       otherwise.</li>
 *   <li><b>Role mapping of non-standard structure types</b> — dual contribution:
 *       one finding per struct element (PASSED if its /S role is standard or
 *       mapped, ERROR otherwise) plus one PASSED per RoleMap entry that resolves
 *       to a standard tag. PAC's row count = (struct-element count) + (RoleMap
 *       entries that resolve). Verified on the corpus: CalSAWS 34+16=50, Filled
 *       213+15=228, Complex 438+0=438.</li>
 *   <li><b>Circular role mapping</b> — one finding per RoleMap entry: PASSED if
 *       its chain terminates at a standard tag without revisiting any node,
 *       ERROR otherwise.</li>
 * </ul>
 *
 * <p>The rule is registered on all three Role mapping checkpoints; a static guard
 * ensures the work runs once per document. veraPDF still drives errors for these
 * checkpoints (7.1-5/6/7).
 */
public class RoleMapValidatorRule implements Rule {

    private static final Set<String> STD_ROLES = Set.of(
            "Document", "Part", "Art", "Sect", "Div", "P", "H", "H1", "H2", "H3", "H4", "H5", "H6",
            "L", "LI", "Lbl", "LBody", "Table", "TR", "TH", "TD", "THead", "TBody", "TFoot",
            "Figure", "Caption", "Formula", "Link", "Note", "Annot", "Span", "Quote", "Code",
            "Reference", "BibEntry", "Ruby", "RB", "RT", "RP", "Warichu", "WP", "WT",
            "Form", "BlockQuote", "TOC", "TOCI", "Index", "Private",
            // ISO 32000-2 (PDF 2.0) / ISO 14289-2 additions.
            "DocumentFragment", "Aside", "NonStruct", "Title", "FENote",
            "Sub", "Sup", "Em", "Strong", "Artifact"
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

        // 1) Per RoleMap entry: emit one finding per entry on the "for standard",
        //    "non-standard", and "circular role mapping" checkpoints. PAC reports
        //    these counts as (e.g. CalSAWS) 16/16/16 — 16 RoleMap entries each
        //    contribute to all three rows. The non-standard row also gets a per-
        //    struct-element contribution from step 2 below; PAC's total is
        //    (per-element count) + (RoleMap-entry count).
        if (roleMap != null) {
            for (PdfName key : roleMap.keySet()) {
                boolean resolvesStd = resolvesToStandard(key.getValue(), roleMap);
                // "For standard structure types": each entry resolves to a standard tag.
                if (resolvesStd) {
                    out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.ROLE_MAPPING_FOR_STANDARD_STRUCTURE, 0, null));
                } else {
                    out.add(new FindingDTO(Severity.ERROR, PDFUACheckpoint.ROLE_MAPPING_FOR_STANDARD_STRUCTURE, 0, null,
                            "RoleMap entry '" + key.getValue() + "' does not resolve to a standard tag"));
                }
                // "Non-standard structure types": each entry itself counts (the map
                // is the mechanism that adapts non-standard tags to standard ones).
                if (resolvesStd) {
                    out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE, 0, null));
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

        // 2) Per struct element (dict-level DFS — works even when iText's typed API
        //    can't traverse the tree, e.g. malformed docs with non-standard roles):
        //    PASSED if the /S role is standard or mapped to a standard tag; ERROR
        //    if it's non-standard and unmapped (matches PAC's per-element output).
        final PdfDictionary rmapForWalk = roleMap;
        StructUtils.walkStructure(pdf, (parent, se) -> {
            PdfName sName = se.getAsName(PdfName.S);
            if (sName == null) return;
            String raw = sName.getValue();
            int page = StructUtils.pageNumOf(pdf, se);
            if (STD_ROLES.contains(raw) || resolvesToStandard(raw, rmapForWalk)) {
                out.add(new FindingDTO(Severity.PASSED,
                        PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE, page, null));
            } else {
                out.add(new FindingDTO(Severity.ERROR,
                        PDFUACheckpoint.ROLE_MAPPING_FOR_NON_STANDARD_STRUCTURE, page, null,
                        "Non-standard structure type \"" + raw
                                + "\" is neither mapped to a standard structure type nor a valid PDF/UA structure type"));
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
