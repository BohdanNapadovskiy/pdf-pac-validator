package com.netralabs.report.wcag;

import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.CheckpointStatus;
import com.netralabs.report.CountsDTO;
import com.netralabs.report.FindingDTO;
import com.netralabs.report.FindingEntryDTO;
import com.netralabs.vera.VeraRuleMapping;
import com.netralabs.wcag.WCAGCriterion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remaps existing {@link PDFUACheckpoint} findings onto PAC's WCAG 2.2 tree.
 * <p>
 * Each {@link WCAGCriterion} declares its source checkpoints — this builder
 * fans findings out to leaves, applies the same status policy as
 * {@code ReportBuilder.toCheckpointDto}, then rolls counts/status up
 * Leaf → Criterion → Guideline → Principle → root.
 */
public final class WCAGReportBuilder {

  private WCAGReportBuilder() {}

  public static WCAGReportDTO build(List<FindingDTO> findings) {
    Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint = new EnumMap<>(PDFUACheckpoint.class);
    for (PDFUACheckpoint cp : PDFUACheckpoint.values()) {
      byCheckpoint.put(cp, new ArrayList<>());
    }
    for (FindingDTO f : findings) {
      if (f.getCheckpoint() != null) {
        byCheckpoint.get(f.getCheckpoint()).add(f);
      }
    }

    // Principle → Guideline → Criterion → entries (each entry has leafName + status + counts + entries)
    Map<String, Map<String, Map<String, List<WCAGCriterion>>>> tree = new LinkedHashMap<>();
    for (WCAGCriterion c : WCAGCriterion.values()) {
      tree.computeIfAbsent(c.getPrinciple(), k -> new LinkedHashMap<>())
          .computeIfAbsent(c.getGuideline(), k -> new LinkedHashMap<>())
          .computeIfAbsent(c.getCriterion(), k -> new ArrayList<>())
          .add(c);
    }

    WCAGReportDTO report = new WCAGReportDTO();
    report.setName("WCAG 2.2");
    CountsDTO rootCounts = new CountsDTO();
    List<CheckpointStatus> principleStatuses = new ArrayList<>();

    for (Map.Entry<String, Map<String, Map<String, List<WCAGCriterion>>>> pEntry : tree.entrySet()) {
      PrincipleDTO principle = new PrincipleDTO();
      principle.setName(pEntry.getKey());
      CountsDTO principleCounts = new CountsDTO();
      List<CheckpointStatus> guidelineStatuses = new ArrayList<>();

      for (Map.Entry<String, Map<String, List<WCAGCriterion>>> gEntry : pEntry.getValue().entrySet()) {
        GuidelineDTO guideline = new GuidelineDTO();
        guideline.setName(gEntry.getKey());
        CountsDTO guidelineCounts = new CountsDTO();
        List<CheckpointStatus> criterionStatuses = new ArrayList<>();

        for (Map.Entry<String, List<WCAGCriterion>> cEntry : gEntry.getValue().entrySet()) {
          CriterionDTO criterion = buildCriterion(cEntry.getKey(), cEntry.getValue(), byCheckpoint);
          guidelineCounts.add(criterion.getCounts());
          criterionStatuses.add(criterion.getStatus());
          guideline.getCriteria().add(criterion);
        }
        guideline.setCounts(guidelineCounts);
        guideline.setStatus(rollup(criterionStatuses));
        principleCounts.add(guidelineCounts);
        guidelineStatuses.add(guideline.getStatus());
        principle.getGuidelines().add(guideline);
      }
      principle.setCounts(principleCounts);
      principle.setStatus(rollup(guidelineStatuses));
      rootCounts.add(principleCounts);
      principleStatuses.add(principle.getStatus());
      report.getPrinciples().add(principle);
    }
    report.setCounts(rootCounts);
    report.setStatus(rollup(principleStatuses));
    return report;
  }

  private static CriterionDTO buildCriterion(String criterionName,
                                             List<WCAGCriterion> entries,
                                             Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint) {
    CriterionDTO dto = new CriterionDTO();
    dto.setName(criterionName);

    boolean criterionAsLeaf = entries.size() == 1 && entries.get(0).getLeaf() == null;

    if (criterionAsLeaf) {
      WCAGCriterion only = entries.get(0);
      LeafResult leaf = evaluateLeaf(only, byCheckpoint);
      dto.setCounts(leaf.counts);
      dto.setStatus(leaf.status);
      dto.setFindings(leaf.entries);
      return dto;
    }

    CountsDTO counts = new CountsDTO();
    List<CheckpointStatus> leafStatuses = new ArrayList<>();
    for (WCAGCriterion entry : entries) {
      LeafResult leaf = evaluateLeaf(entry, byCheckpoint);
      WCAGLeafDTO leafDto = new WCAGLeafDTO();
      leafDto.setName(entry.getLeaf());
      leafDto.setCounts(leaf.counts);
      leafDto.setStatus(leaf.status);
      leafDto.setFindings(leaf.entries);
      counts.add(leaf.counts);
      leafStatuses.add(leaf.status);
      dto.getLeaves().add(leafDto);
    }
    dto.setCounts(counts);
    dto.setStatus(rollup(leafStatuses));
    return dto;
  }

  private static LeafResult evaluateLeaf(WCAGCriterion leaf,
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
        } else if (sev == Severity.PASSED && !leaf.isErrorsOnly()) {
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
    return new LeafResult(counts, entries, status);
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

  private record LeafResult(CountsDTO counts, List<FindingEntryDTO> entries, CheckpointStatus status) {}
}