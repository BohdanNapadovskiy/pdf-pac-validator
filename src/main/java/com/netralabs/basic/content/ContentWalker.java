package com.netralabs.basic.content;

import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class ContentWalker {

    private enum Scope {ARTIFACT, TAGGED_MCID, OTHER}

    private static final PdfName ARTIFACT = new PdfName("Artifact");
    private static final PdfName MCID = new PdfName("MCID");

    private ContentWalker() {
    }

    public static void walkPage(PdfDocument pdf, int pageNo, Hook hook) {
        PdfPage page = pdf.getPage(pageNo);
        if (page == null) return;

        Deque<Scope> stack = new ArrayDeque<>();
        // iterate content streams (page + nested forms when /Do occurs)
        try {
            parseStream(page, page.getContentBytes(), stack, hook, page.getResources().getPdfObject());
            for (int i = 1; i < page.getContentStreamCount(); i++) {
                parseStream(page, page.getContentStream(i).getBytes(true), stack, hook, page.getResources().getPdfObject());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseStream(PdfPage page, byte[] bytes, Deque<Scope> stack,
                                    Hook hook, PdfDictionary currentRes) throws Exception {
        if (bytes == null) return;

        RandomAccessSourceFactory factory = new RandomAccessSourceFactory();
        RandomAccessFileOrArray ra = new RandomAccessFileOrArray(factory.createSource(bytes));
        PdfTokenizer tk = new PdfTokenizer(ra);

        ArrayList<PdfObject> args = new ArrayList<>(8);

        while (tk.nextToken()) {
            if (tk.getTokenType() == PdfTokenizer.TokenType.Other) {
                String op = tk.getStringValue();

                switch (op) {
                    case "BMC": {
                        PdfName tag = args.size() == 1 && args.get(0).isName() ? (PdfName) args.get(0) : null;
                        if (ARTIFACT.equals(tag)) {
                            stack.push(Scope.ARTIFACT);
                            hook.onBeginArtifact();
                        } else {
                            stack.push(Scope.OTHER);
                            hook.onBeginOtherMarked(tag);
                        }
                        break;
                    }
                    case "BDC": {
                        PdfName tag = args.size() >= 1 && args.get(0).isName() ? (PdfName) args.get(0) : null;
                        PdfDictionary props = resolveProps(currentRes, args.size() >= 2 ? args.get(1) : null);
                        if (ARTIFACT.equals(tag)) {
                            stack.push(Scope.ARTIFACT);
                            hook.onBeginArtifact();
                        } else if (props != null && props.getAsNumber(MCID) != null) {
                            stack.push(Scope.TAGGED_MCID);
                            hook.onBeginTaggedMcid(props.getAsNumber(MCID).intValue(), page.getPdfObject());
                        } else {
                            stack.push(Scope.OTHER);
                            hook.onBeginOtherMarked(tag);
                        }
                        break;
                    }
                    case "EMC":
                        if (!stack.isEmpty()) stack.pop();
                        hook.onEndMarked();
                        break;

                    // text painting
                    case "Tj":
                    case "'":
                    case "\"":
                        hook.onPainted();
                        if (!args.isEmpty() && args.get(0).isString()) hook.onShowText((PdfString) args.get(0));
                        break;
                    case "TJ":
                        hook.onPainted();
                        if (!args.isEmpty() && args.get(0).isArray()) hook.onShowTextArray((PdfArray) args.get(0));
                        break;

                    // images / forms
                    case "Do":
                        hook.onPainted();
                        // Optionally descend into Form XObject content
                        PdfName xName = args.size() == 1 && args.get(0).isName() ? (PdfName) args.get(0) : null;
                        PdfDictionary xobjs = currentRes != null ? currentRes.getAsDictionary(PdfName.XObject) : null;
                        if (xobjs != null && xName != null) {
                            PdfStream xo = xobjs.getAsStream(xName);
                            if (xo != null && PdfName.Form.equals(xo.getAsName(PdfName.Subtype))) {
                                PdfFormXObject fx = new PdfFormXObject(xo);
                                parseStream(page, fx.getPdfObject().getBytes(), stack, hook,
                                        xo.getAsDictionary(PdfName.Resources));
                            }
                        }
                        break;

                    // path paint (stroke/fill) ops
                    case "S":
                    case "s":
                    case "f":
                    case "F":
                    case "f*":
                    case "B":
                    case "B*":
                    case "b":
                    case "b*":
                        hook.onPainted();
                        break;
                }
                args.clear();
            } else {
                args.add(readOperand(tk));
            }
        }
    }

    private static PdfDictionary resolveProps(PdfDictionary res, PdfObject prop) {
        if (prop == null) return null;
        if (prop.isDictionary()) return (PdfDictionary) prop;
        if (prop.isName() && res != null) {
            PdfDictionary props = res.getAsDictionary(PdfName.Properties);
            return props != null ? props.getAsDictionary((PdfName) prop) : null;
        }
        return null;
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

}
