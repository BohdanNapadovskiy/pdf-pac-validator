package com.netralabs.logicalstructure.structureelements.notes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;

import static com.netralabs.domain.PDFUACheckpoint.UNIQUE_ID_ENTRIES;
import static com.netralabs.logicalstructure.structureelements.StructUtil.walk;
import static com.netralabs.logicalstructure.structureelements.StructWalk.normRole;

public class ValidateNoteIdUniqueness implements Rule  {
    private static final PdfName ID = new PdfName("ID");

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        validate(ctx.pdf(), out); return out;
    }

    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        // Two-pass: first collect notes + detect duplicate IDs; then emit one finding per Note
        // so counts mirror PAC's per-element granularity.
        List<PdfStructElem> notes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> dups = new HashSet<>();

        walk(pdf, (PdfStructElem el) -> {
            if (!"Note".equals(normRole(pdf, el))) return;
            notes.add(el);
            PdfString val = el.getPdfObject().getAsString(ID);
            if (val == null) return; // presence checked by ValidateNoteIdPresence
            String s = val.getValue();
            if (!seen.add(s)) dups.add(s);
        });

        if (notes.isEmpty()) {
            out.add(new FindingDTO(Severity.IGNORED, UNIQUE_ID_ENTRIES, 0, null));
            return;
        }
        for (PdfStructElem el : notes) {
            PdfString val = el.getPdfObject().getAsString(ID);
            int page = StructUtils.pageNumOf(pdf, el.getPdfObject());
            boolean isDup = val != null && dups.contains(val.getValue());
            if (isDup) {
                out.add(new FindingDTO(Severity.ERROR, UNIQUE_ID_ENTRIES, page, null,
                        "Duplicate /ID on Note structure element"));
            } else {
                out.add(new FindingDTO(Severity.PASSED, UNIQUE_ID_ENTRIES, page, null));
            }
        }
    }


}
