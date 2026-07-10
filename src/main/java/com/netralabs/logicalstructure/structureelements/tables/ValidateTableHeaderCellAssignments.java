package com.netralabs.logicalstructure.structureelements.tables;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.TABLE_HEADER_CELL_ASSIGNMENTS;

/**
 * ISO 14289-1 §7.5 — every TH cell must carry a Table {@code /Scope} attribute
 * ({@code /Row}, {@code /Column} or {@code /Both}) so its role in the header
 * association graph is unambiguous.
 *
 * <p>PAC-style semantics: one PASSED per TH-with-Scope, one ERROR per TH-without-Scope.
 * TDs are not checked — their association is either explicit ({@code /Headers}) or
 * implicit via any TH in the enclosing Table, which is already covered.
 */
public class ValidateTableHeaderCellAssignments implements Rule {

    private static final PdfName SCOPE = new PdfName("Scope");
    private static final PdfName OWNER = new PdfName("O");
    private static final PdfName TABLE_OWNER = new PdfName("Table");

    private static volatile PdfDocument done;

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        synchronized (ValidateTableHeaderCellAssignments.class) {
            if (done == pdf) return new ArrayList<>();
            done = pdf;
        }

        List<FindingDTO> out = new ArrayList<>();
        StructWalk.walk(pdf, elem -> {
            if (!"TH".equals(StructWalk.normRole(pdf, elem))) return;
            PdfDictionary dict = elem.getPdfObject();
            int page = StructUtils.pageNumOf(pdf, dict);
            if (hasTableScope(dict)) {
                out.add(new FindingDTO(Severity.PASSED, TABLE_HEADER_CELL_ASSIGNMENTS, page, null));
            } else {
                out.add(new FindingDTO(Severity.ERROR, TABLE_HEADER_CELL_ASSIGNMENTS, page, null,
                        "Table header cell has no Scope attribute"));
            }
        });
        return out;
    }

    private static boolean hasTableScope(PdfDictionary cell) {
        PdfObject a = cell.get(PdfName.A);
        if (a == null) return false;
        if (a instanceof PdfDictionary d) return isTableOwnerWithScope(d);
        if (a instanceof PdfArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                PdfObject item = arr.get(i);
                if (item instanceof PdfDictionary d && isTableOwnerWithScope(d)) return true;
            }
        }
        return false;
    }

    private static boolean isTableOwnerWithScope(PdfDictionary d) {
        return TABLE_OWNER.equals(d.getAsName(OWNER)) && d.get(SCOPE) != null;
    }
}
