package com.netralabs.logicalstructure.structureelements.notes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.domain.Phase;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.*;

import static com.netralabs.domain.PDFUACheckpoint.UNIQUE_ID_ENTRIES;
import static com.netralabs.logicalstructure.structureelements.StructUtil.walk;
import static com.netralabs.logicalstructure.structureelements.StructWalk.normRole;

public class ValidateNoteIdUniqueness implements Rule  {
    private static final PdfName ID = new PdfName("ID");

    @Override
    public EnumSet<Phase> phases() { return EnumSet.of(Phase.DOCUMENT); }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        validate(ctx.pdf(), out); return out;
    }

    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        Set<String> seen = new HashSet<>();
        Set<String> dups = new LinkedHashSet<>();
        final boolean[] sawNote = {false};

        walk(pdf, (PdfStructElem el) -> {
            if (!"Note".equals(normRole(pdf, el))) return;
            sawNote[0] = true;
            PdfString val = el.getPdfObject().getAsString(ID);
            if (val == null) return; // presence checked by previous rule
            String s = val.getValue();
            if (!seen.add(s)) dups.add(s);
        });

        if (!sawNote[0]) {
            out.add(new FindingDTO(Severity.IGNORED, UNIQUE_ID_ENTRIES, 0, null));
        } else if (!dups.isEmpty()) {
            out.add(new FindingDTO(Severity.ERROR, UNIQUE_ID_ENTRIES, 0, null));
        } else {
            out.add(new FindingDTO(Severity.PASSED, UNIQUE_ID_ENTRIES, 0, null));
        }
    }


}
