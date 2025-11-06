package com.netralabs.basic.naturallanguage;

import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ContentLangWalker {
    private ContentLangWalker(){}
    public interface Hook {
        void on(String event, PdfObject payload);
    }

    private static final PdfName LANG = PdfName.Lang;

    public static void walkPage(PdfPage page, Hook hook) {
        Deque<String> langStack = new ArrayDeque<>();
        PdfDictionary res = page.getResources() != null ? page.getResources().getPdfObject() : null;
        int n = page.getContentStreamCount();
        for (int i = 0; i < n; i++) {
            PdfStream s = page.getContentStream(i);
            if (s != null) parseStream(res, s, langStack, hook);
        }
    }

    private static void parseStream(PdfDictionary currentRes, PdfStream stream, Deque<String> langStack, Hook hook) {
        byte[] decoded = stream.getBytes(true);
        RandomAccessFileOrArray ra = new RandomAccessFileOrArray(new RandomAccessSourceFactory().createSource(decoded));
        PdfTokenizer tk = new PdfTokenizer(ra);

        List<PdfObject> args = new ArrayList<>(8);

        while (safeNext(tk)) {
            PdfTokenizer.TokenType tt = tk.getTokenType();
            if (tt == PdfTokenizer.TokenType.Other) {
                String op = tk.getStringValue();
                switch (op) {
                    case "BMC": { // name
                        PdfName tag = (args.size()==1 && args.get(0).isName()) ? (PdfName) args.get(0) : null;
                        // BMC has no props; no /Lang in stack change
                        break;
                    }
                    case "BDC": { // name, props(dict or name → res/Properties)
                        PdfName tag = (args.size()>=1 && args.get(0).isName()) ? (PdfName) args.get(0) : null;
                        PdfDictionary props = resolveProps(currentRes, args.size()>=2 ? args.get(1) : null);
                        if (props != null) {
                            PdfString lang = props.getAsString(LANG);
                            if (lang != null && !lang.getValue().isBlank()) {
                                langStack.push(lang.getValue());
                                hook.on("LANG_PUSH", new PdfString(lang.getValue()));
                            }
                        }
                        break;
                    }
                    case "EMC": {
                        // pop only if we pushed a language on last BDC; to keep it simple, pop if stack not empty
                        if (!langStack.isEmpty()) { langStack.pop(); hook.on("LANG_POP", null); }
                        break;
                    }
                    case "Tj": case "'": case "\"":
                        if (!args.isEmpty() && args.get(0).isString()) hook.on("TEXT", args.get(0));
                        break;
                    case "TJ":
                        if (!args.isEmpty() && args.get(0).isArray()) hook.on("TEXT_ARRAY", args.get(0));
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
            return (props != null) ? props.getAsDictionary((PdfName) prop) : null;
        }
        return null;
    }

    private static PdfObject readOperand(PdfTokenizer tk) {
        PdfTokenizer.TokenType t = tk.getTokenType();
        if (t == PdfTokenizer.TokenType.Number) return new PdfNumber(Double.parseDouble(tk.getStringValue()));
        if (t == PdfTokenizer.TokenType.String) return new PdfString(tk.getStringValue());
        if (t == PdfTokenizer.TokenType.Name)   return new PdfName(tk.getStringValue());
        return new PdfLiteral(tk.getStringValue());
    }

    private static boolean safeNext(PdfTokenizer tk) { try { return tk.nextToken(); } catch(Exception e){ return false; } }
}
