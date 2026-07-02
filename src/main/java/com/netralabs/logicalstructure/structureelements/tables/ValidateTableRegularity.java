package com.netralabs.logicalstructure.structureelements.tables;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructWalk;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.TABLE_REGULARITY;

/**
 * ISO 14289-1 §7.2 / PAC "Table regularity" — checks that every row of a Table has
 * the same number of columns, accounting for {@code /ColSpan} attributes.
 *
 * <p>PAC-style emission (verified against Complex_Presentation_Sample: 26 P / 24 W):
 * <ul>
 *   <li>One PASSED per regular Table (all TRs have identical column counts).</li>
 *   <li>One WARNING per irregular TR — one per row that does not match the table's
 *       maximum column count.</li>
 * </ul>
 */
public class ValidateTableRegularity implements Rule {

    private static final PdfName COL_SPAN = new PdfName("ColSpan");
    private static final PdfName ROW_SPAN = new PdfName("RowSpan");
    private static final PdfName OWNER = new PdfName("O");
    private static final PdfName TABLE_OWNER = new PdfName("Table");

    private static volatile PdfDocument done;

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        synchronized (ValidateTableRegularity.class) {
            if (done == pdf) return new ArrayList<>();
            done = pdf;
        }

        List<FindingDTO> out = new ArrayList<>();
        StructWalk.walk(pdf, elem -> {
            if (!"Table".equals(StructWalk.normRole(pdf, elem))) return;
            checkTable(pdf, elem, out);
        });
        return out;
    }

    private static void checkTable(PdfDocument pdf, PdfStructElem table, List<FindingDTO> out) {
        List<PdfStructElem> rows = new ArrayList<>();
        collectRows(pdf, table, rows);
        if (rows.isEmpty()) return;

        int[] colCounts = new int[rows.size()];
        int carriedFromAbove = 0;
        int max = 0;
        for (int i = 0; i < rows.size(); i++) {
            int[] result = countColumnsAndCarry(pdf, rows.get(i));
            colCounts[i] = result[0] + carriedFromAbove;
            carriedFromAbove = result[1];
            if (colCounts[i] > max) max = colCounts[i];
        }

        boolean allEqual = true;
        for (int c : colCounts) if (c != max) { allEqual = false; break; }

        int tablePage = StructUtils.pageNumOf(pdf, table.getPdfObject());
        if (allEqual) {
            out.add(new FindingDTO(Severity.PASSED, TABLE_REGULARITY, tablePage, null));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (colCounts[i] == max) continue;
            int rowPage = StructUtils.pageNumOf(pdf, rows.get(i).getPdfObject());
            if (rowPage <= 0) rowPage = tablePage;
            out.add(new FindingDTO(Severity.WARNING, TABLE_REGULARITY, rowPage, null,
                    "Table rows shall have the same number of columns (taking into account column spans)"));
        }
    }

    private static void collectRows(PdfDocument pdf, IStructureNode node, List<PdfStructElem> out) {
        List<IStructureNode> kids = node.getKids();
        if (kids == null) return;
        for (IStructureNode k : kids) {
            if (!(k instanceof PdfStructElem se)) continue;
            String r = StructWalk.normRole(pdf, se);
            if ("TR".equals(r)) out.add(se);
            else if (!"Table".equals(r)) collectRows(pdf, se, out);
        }
    }

    /** @return {@code [cellCols, rowsSpannedAhead]} — cell col span sum for this row,
     *  plus how many columns this row occupies in the NEXT row via {@code /RowSpan>1}. */
    private static int[] countColumnsAndCarry(PdfDocument pdf, PdfStructElem row) {
        int cols = 0, carry = 0;
        List<IStructureNode> kids = row.getKids();
        if (kids == null) return new int[]{0, 0};
        for (IStructureNode k : kids) {
            if (!(k instanceof PdfStructElem se)) continue;
            String r = StructWalk.normRole(pdf, se);
            if ("TH".equals(r) || "TD".equals(r)) {
                int cs = spanOf(se.getPdfObject(), COL_SPAN);
                cols += cs;
                if (spanOf(se.getPdfObject(), ROW_SPAN) > 1) carry += cs;
            }
        }
        return new int[]{cols, carry};
    }

    private static int spanOf(PdfDictionary cell, PdfName key) {
        PdfObject a = cell.get(PdfName.A);
        if (a instanceof PdfDictionary d) {
            Integer n = readTableSpan(d, key);
            if (n != null) return n;
        } else if (a instanceof PdfArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                PdfObject item = arr.get(i);
                if (item instanceof PdfDictionary d) {
                    Integer n = readTableSpan(d, key);
                    if (n != null) return n;
                }
            }
        }
        return 1;
    }

    private static Integer readTableSpan(PdfDictionary d, PdfName key) {
        if (!TABLE_OWNER.equals(d.getAsName(OWNER))) return null;
        PdfObject v = d.get(key);
        if (v instanceof PdfNumber n) {
            int i = n.intValue();
            return i > 0 ? i : null;
        }
        return null;
    }
}
