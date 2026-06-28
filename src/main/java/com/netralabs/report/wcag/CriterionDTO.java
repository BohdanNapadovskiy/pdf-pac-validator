package com.netralabs.report.wcag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.netralabs.report.CheckpointStatus;
import com.netralabs.report.CountsDTO;
import com.netralabs.report.FindingEntryDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One WCAG 2.2 Success Criterion node.
 * <p>
 * If the criterion has named sub-leaves (e.g. 1.1.1, 1.3.1, 2.4.2, 3.1.2, 4.1.1)
 * they are listed in {@code leaves} and counts/status roll up from them.
 * Otherwise {@code leaves} is empty and the criterion's own findings/status apply.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"name", "status", "counts", "findings", "leaves"})
public class CriterionDTO {
  private String name;
  private CheckpointStatus status;
  private CountsDTO counts;
  private List<FindingEntryDTO> findings = new ArrayList<>();
  private List<WCAGLeafDTO> leaves = new ArrayList<>();
}