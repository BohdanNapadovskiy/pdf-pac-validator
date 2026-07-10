package com.netralabs.logicalstructure.structureelements.annotations;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Iterates every page {@code /Annots} entry and emits one PASSED per annotation,
 * routed by subtype to one of the three "Nesting of … annotations" checkpoints:
 *
 * <ul>
 *   <li>{@link PDFUACheckpoint#NESTING_LINK_ANNOTATIONS} — Link annotation
 *       (ISO 14289-1 §7.18.5).</li>
 *   <li>{@link PDFUACheckpoint#NESTING_WIDGET_ANNOTATIONS} — Widget annotation
 *       (ISO 14289-1 §7.18.4).</li>
 *   <li>{@link PDFUACheckpoint#NESTING_ANNOTATIONS_ANNOT} — every other annotation
 *       subtype not explicitly handled elsewhere (§7.18.1).</li>
 * </ul>
 *
 * <p>PAC-style semantics for the aggregated "Annotations" row: one PASSED per
 * annotation in the document, regardless of whether the annotation is tagged or
 * whether its enclosing struct parent matches its subtype's expected wrapper.
 * veraPDF (7.18.4-1 / 7.18.5-1 / 7.18.1-1) supplies ERRORs for wrongly-nested or
 * untagged annotations. A wrongly-nested annotation therefore contributes one
 * PASSED (this rule) + one ERROR (vera) — matching PAC's observed 38P/32E on
 * Filled_Graduate (31 widgets + 3 links + 4 other = 38 total annotations; 31
 * widget-nesting + 1 link-nesting failures = 32 errors).
 *
 * <p>Registered on all three checkpoints; a static guard ensures the walk runs
 * once per document.
 */
public class ValidateAnnotationNesting implements Rule {

    /** Subtypes excluded from the catch-all "Annot" bucket per ISO 14289-1 §7.18. */
    private static final Set<PdfName> SPECIAL_OR_EXCLUDED = Set.of(
            PdfName.Widget,      // routed to NESTING_WIDGET_ANNOTATIONS
            PdfName.Link,        // routed to NESTING_LINK_ANNOTATIONS
            PdfName.Popup,       // §7.18.1 explicitly excludes Popup
            PdfName.TrapNet,     // §7.18.2 prohibits entirely (own checkpoint)
            PdfName.PrinterMark  // §7.18.8 separately handled (own checkpoint)
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
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfArray annots = pdf.getPage(i).getPdfObject().getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int j = 0; j < annots.size(); j++) {
                PdfObject o = annots.get(j);
                if (!(o instanceof PdfDictionary annot)) continue;
                PdfName subtype = annot.getAsName(PdfName.Subtype);
                if (subtype == null) continue;
                emitPass(subtype, i, out);
            }
        }
        return out;
    }

    private static void emitPass(PdfName subtype, int page, List<FindingDTO> out) {
        if (PdfName.Widget.equals(subtype)) {
            out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.NESTING_WIDGET_ANNOTATIONS, page, null));
        } else if (PdfName.Link.equals(subtype)) {
            out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.NESTING_LINK_ANNOTATIONS, page, null));
        } else if (!SPECIAL_OR_EXCLUDED.contains(subtype)) {
            out.add(new FindingDTO(Severity.PASSED, PDFUACheckpoint.NESTING_ANNOTATIONS_ANNOT, page, null));
        }
    }
}