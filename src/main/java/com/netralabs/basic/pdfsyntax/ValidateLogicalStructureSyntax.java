package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;


import static com.netralabs.basic.pdfsyntax.StructUtils.isTaggedPdf;
import static com.netralabs.basic.pdfsyntax.StructUtils.pageNumOf;
import static com.netralabs.basic.pdfsyntax.StructUtils.structTreeRoot;
import static com.netralabs.basic.pdfsyntax.StructUtils.walkStructure;
import static com.netralabs.domain.PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX;

/**
 * Syntactic well-formedness of struct elements: /S presence and /K child-reference
 * shape (MCID needs /Pg, MCR/OBJR keys well-formed, /K element type recognized).
 *
 * <p>Role validity (standard-or-mapped) is intentionally NOT checked here — that
 * concern belongs to the "Role mapping" subcategory, driven by
 * {@code RoleMapValidatorRule} + vera clauses 7.1-5/6/7. PAC reports role
 * violations under Role mapping, never under Logical structure syntax.
 */
public class ValidateLogicalStructureSyntax implements Rule {

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        // PAC treats logical-structure-syntax rows as NA when the document isn't a
        // tagged PDF (no /MarkInfo/Marked=true). Skip validation for vestigial struct
        // trees on untagged docs — they aren't required to be well-formed.
        if (!isTaggedPdf(pdf)) return out;
        PdfDictionary str = structTreeRoot(pdf);
        if (str == null) return out;

        walkStructure(pdf, (parent, se) -> {
            int page = pageNumOf(pdf, se);
            String error = null;

            // 1) /S must exist. Role standardness is Role mapping's job, not ours.
            PdfName role = se.getAsName(PdfName.S);
            if (role == null) {
                error = "Structure element missing /S role";
            }

            // 2) Validate /K content references (syntax only)
            if (error == null) {
                PdfObject k = se.get(PdfName.K);
                if (k != null) {
                    if (k.isNumber()) {
                        if (se.getAsDictionary(PdfName.Pg) == null) {
                            error = "MCID reference without /Pg on structure element";
                        }
                    } else if (k.isDictionary()) {
                        error = checkMcrOrObjr((PdfDictionary) k);
                    } else if (k.isArray()) {
                        PdfArray arr = (PdfArray) k;
                        for (int i = 0; i < arr.size() && error == null; i++) {
                            PdfObject item = arr.get(i);
                            if (item == null) continue;
                            if (item.isNumber()) {
                                if (se.getAsDictionary(PdfName.Pg) == null) {
                                    error = "MCID reference without /Pg on structure element";
                                }
                            } else if (item.isDictionary()) {
                                error = checkMcrOrObjr((PdfDictionary) item);
                            }
                        }
                    } else {
                        error = "Structure element /K has unexpected type";
                    }
                }
            }

            // PAC treats "Logical structure syntax" as an errors-only checkpoint: it reports
            // violations but never a positive count. When the tree is clean the checkpoint
            // rolls up to NOT_APPLICABLE — matching PAC's `-/-/-` on that row.
            if (error != null) {
                out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, page, null, error));
            }
        });
        return out;
    }

    private static String checkMcrOrObjr(PdfDictionary d) {
        PdfName type = d.getAsName(PdfName.Type);
        if (new PdfName("MCR").equals(type)) {
            if (d.getAsNumber(new PdfName("MCID")) == null || d.getAsDictionary(PdfName.Pg) == null) {
                return "MCR is missing /MCID or /Pg";
            }
            return null;
        } else if (PdfName.OBJR.equals(type)) {
            if (d.get(PdfName.Obj) == null) {
                return "OBJR is missing /Obj";
            }
            return null;
        }
        // Either an untyped child struct element (legal — /K may contain nested StructElems)
        // or an explicitly typed StructElem dictionary. Both are valid here.
        if (type == null || new PdfName("StructElem").equals(type)) return null;
        return "Dictionary in /K is not an MCR or OBJR";
    }
}
