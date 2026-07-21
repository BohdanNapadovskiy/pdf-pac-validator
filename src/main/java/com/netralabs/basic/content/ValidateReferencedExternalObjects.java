package com.netralabs.basic.content;

import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfStream;
import com.netralabs.Rule;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.List;

/**
 * Emits one PASSED finding per page that contains at least one <code>Do</code> operator
 * whose name argument resolves to a <em>Form</em> XObject in the page's
 * <code>/Resources/XObject</code> dictionary. Matches PAC's per-page emission granularity
 * (verified: 13 pages → 13 PASSED on Complex_Presentation_Sample.pdf; OP_AoD's 16 pages
 * of image-only Do calls → 0 PASSED / row shown as N/A by PAC).
 *
 * <p>Image XObjects don't count — PAC only surfaces the check for Form XObjects because
 * only those can carry their own struct-tree-relevant content. A page whose Do operators
 * exclusively reference Images contributes nothing to this row.
 *
 * <p>ERRORs are intentionally not emitted here — veraPDF (7.20-1) drives the failure side
 * for missing references. Same pattern as {@code ValidateAnnotationNesting} (F-14): native
 * adds the PASSED counts that vera doesn't expose; combined column matches PAC.
 *
 * <p>Walks only the page's top-level content streams. Nested Form XObject content is not
 * recursed into — a {@code Do} inside a Form references an XObject from that Form's own
 * resources, which is a different scope from "external objects referenced by the page".
 */
public class ValidateReferencedExternalObjects implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfPage page = ctx.page();
        if (page == null) return List.of();
        // ISO 14289-1 §7.20-1 evaluates whether referenced content is reflected in
        // the struct tree — a meaningless check on untagged docs. PAC treats the
        // row as N/A (dashes) when /StructTreeRoot or /MarkInfo /Marked are absent.
        if (!StructUtils.isTaggedPdf(ctx.pdf())) return List.of();

        PdfDictionary xobjects = page.getResources() != null
                ? page.getResources().getPdfObject().getAsDictionary(PdfName.XObject)
                : null;
        if (xobjects == null) return List.of();

        int streams = page.getContentStreamCount();
        for (int i = 0; i < streams; i++) {
            PdfStream stream = page.getContentStream(i);
            if (stream != null && hasResolvedFormDo(stream.getBytes(true), xobjects)) {
                return List.of(new FindingDTO(Severity.PASSED,
                        PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT, ctx.pageNum(), null));
            }
        }
        return List.of();
    }

    private static boolean hasResolvedFormDo(byte[] bytes, PdfDictionary xobjects) {
        if (bytes == null) return false;
        RandomAccessSourceFactory factory = new RandomAccessSourceFactory();
        RandomAccessFileOrArray ra = new RandomAccessFileOrArray(factory.createSource(bytes));
        PdfTokenizer tk = new PdfTokenizer(ra);
        String lastName = null;

        while (safeNext(tk)) {
            PdfTokenizer.TokenType t = tk.getTokenType();
            if (t == PdfTokenizer.TokenType.Other) {
                if ("Do".equals(tk.getStringValue()) && lastName != null) {
                    PdfStream xo = xobjects.getAsStream(new PdfName(lastName));
                    if (xo != null && PdfName.Form.equals(xo.getAsName(PdfName.Subtype))) {
                        return true;
                    }
                }
                lastName = null;
            } else if (t == PdfTokenizer.TokenType.Name) {
                lastName = tk.getStringValue();
            } else {
                lastName = null;
            }
        }
        return false;
    }

    private static boolean safeNext(PdfTokenizer tk) {
        try { return tk.nextToken(); } catch (Exception e) { return false; }
    }
}
