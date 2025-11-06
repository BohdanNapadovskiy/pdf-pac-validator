package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.netralabs.basic.pdfsyntax.StructUtils.structTreeRoot;
import static com.netralabs.basic.pdfsyntax.StructUtils.walkStructure;
import static com.netralabs.domain.PDFUACheckpoint.PARENTS_OF_STRUCTURE_ELEMENTS;

public class ValidateParentsOfStructureElements implements Rule {
    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        PdfDictionary str = structTreeRoot(pdf);
        if (str == null) return out; // skip (no structure)

        walkStructure(pdf, (parent, child) -> {
            if (parent == str) {
                PdfDictionary p = child.getAsDictionary(new PdfName("P"));
                if (p == null || p == str) {
                    out.add(new FindingDTO(Severity.PASSED, PARENTS_OF_STRUCTURE_ELEMENTS, 0, null));
                } else {
                    out.add(new FindingDTO(Severity.ERROR, PARENTS_OF_STRUCTURE_ELEMENTS, 0, null));
                }
                return;
            }

            // non-top-level: /P must be the enclosing StructElem
            PdfDictionary p = child.getAsDictionary(new PdfName("P"));
            if (p == null) {
                out.add(new FindingDTO(Severity.ERROR, PARENTS_OF_STRUCTURE_ELEMENTS, 0, null));
            } else if (!sameIndirect(p, parent)) {
                out.add(new FindingDTO(Severity.ERROR, PARENTS_OF_STRUCTURE_ELEMENTS, 0, null));
            } else {
                out.add(new FindingDTO(Severity.PASSED, PARENTS_OF_STRUCTURE_ELEMENTS, 0, null));
            }
        });
        return out;
    }

    private static boolean sameIndirect(PdfDictionary a, PdfDictionary b) {
        if (a == null || b == null) return false;
        PdfIndirectReference ra = a.getIndirectReference();
        PdfIndirectReference rb = b.getIndirectReference();
        return ra != null && rb != null && ra.getObjNumber() == rb.getObjNumber();
    }
}
