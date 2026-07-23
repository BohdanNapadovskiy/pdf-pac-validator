package com.netralabs.report.pac;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;
import com.netralabs.wcag.WCAGCriterion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the flat, failure-only detailed report — one {@link DetailedIssueDTO}
 * per (checkpoint, issue code) with a {@code details[]} array of per-instance
 * page + bounding-box entries.
 * <p>
 * Groups findings under two report types: "PDF/UA" (all PDF/UA-reporting
 * checkpoints) and "WCAG" (checkpoints referenced by any {@link WCAGCriterion}
 * source, deduplicated so a shared checkpoint appears once per type).
 */
public final class DetailedReportBuilder {

    private DetailedReportBuilder() {}

    /**
     * Convenience overload — computes {@code cropBoxRanges} from the open PDF.
     * Prefer {@link #build(List, List)} when the PDF has already been closed
     * (e.g. detailed report is built lazily from cached findings).
     */
    public static DetailedBodyDTO build(PdfDocument pdf, List<FindingDTO> findings) {
        return build(findings, buildCropBoxRanges(pdf));
    }

    /**
     * Build the detailed body from a findings list and a pre-computed set of
     * {@code cropBoxRanges}. Doesn't touch the PDF — safe to call after the
     * source document has been closed.
     */
    public static DetailedBodyDTO build(List<FindingDTO> findings, List<CropBoxRangeDTO> cropBoxRanges) {
        DetailedBodyDTO body = new DetailedBodyDTO();
        body.setIssues(buildTypeSections(findings));
        body.setCropBoxRanges(cropBoxRanges == null ? List.of() : cropBoxRanges);
        return body;
    }

    private static List<DetailedTypeDTO> buildTypeSections(List<FindingDTO> findings) {
        Set<PDFUACheckpoint> wcagCheckpoints = collectWcagCheckpoints();

        Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint = new LinkedHashMap<>();
        for (FindingDTO f : findings) {
            PDFUACheckpoint cp = f.getCheckpoint();
            if (cp == null) continue;
            Severity sev = f.getSeverity();
            if (sev != Severity.ERROR && sev != Severity.WARNING) continue;
            byCheckpoint.computeIfAbsent(cp, k -> new ArrayList<>()).add(f);
        }

        List<DetailedTypeDTO> sections = new ArrayList<>();
        DetailedTypeDTO pdfUa = new DetailedTypeDTO();
        pdfUa.setType("PDF/UA");
        DetailedTypeDTO wcag = new DetailedTypeDTO();
        // Matches PAC's simple.json section type — the WCAG bucket is labelled
        // by its root checkId, not the display name.
        wcag.setType(PacCheckId.WCAG_ROOT);

        for (Map.Entry<PDFUACheckpoint, List<FindingDTO>> e : byCheckpoint.entrySet()) {
            PDFUACheckpoint cp = e.getKey();
            DetailedIssueDTO issue = toIssue(cp, e.getValue());
            if ("PDF/UA".equals(cp.getReportName())) {
                pdfUa.getIssues().add(issue);
            }
            if (wcagCheckpoints.contains(cp)) {
                wcag.getIssues().add(issue);
            }
        }
        if (!pdfUa.getIssues().isEmpty()) sections.add(pdfUa);
        if (!wcag.getIssues().isEmpty()) sections.add(wcag);
        return sections;
    }

    private static Set<PDFUACheckpoint> collectWcagCheckpoints() {
        EnumSet<PDFUACheckpoint> set = EnumSet.noneOf(PDFUACheckpoint.class);
        for (WCAGCriterion c : WCAGCriterion.values()) {
            set.addAll(c.getSources());
        }
        return set;
    }

    private static DetailedIssueDTO toIssue(PDFUACheckpoint cp, List<FindingDTO> findings) {
        String fallbackCaption = (cp.getErrorMessage() != null && !cp.getErrorMessage().isEmpty())
                ? cp.getErrorMessage() : cp.getElement();
        PacIssueId.IssueCode code = PacIssueId.forCheckpoint(cp, fallbackCaption);

        int errors = 0, warnings = 0;
        List<IssueDetailDTO> details = new ArrayList<>();
        for (FindingDTO f : findings) {
            Severity sev = f.getSeverity();
            if (sev == Severity.ERROR) errors++;
            else if (sev == Severity.WARNING) warnings++;
            IssueDetailDTO detail = toDetail(f);
            if (detail != null) details.add(detail);
        }
        PacSeverity sev = errors > 0 ? PacSeverity.ERROR : PacSeverity.WARNING;

        DetailedIssueDTO issue = new DetailedIssueDTO();
        issue.setIssueId(code.id());
        issue.setSeverity(sev.label());
        issue.setSeverityId(sev.id());
        issue.setCheckId(PacCheckId.forLeaf(cp));
        issue.setCaption(code.caption());
        issue.setCount(errors + warnings);
        issue.setDetails(details);
        return issue;
    }

    private static IssueDetailDTO toDetail(FindingDTO f) {
        // page 0 or null in FindingDTO means "no page" — skip; PAC only emits
        // details with a valid page + bbox.
        if (f.getPage() == null || f.getPage() == 0 || f.getBBox() == null) return null;
        BBoxDTO b = f.getBBox();
        // BBoxDTO uses (top, left, height, width) in PDF user-space (origin bottom-left);
        // convert to PAC's (top, bottom, left, right).
        double top = b.getTop();
        double left = b.getLeft();
        double bottom = top - b.getHeight();
        double right = left + b.getWidth();
        // pageIndex is 0-based in PAC's export; our FindingDTO.page is 1-based.
        int pageIndex = f.getPage() - 1;
        return new IssueDetailDTO(pageIndex, new RectangleDTO(top, bottom, left, right));
    }

    /** Compute contiguous same-CropBox page ranges for the open document. */
    public static List<CropBoxRangeDTO> buildCropBoxRanges(PdfDocument pdf) {
        List<CropBoxRangeDTO> ranges = new ArrayList<>();
        if (pdf == null) return ranges;
        int pageCount = pdf.getNumberOfPages();
        if (pageCount <= 0) return ranges;

        Rectangle currentBox = null;
        int rangeStart = 0;
        for (int i = 1; i <= pageCount; i++) {
            Rectangle box = pdf.getPage(i).getCropBox();
            if (currentBox == null) {
                currentBox = box;
                rangeStart = i - 1;
            } else if (!sameBox(currentBox, box)) {
                ranges.add(new CropBoxRangeDTO(rangeStart, i - 2, toRect(currentBox)));
                currentBox = box;
                rangeStart = i - 1;
            }
        }
        if (currentBox != null) {
            ranges.add(new CropBoxRangeDTO(rangeStart, pageCount - 1, toRect(currentBox)));
        }
        return ranges;
    }

    private static boolean sameBox(Rectangle a, Rectangle b) {
        return a.getTop() == b.getTop()
                && a.getBottom() == b.getBottom()
                && a.getLeft() == b.getLeft()
                && a.getRight() == b.getRight();
    }

    private static RectangleDTO toRect(Rectangle r) {
        return new RectangleDTO(r.getTop(), r.getBottom(), r.getLeft(), r.getRight());
    }
}
