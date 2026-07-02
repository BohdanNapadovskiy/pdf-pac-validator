package com.netralabs.logicalstructure.structureelements.annotations;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.logicalstructure.structureelements.StructUtil;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Walks the structure tree and emits one PASSED finding per correctly-nested annotation
 * on the three "Nesting of … annotations" checkpoints:
 *
 * <ul>
 *   <li>{@link PDFUACheckpoint#NESTING_LINK_ANNOTATIONS} — Link annotation directly under a
 *       Link structure element (ISO 14289-1 §7.18.5).</li>
 *   <li>{@link PDFUACheckpoint#NESTING_WIDGET_ANNOTATIONS} — Widget annotation directly under a
 *       Form structure element (ISO 14289-1 §7.18.4).</li>
 *   <li>{@link PDFUACheckpoint#NESTING_ANNOTATIONS_ANNOT} — other annotation subtypes nested
 *       inside an Annot structure element (ISO 14289-1 §7.18.1).</li>
 * </ul>
 *
 * <p>The rule emits <strong>PASSED only</strong>. veraPDF (7.18.4-1 / 7.18.5-1 / 7.18.1-1)
 * continues to drive ERRORs for incorrectly-nested or orphaned annotations — emitting both
 * here would double-count. The combined effect matches PAC's column ({passes from native}
 * + {errors from veraPDF}).
 *
 * <p>Registered on all three checkpoints; a static guard ensures the walk runs once per
 * document (same pattern as {@code RoleMapValidatorRule}).
 */
public class ValidateAnnotationNesting implements Rule {

    /** Subtypes excluded from the catch-all "Annot" nesting check per ISO 14289-1 §7.18. */
    private static final Set<PdfName> SPECIAL_OR_EXCLUDED = Set.of(
            PdfName.Widget,      // covered by §7.18.4
            PdfName.Link,        // covered by §7.18.5
            PdfName.Popup,       // §7.18.1 explicitly excludes Popup
            PdfName.TrapNet,     // §7.18.2 prohibits entirely
            PdfName.PrinterMark  // §7.18.8 separately handled
    );

    private static volatile PdfDocument done;

    @Override
    public List<FindingDTO> run(Context ctx) {
        PdfDocument pdf = ctx.pdf();
        synchronized (ValidateAnnotationNesting.class) {
            if (done == pdf) return new ArrayList<>();
            done = pdf;
        }

        List<FindingDTO> out = new ArrayList<>();
        StructUtil.walk(pdf, elem -> visitElement(pdf, elem, out));
        return out;
    }

    private static void visitElement(PdfDocument pdf, PdfStructElem elem, List<FindingDTO> out) {
        List<IStructureNode> kids = elem.getKids();
        if (kids == null) return;
        String parentRole = StructUtil.normRole(pdf, elem);
        int page = StructUtils.pageNumOf(pdf, elem.getPdfObject());

        for (IStructureNode kid : kids) {
            if (!(kid instanceof PdfObjRef ref)) continue;
            PdfObject obj = ref.getReferencedObject();
            if (!(obj instanceof PdfDictionary annot)) continue;
            PdfName subtype = annot.getAsName(PdfName.Subtype);
            if (subtype == null) continue;

            if (PdfName.Widget.equals(subtype)) {
                if ("Form".equals(parentRole)) {
                    out.add(new FindingDTO(Severity.PASSED,
                            PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS, page, null));
                }
            } else if (PdfName.Link.equals(subtype)) {
                if ("Link".equals(parentRole)) {
                    out.add(new FindingDTO(Severity.PASSED,
                            PDFUACheckpoint.NESTING_LINK_ANNOTATIONS, page, null));
                }
            } else if (!SPECIAL_OR_EXCLUDED.contains(subtype)) {
                if ("Annot".equals(parentRole)) {
                    out.add(new FindingDTO(Severity.PASSED,
                            PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT, page, null));
                }
            }
        }
    }
}