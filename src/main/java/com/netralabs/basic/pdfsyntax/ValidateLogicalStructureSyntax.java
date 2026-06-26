package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.netralabs.basic.pdfsyntax.StructUtils.pageNumOf;
import static com.netralabs.basic.pdfsyntax.StructUtils.structTreeRoot;
import static com.netralabs.basic.pdfsyntax.StructUtils.walkStructure;
import static com.netralabs.domain.PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX;

public class ValidateLogicalStructureSyntax implements Rule {

    private static final Set<String> STD_ROLES = Set.of(
            "Document", "Part", "Art", "Sect", "Div", "P", "H", "H1", "H2", "H3", "H4", "H5", "H6",
            "L", "LI", "Lbl", "LBody", "Table", "TR", "TH", "TD", "THead", "TBody", "TFoot",
            "Figure", "Caption", "Formula", "Link", "Note", "Annot", "Span", "Quote", "Code",
            "Reference", "BibEntry", "BlockQuote", "TOC", "TOCI", "Index", "Private",
            "Ruby", "RB", "RT", "RP", "Warichu", "WP", "WT", "Form"
    );

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        PdfDictionary str = structTreeRoot(pdf);
        if (str == null) return out; // skip

        PdfDictionary roleMap = str.getAsDictionary(new PdfName("RoleMap"));

        walkStructure(pdf, (parent, se) -> {
            int page = pageNumOf(pdf, se);
            String error = null;

            // 1) Role /S must exist and be valid or mapped
            PdfName role = se.getAsName(PdfName.S);
            if (role == null) {
                error = "Structure element missing /S role";
            } else if (!isValidRole(role, roleMap)) {
                error = "Structure element role is not standard or mapped";
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

            if (error != null) {
                out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, page, null, error));
            } else {
                out.add(new FindingDTO(Severity.PASSED, LOGICAL_STRUCTURE_SYNTAX, page, null));
            }
        });
        return out;
    }

    private static boolean isValidRole(PdfName role, PdfDictionary roleMap) {
        String r = role.getValue();
        if (STD_ROLES.contains(r)) return true;
        if (roleMap == null) return false;
        PdfName mapped = roleMap.getAsName(role);
        return mapped != null && STD_ROLES.contains(mapped.getValue());
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
