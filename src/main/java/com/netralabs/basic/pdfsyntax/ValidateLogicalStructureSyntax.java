package com.netralabs.basic.pdfsyntax;

import com.itextpdf.kernel.pdf.*;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.netralabs.basic.pdfsyntax.StructUtils.structTreeRoot;
import static com.netralabs.basic.pdfsyntax.StructUtils.walkStructure;
import static com.netralabs.domain.PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX;

public class ValidateLogicalStructureSyntax implements Rule {

    private static final Set<String> STD_ROLES = Set.of(
            "Document", "Part", "Art", "Sect", "Div", "P", "H", "H1", "H2", "H3", "H4", "H5", "H6",
            "L", "LI", "Lbl", "LBody", "Table", "TR", "TH", "TD", "THead", "TBody", "TFoot",
            "Figure", "Caption", "Formula", "Link", "Note", "Annot", "Span", "Quote", "Code"
    );

    @Override
    public EnumSet<Phase> phases() {
        return EnumSet.of(Phase.DOCUMENT);
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();
        PdfDictionary str = structTreeRoot(pdf);
        if (str == null) return out; // skip

        PdfDictionary roleMap = str.getAsDictionary(new PdfName("RoleMap"));

        walkStructure(pdf, (parent, se) -> {
            // 1) Role /S must exist and be valid or mapped
            PdfName role = se.getAsName(PdfName.S);
            if (role == null) {
                out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
            } else if (!isValidRole(role, roleMap)) {
                out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
            } else {
                out.add(new FindingDTO(Severity.PASSED, LOGICAL_STRUCTURE_SYNTAX, 0, null));
            }

            // 2) Validate /K content references (syntax only)
            PdfObject k = se.get(PdfName.K);
            if (k == null) return;

            if (k.isNumber()) {
                // MCID number requires /Pg on the same StructElem (ISO 32000-2, 28.9.3)
                if (se.getAsDictionary(PdfName.Pg) == null) {
                    out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
                } else {
                    out.add(new FindingDTO(Severity.PASSED, LOGICAL_STRUCTURE_SYNTAX, 0, null));
                }
            } else if (k.isDictionary()) {
                checkMcrOrObjr((PdfDictionary) k, out);
            } else if (k.isArray()) {
                PdfArray arr = (PdfArray) k;
                for (int i = 0; i < arr.size(); i++) {
                    PdfObject item = arr.get(i);
                    if (item == null) continue;
                    if (item.isNumber()) {
                        if (se.getAsDictionary(PdfName.Pg) == null) {
                            out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
                        }
                    } else if (item.isDictionary()) {
                        checkMcrOrObjr((PdfDictionary) item, out);
                    } // other types are illegal
                }
            } else {
                out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
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

    private static void checkMcrOrObjr(PdfDictionary d, List<FindingDTO> out) {
        PdfName type = d.getAsName(PdfName.Type);
        if (new PdfName("MCR").equals(type)) {
            if (d.getAsNumber(new PdfName("MCID")) == null || d.getAsDictionary(PdfName.Pg) == null) {
                out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
            } else {
                out.add(new FindingDTO(Severity.PASSED, LOGICAL_STRUCTURE_SYNTAX, 0, null));
            }
        } else if (PdfName.OBJR.equals(type)) {
            if (d.get(PdfName.Obj) == null) {
                out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
            } else {
                out.add(new FindingDTO(Severity.PASSED, LOGICAL_STRUCTURE_SYNTAX, 0, null));
            }
        } else {
            out.add(new FindingDTO(Severity.ERROR, LOGICAL_STRUCTURE_SYNTAX, 0, null));
        }
    }
}
