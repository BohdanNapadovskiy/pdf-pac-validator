package com.netralabs.logicalstructure.structureelements;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.netralabs.domain.PDFUACheckpoint.*;

/**
 * Counts struct elements per PDF role and emits one PASSED finding per element on the
 * role's corresponding checkpoint. PAC produces these counts (e.g. "P" → 146 passes,
 * "Span" → 518 passes) for the "Structure tree" subcategory.
 *
 * <p>The walk is done once per document; the rule is registered on every element-type
 * checkpoint so each one is reported as "natively covered" (so it lands on NOT_APPLICABLE
 * when no element of that role exists). A static guard ensures we only walk and emit on
 * the first instance per document.
 */
public class StructElementByRoleRule implements Rule {

    private static final Map<String, PDFUACheckpoint> ROLE_TO_CHECKPOINT = Map.ofEntries(
            Map.entry("Document",   DOCUMENT_STRUCTURE_ELEMENT),
            Map.entry("Part",       PART_STRUCTURE_ELEMENT),
            Map.entry("Art",        ART_STRUCTURE_ELEMENT),
            Map.entry("Sect",       SECT_STRUCTURE_ELEMENT),
            Map.entry("Div",        DIV_STRUCTURE_ELEMENT),
            Map.entry("BlockQuote", BLOCKQUOTE_STRUCTURE_ELEMENT),
            Map.entry("Caption",    CAPTION_STRUCTURE_ELEMENTS),
            Map.entry("TOC",        TOC_STRUCTURE_ELEMENTS),
            Map.entry("TOCI",       TOCI_STRUCTURE_ELEMENTS),
            Map.entry("Index",      INDEX_STRUCTURE_ELEMENTS),
            Map.entry("Private",    PRIVATE_STRUCTURE_ELEMENTS),
            Map.entry("H",          H_STRUCTURE_ELEMENTS),
            Map.entry("H1",         H1_STRUCTURE_ELEMENTS),
            Map.entry("H2",         H2_STRUCTURE_ELEMENTS),
            Map.entry("H3",         H3_STRUCTURE_ELEMENTS),
            Map.entry("H4",         H4_STRUCTURE_ELEMENTS),
            Map.entry("H5",         H5_STRUCTURE_ELEMENTS),
            Map.entry("H6",         H6_STRUCTURE_ELEMENTS),
            Map.entry("P",          P_STRUCTURE_ELEMENTS),
            Map.entry("L",          L_STRUCTURE_ELEMENTS),
            Map.entry("LI",         LI_STRUCTURE_ELEMENTS),
            Map.entry("Lbl",        Lbl_STRUCTURE_ELEMENTS),
            Map.entry("LBody",      LBODY_STRUCTURE_ELEMENTS),
            Map.entry("Table",      TABLE_STRUCTURE_ELEMENTS),
            Map.entry("TR",         TR_STRUCTURE_ELEMENTS),
            Map.entry("TH",         TH_STRUCTURE_ELEMENTS),
            Map.entry("TD",         TD_STRUCTURE_ELEMENTS),
            Map.entry("THead",      THEAD_STRUCTURE_ELEMENTS),
            Map.entry("TBody",      TBODY_STRUCTURE_ELEMENTS),
            Map.entry("TFoot",      TFOOT_STRUCTURE_ELEMENTS),
            Map.entry("Span",       SPAN_STRUCTURE_ELEMENTS),
            Map.entry("Quote",      QUOTE_STRUCTURE_ELEMENTS),
            Map.entry("Note",       NOTE_STRUCTURE_ELEMENTS),
            Map.entry("Reference",  REFERENCE_STRUCTURE_ELEMENTS),
            Map.entry("BibEntry",   BIBENTRY_STRUCTURE_ELEMENTS),
            Map.entry("Code",       CODE_STRUCTURE_ELEMENTS),
            Map.entry("Link",       LINK_STRUCTURE_ELEMENTS),
            Map.entry("Annot",      ANNOT_STRUCTURE_ELEMENTS),
            Map.entry("Ruby",       RUBY_STRUCTURE_ELEMENTS),
            Map.entry("RB",         RB_STRUCTURE_ELEMENTS),
            Map.entry("RT",         RT_STRUCTURE_ELEMENTS),
            Map.entry("RP",         RP_STRUCTURE_ELEMENTS),
            Map.entry("Warichu",    WARICHU_STRUCTURE_ELEMENTS),
            Map.entry("WP",         WP_STRUCTURE_ELEMENTS),
            Map.entry("WT",         WT_STRUCTURE_ELEMENTS),
            Map.entry("Figure",     FIGURE_STRUCTURE_ELEMENTS),
            Map.entry("Formula",    FORMULA_STRUCTURE_ELEMENTS),
            Map.entry("Form",       FORM_STRUCTURE_ELEMENTS)
    );

    // Guard: only the first instance per document does the walk; later instances return empty.
    private static volatile PdfDocument done;

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        synchronized (StructElementByRoleRule.class) {
            if (done == pdf) return new ArrayList<>();
            done = pdf;
        }

        List<FindingDTO> out = new ArrayList<>();
        StructWalk.walk(pdf, elem -> {
            String role = StructWalk.normRole(pdf, elem);
            if (role == null) return;
            PDFUACheckpoint cp = ROLE_TO_CHECKPOINT.get(role);
            int page = StructUtils.pageNumOf(pdf, elem.getPdfObject());
            if (cp != null) {
                out.add(new FindingDTO(Severity.PASSED, cp, page, null));
            }

            // PAC behavior for 7.2-10 ("TR may contain only TH and TD"): emit the error against
            // the offending child (e.g. Span), not against the TR parent the way veraPDF does.
            if ("TR".equals(role)) {
                List<IStructureNode> kids = elem.getKids();
                if (kids != null) {
                    for (IStructureNode kid : kids) {
                        if (!(kid instanceof PdfStructElem child)) continue;
                        String childRole = StructWalk.normRole(pdf, child);
                        if ("TH".equals(childRole) || "TD".equals(childRole)) continue;
                        PDFUACheckpoint childCp = childRole == null ? null : ROLE_TO_CHECKPOINT.get(childRole);
                        if (childCp == null) continue;
                        int childPage = StructUtils.pageNumOf(pdf, child.getPdfObject());
                        out.add(new FindingDTO(Severity.ERROR, childCp, childPage, null,
                                childRole + " element is misplaced as direct child of TR (only TH/TD allowed)"));
                    }
                }
            }
        });
        return out;
    }
}
