package com.netralabs.logicalstructure.structureelements.notes;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;

import java.util.ArrayList;
import java.util.List;

import static com.netralabs.domain.PDFUACheckpoint.ID_NOTE;
import static com.netralabs.logicalstructure.structureelements.StructUtil.normRole;
import static com.netralabs.logicalstructure.structureelements.StructUtil.walk;

public class ValidateNoteIdPresence implements Rule {

    private static final PdfName ID = new PdfName("ID");

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        validate(ctx.pdf(), out);
        return out;
    }

    public void validate(PdfDocument pdf, List<FindingDTO> out) {
        final boolean[] sawNote = {false};
        walk(pdf, (PdfStructElem el) -> {
            if (!"Note".equals(normRole(pdf, el))) return;
            sawNote[0] = true;
            PdfDictionary dict = el.getPdfObject();
            if (dict == null || !dict.containsKey(ID) || dict.getAsString(ID) == null) {
                out.add(new FindingDTO(Severity.ERROR, ID_NOTE, StructUtils.pageNumOf(pdf, dict), null,
                        "Note structure element missing /ID"));
            }
        });
        if (!sawNote[0]) out.add(
                new FindingDTO(Severity.IGNORED, ID_NOTE, 0, null)
        );
    }
}
