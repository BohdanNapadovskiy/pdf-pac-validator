package com.netralabs.basic.content;

import com.itextpdf.io.source.IRandomAccessSource;
import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;
import java.util.*;

public class ValidateReferencedExternalObjects implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> findings = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int pageNo = 1; pageNo <= pdf.getNumberOfPages(); pageNo++) {
            PdfPage page = pdf.getPage(pageNo);
            PdfDictionary pageRes = page.getResources() != null ? page.getResources().getPdfObject() : null;

            Set<String> resolved = new LinkedHashSet<>();
            Set<String> missing  = new LinkedHashSet<>();
            Set<Integer> visitedForms = new HashSet<>();

            // parse all page content streams
            int streams = page.getContentStreamCount();
            for (int i = 0; i < streams; i++) {
                PdfStream stream = page.getContentStream(i);
                if (stream != null) {
                    byte[]  bytes = stream.getBytes(true);
                    parseStream(pageRes, bytes, resolved, missing, visitedForms);
                }
            }

            if (resolved.isEmpty() && missing.isEmpty()) {
                // No 'Do' operators on this page → skip (PAC-style “—”)
                continue;
            }
            if (!missing.isEmpty()) {
                findings.add(new FindingDTO(
                        Severity.ERROR,
                        com.netralabs.domain.PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT,
                        pageNo,
                        null
                ));
            }
            if (!resolved.isEmpty()) {
                findings.add(new FindingDTO(
                        Severity.PASSED,
                        com.netralabs.domain.PDFUACheckpoint.REFERENCED_EXTERNAL_OBJECT,
                        pageNo,
                        null
                ));
            }
        }
        return findings;
    }

    private void parseStream(PdfDictionary currentRes,
                             byte[] bytes,
                             Set<String> resolved,
                             Set<String> missing,
                             Set<Integer> visitedForms) {

        if (bytes == null) return;

        RandomAccessSourceFactory factory = new RandomAccessSourceFactory();
        RandomAccessFileOrArray ra = new RandomAccessFileOrArray(factory.createSource(bytes));
        PdfTokenizer tk = new PdfTokenizer(ra);

        List<PdfObject> args = new ArrayList<>(8);

        while (safeNext(tk)) {
            if (tk.getTokenType() == PdfTokenizer.TokenType.Other) {
                String op = tk.getStringValue();

                if ("Do".equals(op)) {
                    PdfName xName = (args.size() == 1 && args.get(0).isName()) ? (PdfName) args.get(0) : null;
                    if (xName != null) {
                        PdfStream xobj = lookupXObject(currentRes, xName);
                        if (xobj == null) {
                            missing.add(xName.getValue());
                        } else {
                            resolved.add(xName.getValue());
                            // If it's a Form XObject, recurse with its resources
                            if (PdfName.Form.equals(xobj.getAsName(PdfName.Subtype))) {
                                PdfIndirectReference ref = xobj.getIndirectReference();
                                Integer id = (ref != null) ? ref.getObjNumber() : System.identityHashCode(xobj);
                                if (visitedForms.add(id)) {
                                    PdfDictionary subRes = xobj.getAsDictionary(PdfName.Resources);
                                    byte[] subBytes = xobj.getBytes(true);
                                    parseStream(subRes, subBytes, resolved, missing, visitedForms);
                                }
                            }
                        }
                    }
                }
                // clear operands for next operator
                args.clear();
            } else {
                args.add(readOperand(tk));
            }
        }
    }

    private PdfStream lookupXObject(PdfDictionary res, PdfName name) {
        if (res == null || name == null) return null;
        PdfDictionary xobjs = res.getAsDictionary(PdfName.XObject);
        return xobjs != null ? xobjs.getAsStream(name) : null;
    }

    private static PdfObject readOperand(PdfTokenizer tk) {
        switch (tk.getTokenType()) {
            case Number:
                return new PdfNumber(Double.parseDouble(tk.getStringValue()));
            case String:
                return new PdfString(tk.getStringValue());
            case Name:
                return new PdfName(tk.getStringValue());

            default:
                return new PdfLiteral(tk.getStringValue());
        }
    }

    private static boolean safeNext(PdfTokenizer tk) {
        try { return tk.nextToken(); } catch (Exception e) { return false; }
    }
}
