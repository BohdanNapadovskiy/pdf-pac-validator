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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netralabs.domain.PDFUACheckpoint.TABLE_REGULARITY;

/**
 * ISO 14289-1 §7.2 / PAC "Table regularity" — checks that every row of a Table has
 * the same effective column count as the <em>first</em> row, accounting for both
 * {@code /ColSpan} and {@code /RowSpan}. A cell with {@code /RowSpan=N} occupies
 * its column indices in the subsequent {@code N-1} rows, so those rows' width
 * includes the blocked columns even when their own {@code /K} contains fewer
 * explicit cells.
 *
 * <p>Emission (verified against PAC on OP_AoD 7P/14W and Filled_Graduate 51P/9W):
 * <ul>
 *   <li>One PASSED per Table where every row's effective width matches the
 *       first row's width.</li>
 *   <li>One WARNING ("Irregular table row") per row whose effective width
 *       differs from the first row's width.</li>
 * </ul>
 *
 * <p>PAC uses the first row's column count as the canonical width (typically the
 * header row establishes the table's schema). Using MAX instead under-counts:
 * on OP_AoD Table 5 [4, 4, 6, 6, 6, 6, 6] with first-row-width = 4, PAC flags all
 * 5 wider rows as irregular (5W); MAX-based counting flags only the 2 short rows.
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

        int[] colCounts = layOutRows(pdf, rows);
        int firstWidth = colCounts[0];

        boolean allMatch = true;
        for (int c : colCounts) if (c != firstWidth) { allMatch = false; break; }

        int tablePage = StructUtils.pageNumOf(pdf, table.getPdfObject());
        if (allMatch) {
            out.add(new FindingDTO(Severity.PASSED, TABLE_REGULARITY, tablePage, null));
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (colCounts[i] == firstWidth) continue;
                int rowPage = StructUtils.pageNumOf(pdf, rows.get(i).getPdfObject());
                if (rowPage <= 0) rowPage = tablePage;
                out.add(new FindingDTO(Severity.WARNING, TABLE_REGULARITY, rowPage, null,
                        "Irregular table row"));
            }
        }
    }

    /**
     * Compute the effective column width of each row using a per-column occupancy tracker.
     * A cell with {@code /RowSpan=N} occupies the same column indices in the next {@code N-1}
     * rows, so those rows' effective width includes those blocked columns even when their
     * own {@code /K} contains fewer cells.
     */
    private static int[] layOutRows(PdfDocument pdf, List<PdfStructElem> rows) {
        int[] widths = new int[rows.size()];
        // pending[col] = remaining rows that column stays occupied by a spanning cell from above.
        Map<Integer, Integer> pending = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            widths[i] = placeRow(pdf, rows.get(i), pending);
            // Advance one row: decrement each pending column, drop those that hit 0.
            pending.entrySet().removeIf(e -> {
                int remaining = e.getValue() - 1;
                if (remaining <= 0) return true;
                e.setValue(remaining);
                return false;
            });
        }
        return widths;
    }

    /**
     * Greedily place this row's cells into the first free column indices (skipping columns
     * still occupied by row-spans from above), recording new row-span occupancies. Returns
     * the row's effective width (highest occupied column index + 1).
     */
    private static int placeRow(PdfDocument pdf, PdfStructElem row, Map<Integer, Integer> pending) {
        List<IStructureNode> kids = row.getKids();
        int col = 0;
        int maxCol = -1;
        if (kids != null) {
            for (IStructureNode k : kids) {
                if (!(k instanceof PdfStructElem se)) continue;
                String r = StructWalk.normRole(pdf, se);
                if (!"TH".equals(r) && !"TD".equals(r)) continue;
                while (pending.containsKey(col)) col++;
                int cs = spanOf(se.getPdfObject(), COL_SPAN);
                int rs = spanOf(se.getPdfObject(), ROW_SPAN);
                for (int c = 0; c < cs; c++) {
                    int colIdx = col + c;
                    if (rs > 1) pending.put(colIdx, rs - 1);
                }
                col += cs;
                maxCol = col - 1;
            }
        }
        int width = maxCol + 1;
        for (int occCol : pending.keySet()) if (occCol + 1 > width) width = occCol + 1;
        return width;
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
