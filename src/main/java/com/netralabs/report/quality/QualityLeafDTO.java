package com.netralabs.report.quality;

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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"name", "status", "counts", "findings"})
public class QualityLeafDTO {
  private String name;
  private CheckpointStatus status;
  private CountsDTO counts;
  private List<FindingEntryDTO> findings = new ArrayList<>();
}