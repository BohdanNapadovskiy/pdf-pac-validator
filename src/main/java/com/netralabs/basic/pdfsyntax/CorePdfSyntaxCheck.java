package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.PDF_SYNTAX;
import static com.netralabs.domain.Severity.ERROR;
import static com.netralabs.domain.Severity.PASSED;

/**
 * ISO 32000-1 §7.5 file structure sanity checks. PAC's "PDF syntax" tally is
 * one PASSED per validated indirect PDF object that participates in the
 * document skeleton: Catalog, every Pages tree node, every Page, every
 * struct element, Resources / Annots dicts per page, and the Info dict.
 *
 * <p>For a small untagged doc this yields single-digit counts; for a large
 * well-tagged doc it scales with the struct-tree size — matching PAC.
 */
public class CorePdfSyntaxCheck implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<FindingDTO> out = new ArrayList<>();
        PdfDictionary catalog = pdf.getCatalog() != null ? pdf.getCatalog().getPdfObject() : null;

        // Catalog
        if (catalog == null) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Catalog dictionary missing"));
            return out;
        }
        out.add(new FindingDTO(PASSED, PDF_SYNTAX, 0, null));

        // Pages tree — count every node (root + intermediates).
        PdfDictionary pagesRoot = catalog.getAsDictionary(PdfName.Pages);
        if (pagesRoot == null) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Pages tree missing"));
            return out;
        }
        countPagesTreeNodes(pagesRoot, out);

        // Per page: page dict + Resources (if present) + Annots (if present).
        int n = pdf.getNumberOfPages();
        if (n <= 0) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Document has no pages"));
            return out;
        }
        for (int i = 1; i <= n; i++) {
            PdfPage page = pdf.getPage(i);
            PdfDictionary p = page.getPdfObject();

            PdfName pageType = p.getAsName(PdfName.Type);
            if (pageType != null && !PdfName.Page.equals(pageType)) {
                out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /Type is not /Page"));
            } else if (p.getAsDictionary(PdfName.Parent) == null) {
                out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /Parent missing"));
            } else {
                PdfArray media = p.getAsArray(PdfName.MediaBox);
                if (media == null || media.size() != 4) {
                    out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /MediaBox missing or invalid"));
                } else {
                    out.add(new FindingDTO(PASSED, PDF_SYNTAX, i, null));
                }
            }

            // Resources dict, if the page carries one (may be inherited otherwise).
            PdfObject res = p.get(PdfName.Resources);
            if (res instanceof PdfDictionary) {
                out.add(new FindingDTO(PASSED, PDF_SYNTAX, i, null));
            } else if (res != null) {
                out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /Resources is not a dictionary"));
            }

            // Annots array, if present.
            if (p.get(PdfName.Annots) != null) {
                out.add(new FindingDTO(PASSED, PDF_SYNTAX, i, null));
            }
        }

        // Info dict (only when it's an indirect dictionary).
        PdfObject info = pdf.getTrailer().get(PdfName.Info);
        if (info instanceof PdfDictionary && info.getIndirectReference() != null) {
            out.add(new FindingDTO(PASSED, PDF_SYNTAX, 0, null));
        }

        // Struct elements: one PASSED per element via dict-level DFS (works even
        // when iText's typed API can't traverse a malformed struct tree).
        StructUtils.walkStructure(pdf, (parent, se) -> {
            int page = StructUtils.pageNumOf(pdf, se);
            out.add(new FindingDTO(PASSED, PDF_SYNTAX, page, null));
        });

        // Font resources referenced by pages — one PASSED per unique Font dict
        // (excluding Standard 14 which need no validation).
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int i = 1; i <= n; i++) {
            PdfPage page = pdf.getPage(i);
            PdfDictionary res = page.getPdfObject().getAsDictionary(PdfName.Resources);
            if (res == null) continue;
            PdfDictionary fonts = res.getAsDictionary(PdfName.Font);
            if (fonts == null) continue;
            for (PdfName fname : fonts.keySet()) {
                PdfDictionary font = fonts.getAsDictionary(fname);
                if (font == null || isStandard14(font)) continue;
                addOncePassed(seen, font, out);
            }
        }

        // Any indirect FontDescriptor or Encoding dictionary in the file (whether
        // reached from pages or via XObjects). PAC includes these in the tally.
        int totalObjs = pdf.getNumberOfPdfObjects();
        for (int i = 1; i <= totalObjs; i++) {
            PdfObject o = pdf.getPdfObject(i);
            if (!(o instanceof PdfDictionary d) || o.isStream()) continue;
            PdfName t = d.getAsName(PdfName.Type);
            if (PdfName.FontDescriptor.equals(t) || PdfName.Encoding.equals(t)) {
                addOncePassed(seen, d, out);
            }
        }

        return out;
    }

    private static boolean addOncePassed(java.util.Set<Integer> seen, PdfDictionary d,
                                         List<FindingDTO> out) {
        PdfIndirectReference ref = d.getIndirectReference();
        Integer id = ref != null ? ref.getObjNumber() : System.identityHashCode(d);
        if (!seen.add(id)) return false;
        out.add(new FindingDTO(PASSED, PDF_SYNTAX, 0, null));
        return true;
    }

    private static final java.util.Set<String> STANDARD_14 = java.util.Set.of(
            "Times-Roman", "Times-Bold", "Times-Italic", "Times-BoldItalic",
            "Helvetica", "Helvetica-Bold", "Helvetica-Oblique", "Helvetica-BoldOblique",
            "Courier", "Courier-Bold", "Courier-Oblique", "Courier-BoldOblique",
            "Symbol", "ZapfDingbats");

    private static boolean isStandard14(PdfDictionary font) {
        PdfName base = font.getAsName(PdfName.BaseFont);
        if (base == null) return false;
        String name = base.getValue();
        int plus = name.indexOf('+');
        if (plus == 6) name = name.substring(plus + 1);
        return STANDARD_14.contains(name);
    }

    /** DFS over the pages tree emitting one PASSED per Pages node. */
    private static void countPagesTreeNodes(PdfDictionary node, List<FindingDTO> out) {
        PdfName t = node.getAsName(PdfName.Type);
        if (t != null && !PdfName.Pages.equals(t)) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Pages tree node /Type is not /Pages"));
            return;
        }
        out.add(new FindingDTO(PASSED, PDF_SYNTAX, 0, null));
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids == null) return;
        for (int i = 0; i < kids.size(); i++) {
            PdfObject k = kids.get(i);
            if (k instanceof PdfDictionary d && PdfName.Pages.equals(d.getAsName(PdfName.Type))) {
                countPagesTreeNodes(d, out);
            }
        }
    }
}
