package com.netralabs.report.quality;

import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.quality.QualityCriterion;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.CheckpointStatus;
import com.netralabs.report.CountsDTO;
import com.netralabs.report.FindingDTO;
import com.netralabs.report.FindingEntryDTO;
import com.netralabs.report.ShortSummaryEntryDTO;
import com.netralabs.vera.VeraRuleMapping;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds PAC's "Quality" report tree: a flat list of leaves, one per
 * {@link QualityCriterion}. Applies the same status policy as
 * {@code ReportBuilder.toCheckpointDto} and rolls counts / status up to a
 * single root.
 */
public final class QualityReportBuilder {

  private QualityReportBuilder() {}

  public static QualityReportDTO build(List<FindingDTO> findings) {
    Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint = new EnumMap<>(PDFUACheckpoint.class);
    for (PDFUACheckpoint cp : PDFUACheckpoint.values()) {
      byCheckpoint.put(cp, new ArrayList<>());
    }
    for (FindingDTO f : findings) {
      if (f.getCheckpoint() != null) {
        byCheckpoint.get(f.getCheckpoint()).add(f);
      }
    }

    QualityReportDTO report = new QualityReportDTO();
    report.setName("Quality");
    CountsDTO rootCounts = new CountsDTO();
    List<CheckpointStatus> leafStatuses = new ArrayList<>();

    for (QualityCriterion c : QualityCriterion.values()) {
      QualityLeafDTO leaf = evaluateLeaf(c, byCheckpoint);
      rootCounts.add(leaf.getCounts());
      leafStatuses.add(leaf.getStatus());
      report.getLeaves().add(leaf);
    }
    report.setCounts(rootCounts);
    report.setStatus(rollup(leafStatuses));
    List<ShortSummaryEntryDTO> shortSummary = new ArrayList<>();
    for (QualityLeafDTO l : report.getLeaves()) {
      shortSummary.add(new ShortSummaryEntryDTO(l.getName(), l.getStatus(), l.getCounts()));
    }
    report.setShortSummary(shortSummary);
    return report;
  }

  private static QualityLeafDTO evaluateLeaf(QualityCriterion leaf,
                                             Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint) {
    CountsDTO counts = new CountsDTO();
    List<FindingEntryDTO> entries = new ArrayList<>();
    boolean anyCovered = false;

    for (PDFUACheckpoint cp : leaf.getSources()) {
      boolean hasRunnableNative = cp.getFactory() != null && !cp.getPhases().isEmpty();
      if (hasRunnableNative || VeraRuleMapping.covers(cp)) {
        anyCovered = true;
      }
      for (FindingDTO f : byCheckpoint.getOrDefault(cp, List.of())) {
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
    }

    CheckpointStatus status;
    if (counts.getError() > 0) status = CheckpointStatus.FAILED;
    else if (counts.getWarning() > 0) status = CheckpointStatus.WARNING;
    else if (counts.getPassed() > 0) status = CheckpointStatus.PASSED;
    else if (anyCovered) status = CheckpointStatus.NOT_APPLICABLE;
    else status = CheckpointStatus.NOT_IMPLEMENTED;

    QualityLeafDTO dto = new QualityLeafDTO();
    dto.setName(leaf.getLeaf());
    dto.setCounts(counts);
    dto.setStatus(status);
    dto.setFindings(entries);
    return dto;
  }

  private static FindingEntryDTO toEntry(FindingDTO f, PDFUACheckpoint cp) {
    String message = f.getMessage();
    if (message == null || message.isEmpty()) {
      String em = cp.getErrorMessage();
      message = (em == null || em.isEmpty()) ? null : em;
    }
    Integer page = (f.getPage() == null || f.getPage() == 0) ? null : f.getPage();
    BBoxDTO bbox = f.getBBox();
    return new FindingEntryDTO(message, f.getSeverity(), page, bbox);
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
}