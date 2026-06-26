package com.netralabs.basic.naturallanguage;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;

import com.netralabs.report.FindingDTO;

import java.util.*;


import static com.netralabs.basic.naturallanguage.LangUtils.docLang;

public class ValidateLangOfTextObjects implements Rule {



    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        final String docLang = docLang(pdf);
        for (int pageNum = 1; pageNum <= pdf.getNumberOfPages(); pageNum++) {
            PdfPage page = pdf.getPage(pageNum);
            ContentListener listener = new ContentListener(out, pageNum, docLang);
            PdfCanvasProcessor proc = new PdfCanvasProcessor(listener);
            proc.registerContentOperator("BMC", (p, op, operands) -> {
                String tag = operands.get(0).toString().replace("/", "");
                listener.beginMarked(tag, null);
            });

            proc.registerContentOperator("BDC", (p, op, operands) -> {
                String tag = operands.get(0).toString().replace("/", "");
                PdfDictionary props = (operands.size() > 1 && operands.get(1) instanceof PdfDictionary)
                        ? (PdfDictionary) operands.get(1) : null;
                listener.beginMarked(tag, props);
            });

            proc.registerContentOperator("EMC", (p, op, operands) -> listener.endMarked());
            proc.processPageContent(page);
        }
        return out;
    }

}
