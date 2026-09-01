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
 * one PASSED per validated skeleton object: Catalog, Pages tree root, every
 * Page dict, per-page /Resources (when present) and /Annots (when present, on
 * tagged docs only), and every struct element (via {@link StructUtils#walkStructure}).
 *
 * <p>Font dictionaries, FontDescriptors, Encodings and the trailer /Info dict
 * are intentionally NOT tallied here — PAC handles fonts via the "Font
 * embedding" row and treats /Info as a metadata concern; folding them in
 * over-counts on font-heavy documents.
 *
 * <p>On <b>untagged</b> docs (no {@code /StructTreeRoot} or {@code /MarkInfo/Marked=true}),
 * /Annots arrays are not tallied — PAC treats the annotation collection as an
 * accessibility concern that's meaningless without a struct tree. Verified on
 * AoD Benchmark ("no tags", 7 pages with /Annots): PAC 34 = 1+1+16+16, our
 * previous 41 over-counted the 7 annotated pages.
 */
public class CorePdfSyntaxCheck implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<FindingDTO> out = new ArrayList<>();
        final boolean tagged = StructUtils.isTaggedPdf(pdf);
        PdfDictionary catalog = pdf.getCatalog() != null ? pdf.getCatalog().getPdfObject() : null;

        // Catalog
        if (catalog == null) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Catalog dictionary missing"));
            return out;
        }
        out.add(new FindingDTO(PASSED, PDF_SYNTAX, 0, null));

        // Pages tree root — one PASSED for the root node, whether or not it has
        // intermediate /Kids nodes (PAC does not tally intermediates separately).
        PdfDictionary pagesRoot = catalog.getAsDictionary(PdfName.Pages);
        if (pagesRoot == null) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Pages tree missing"));
            return out;
        }
        PdfName rootType = pagesRoot.getAsName(PdfName.Type);
        if (rootType != null && !PdfName.Pages.equals(rootType)) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Pages tree node /Type is not /Pages"));
        } else {
            out.add(new FindingDTO(PASSED, PDF_SYNTAX, 0, null));
        }

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

            // Annots array, if present (skipped on untagged docs — PAC parity).
            if (tagged && p.get(PdfName.Annots) != null) {
                out.add(new FindingDTO(PASSED, PDF_SYNTAX, i, null));
            }
        }

        // Struct elements: one PASSED per element via dict-level DFS (works even
        // when iText's typed API can't traverse a malformed struct tree).
        StructUtils.walkStructure(pdf, (parent, se) -> {
            int page = StructUtils.pageNumOf(pdf, se);
            out.add(new FindingDTO(PASSED, PDF_SYNTAX, page, null));
        });

        return out;
    }
}