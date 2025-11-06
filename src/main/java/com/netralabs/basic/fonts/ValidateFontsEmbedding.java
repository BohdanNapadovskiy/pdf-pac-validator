package com.netralabs.basic.fonts;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.FONT_EMBEDDING;


@Slf4j
public class ValidateFontsEmbedding implements Rule {


    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public boolean supportsRole(PdfName role) {
        return Rule.super.supportsRole(role);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
            PdfDictionary pageRes = pdf.getPage(p).getResources().getPdfObject();
            if (pageRes == null) continue;

            // Track fonts we’ve already checked on this page (by object number)
            var seenFontObjNums = new java.util.HashSet<Integer>();

            // Walk resources recursively (page → forms → patterns → nested)
            validateFontsInResources(pageRes, p, seenFontObjNums, out);
        }
        return out;
    }

    private void validateFontsInResources(PdfDictionary resources,
                                          int page,
                                          java.util.Set<Integer> seenFontObjNums,
                                          List<FindingDTO> out) {

        if (resources == null) return;

        // 1) Fonts at this resource level
        PdfDictionary fonts = resources.getAsDictionary(PdfName.Font);
        if (fonts != null) {
            for (PdfName fname : fonts.keySet()) {
                PdfDictionary font = fonts.getAsDictionary(fname); // auto-deref
                if (font == null) continue;

                PdfIndirectReference ref = font.getIndirectReference();
                Integer objNum = (ref != null) ? ref.getObjNumber() : System.identityHashCode(font);

                if (seenFontObjNums.add(objNum)) { // first time we see this font on this page
                    PdfDictionary fd = font.getAsDictionary(PdfName.FontDescriptor);
                    if (!isEmbedded(fd)) {
                        log.error("Font {} not embedded in page {}", fname.getValue(), page);
                        out.add(new FindingDTO(Severity.ERROR, FONT_EMBEDDING, page,
                                null));
                    } else {
                        log.info("Font {} embedded in page {}", fname.getValue(), page);
                        out.add(new FindingDTO(Severity.PASSED, FONT_EMBEDDING, page,
                                null));
                    }
                }
            }
        }

        // 2) Recurse into Form XObjects
        PdfDictionary xobjects = resources.getAsDictionary(PdfName.XObject);
        if (xobjects != null) {
            for (PdfName xn : xobjects.keySet()) {
                PdfStream xo = xobjects.getAsStream(xn);
                if (xo == null) continue;
                PdfName subtype = xo.getAsName(PdfName.Subtype);
                if (PdfName.Form.equals(subtype)) {
                    PdfDictionary xoRes = xo.getAsDictionary(PdfName.Resources);
                    validateFontsInResources(xoRes, page, seenFontObjNums, out);
                }
            }
        }

        // 3) Recurse into Tiling Patterns (PatternType 1)
        PdfDictionary patterns = resources.getAsDictionary(PdfName.Pattern);
        if (patterns != null) {
            for (PdfName pn : patterns.keySet()) {
                PdfStream pat = patterns.getAsStream(pn);
                if (pat == null) continue;
                PdfNumber type = pat.getAsNumber(PdfName.PatternType);
                if (type != null && type.intValue() == 1) { // tiling pattern
                    PdfDictionary patRes = pat.getAsDictionary(PdfName.Resources);
                    validateFontsInResources(patRes, page, seenFontObjNums, out);
                }
            }
        }
    }


    private static boolean isEmbedded(PdfDictionary fd) {
        if (fd == null) return false;
        else return true;
//        return fd.containsKey(PdfName.FontFile) ||
//                fd.containsKey(PdfName.FontFile2) ||
//                fd.containsKey(PdfName.FontFile3);
    }
}