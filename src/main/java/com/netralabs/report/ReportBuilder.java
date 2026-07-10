package com.netralabs.report;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.vera.VeraRuleMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportBuilder {

  private ReportBuilder() {}

  public static ReportDTO build(String documentPath, List<FindingDTO> findings) {
    return build(null, documentPath, findings);
  }

  public static ReportDTO build(PdfDocument pdf, String documentPath, List<FindingDTO> findings) {
    Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint = new LinkedHashMap<>();
    for (PDFUACheckpoint cp : PDFUACheckpoint.values()) {
      byCheckpoint.put(cp, new ArrayList<>());
    }
    for (FindingDTO f : findings) {
      if (f.getCheckpoint() != null) {
        byCheckpoint.get(f.getCheckpoint()).add(f);
      }
    }

    // 4-level tree: Category → SubCategory → (optional Group) → Checkpoint
    // The null group key means "checkpoint sits directly under the subcategory".
    Map<String, Map<String, Map<String, List<CheckpointReportDTO>>>> tree = new LinkedHashMap<>();
    SummaryDTO summary = new SummaryDTO();

    for (Map.Entry<PDFUACheckpoint, List<FindingDTO>> e : byCheckpoint.entrySet()) {
      PDFUACheckpoint cp = e.getKey();
      // WCAG-only checkpoints don't belong in the PDF/UA report tree — they exist
      // solely to feed WCAGReportBuilder via WCAGCriterion source mappings.
      if (!"PDF/UA".equals(cp.getCategory())) continue;

      CheckpointReportDTO checkpointDto = toCheckpointDto(cp, e.getValue());
      tally(summary, checkpointDto.getStatus());

      String groupKey = cp.getGroup() == null ? "" : cp.getGroup();
      tree.computeIfAbsent(cp.getCategory(), k -> new LinkedHashMap<>())
          .computeIfAbsent(cp.getSubCategory(), k -> new LinkedHashMap<>())
          .computeIfAbsent(groupKey, k -> new ArrayList<>())
          .add(checkpointDto);
    }
    summary.setTotal(summary.getPassed() + summary.getWarning() + summary.getFailed()
        + summary.getNotApplicable() + summary.getNotImplemented());

    PdfUaSectionDTO pdfUa = new PdfUaSectionDTO();
    // summary stays local — used to drive info.compliant; not surfaced in JSON since Step 14.
    CountsDTO rootCounts = new CountsDTO();

    for (Map.Entry<String, Map<String, Map<String, List<CheckpointReportDTO>>>> catEntry : tree.entrySet()) {
      CategoryDTO category = new CategoryDTO();
      category.setName(catEntry.getKey());
      List<CheckpointStatus> subStatuses = new ArrayList<>();
      CountsDTO categoryCounts = new CountsDTO();

      for (Map.Entry<String, Map<String, List<CheckpointReportDTO>>> subEntry : catEntry.getValue().entrySet()) {
        SubCategoryDTO sub = new SubCategoryDTO();
        sub.setName(subEntry.getKey());

        List<CheckpointStatus> subItemStatuses = new ArrayList<>();
        CountsDTO subCounts = new CountsDTO();

        // Each subcategory has zero or more direct-leaf checkpoints and zero or more
        // named-group containers, all emitted into one unified `checkpoints[]` array.
        // Direct leaves come first, then each named group as a container CheckpointReportDTO
        // whose `element` is the group name and `checkpoints` holds the group's leaves.
        for (Map.Entry<String, List<CheckpointReportDTO>> grpEntry : subEntry.getValue().entrySet()) {
          String groupName = grpEntry.getKey();
          List<CheckpointReportDTO> cps = grpEntry.getValue();
          if (groupName.isEmpty()) {
            for (CheckpointReportDTO c : cps) {
              subItemStatuses.add(c.getStatus());
              if (c.getCounts() != null) subCounts.add(c.getCounts());
            }
            sub.getCheckpoints().addAll(cps);
          } else {
            CheckpointReportDTO container = new CheckpointReportDTO();
            container.setElement(groupName);
            List<CheckpointStatus> containerStatuses = new ArrayList<>();
            CountsDTO containerCounts = new CountsDTO();
            for (CheckpointReportDTO c : cps) {
              containerStatuses.add(c.getStatus());
              if (c.getCounts() != null) containerCounts.add(c.getCounts());
            }
            container.setStatus(rollup(containerStatuses));
            container.setCounts(containerCounts);
            container.setCheckpoints(cps);
            subItemStatuses.add(container.getStatus());
            subCounts.add(containerCounts);
            sub.getCheckpoints().add(container);
          }
        }

        sub.setStatus(rollup(subItemStatuses));
        sub.setCounts(subCounts);
        subStatuses.add(sub.getStatus());
        categoryCounts.add(subCounts);
        category.getSubCategories().add(sub);
      }
      category.setStatus(rollup(subStatuses));
      category.setCounts(categoryCounts);
      rootCounts.add(categoryCounts);
      pdfUa.getCategories().add(category);
    }
    pdfUa.setShortSummary(buildShortSummary(pdfUa.getCategories()));

    ReportsDTO reports = new ReportsDTO();
    reports.setDocument(documentPath);
    if (pdf != null) {
      DocumentInfoDTO info = DocumentInfoBuilder.build(pdf, documentPath);
      info.setCompliant(summary.getFailed() == 0);
      reports.setInfo(info);
    }
    reports.setPdfUa(pdfUa);

    ReportDTO report = new ReportDTO();
    report.setReports(reports);
    return report;
  }

  private static List<ShortSummaryEntryDTO> buildShortSummary(List<CategoryDTO> categories) {
    List<ShortSummaryEntryDTO> entries = new ArrayList<>();
    for (CategoryDTO cat : categories) {
      for (SubCategoryDTO sub : cat.getSubCategories()) {
        entries.add(new ShortSummaryEntryDTO(sub.getName(), sub.getStatus(), sub.getCounts()));
      }
    }
    return entries;
  }

  private static CheckpointReportDTO toCheckpointDto(PDFUACheckpoint cp, List<FindingDTO> findings) {
    CheckpointReportDTO dto = new CheckpointReportDTO();
    dto.setElement(cp.getElement());
    dto.setErrorMessage(emptyToNull(cp.getErrorMessage()));

    CountsDTO counts = new CountsDTO();
    List<FindingEntryDTO> entries = new ArrayList<>();
    for (FindingDTO f : findings) {
      Severity sev = f.getSeverity();
      if (sev == Severity.ERROR) {
        counts.setError(counts.getError() + 1);
        entries.add(toEntry(f, cp));
      } else if (sev == Severity.WARNING) {
        counts.setWarning(counts.getWarning() + 1);
        entries.add(toEntry(f, cp));
      } else if (sev == Severity.PASSED) {
        counts.setPassed(counts.getPassed() + 1);
      }
    }
    dto.setFindings(entries);
    dto.setCounts(counts);

    boolean hasRunnableNative = cp.getFactory() != null && !cp.getPhases().isEmpty();
    boolean covered = hasRunnableNative || VeraRuleMapping.covers(cp);

    if (counts.getError() > 0) dto.setStatus(CheckpointStatus.FAILED);
    else if (counts.getWarning() > 0) dto.setStatus(CheckpointStatus.WARNING);
    else if (counts.getPassed() > 0) dto.setStatus(CheckpointStatus.PASSED);
    else if (covered) dto.setStatus(CheckpointStatus.NOT_APPLICABLE);
    else dto.setStatus(CheckpointStatus.NOT_IMPLEMENTED);
    return dto;
  }

  private static FindingEntryDTO toEntry(FindingDTO f, PDFUACheckpoint cp) {
    String message = f.getMessage();
    if (message == null || message.isEmpty()) message = emptyToNull(cp.getErrorMessage());
    return new FindingEntryDTO(message, f.getSeverity(), nullIfZero(f.getPage()), f.getBBox());
  }

  private static CheckpointStatus rollup(List<CheckpointStatus> statuses) {
    boolean anyFailed = false, anyWarning = false, anyPassed = false, anyNa = false;
    for (CheckpointStatus s : statuses) {
      if (s == CheckpointStatus.FAILED) anyFailed = true;
      else if (s == CheckpointStatus.WARNING) anyWarning = true;
      else if (s == CheckpointStatus.PASSED) anyPassed = true;
      else if (s == CheckpointStatus.NOT_APPLICABLE) anyNa = true;
    }
    if (anyFailed) return CheckpointStatus.FAILED;
    if (anyWarning) return CheckpointStatus.WARNING;
    if (anyPassed) return CheckpointStatus.PASSED;
    if (anyNa) return CheckpointStatus.NOT_APPLICABLE;
    return CheckpointStatus.NOT_IMPLEMENTED;
  }

  private static void tally(SummaryDTO s, CheckpointStatus st) {
    switch (st) {
      case FAILED -> s.setFailed(s.getFailed() + 1);
      case WARNING -> s.setWarning(s.getWarning() + 1);
      case PASSED -> s.setPassed(s.getPassed() + 1);
      case NOT_APPLICABLE -> s.setNotApplicable(s.getNotApplicable() + 1);
      case NOT_IMPLEMENTED -> s.setNotImplemented(s.getNotImplemented() + 1);
    }
  }

  private static String emptyToNull(String s) {
    return (s == null || s.isEmpty()) ? null : s;
  }

  private static Integer nullIfZero(Integer p) {
    return (p == null || p == 0) ? null : p;
  }
}
