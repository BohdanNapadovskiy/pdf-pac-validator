package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.PDF_SYNTAX;
import static com.netralabs.domain.Severity.ERROR;

public class CorePdfSyntaxCheck implements Rule {
    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        List<FindingDTO> out = new ArrayList<>();
        PdfDictionary catalog = pdf.getCatalog() != null ? pdf.getCatalog().getPdfObject() : null;
        if (catalog == null) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Catalog dictionary missing"));
        }
        PdfDictionary pages = catalog.getAsDictionary(PdfName.Pages);
        if (pages == null) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Pages tree missing"));
        }
        PdfName pType = pages.getAsName(PdfName.Type);
        if (pType != null && !PdfName.Pages.equals(pType)) {
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Pages tree /Type is not /Pages"));
        }
        int n = pdf.getNumberOfPages();
        if (n <= 0)
            out.add(new FindingDTO(ERROR, PDF_SYNTAX, 0, null, "Document has no pages"));
        for (int i = 1; i <= n; i++) {
            PdfPage page = pdf.getPage(i);
            PdfDictionary p = page.getPdfObject();
            PdfName pageType = p.getAsName(PdfName.Type);
            if (pageType != null && !PdfName.Page.equals(pageType)) {
                out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /Type is not /Page"));
            }
            if (p.getAsDictionary(PdfName.Parent) == null) {
                out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /Parent missing"));
            }
            PdfArray media = p.getAsArray(PdfName.MediaBox);
            if (media == null || media.size() != 4)
                out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /MediaBox missing or invalid"));
            PdfObject res = p.get(PdfName.Resources);
            if (res != null && !(res instanceof PdfDictionary))
                out.add(new FindingDTO(ERROR, PDF_SYNTAX, i, null, "Page /Resources is not a dictionary"));
        }
        return out;
    }
}
