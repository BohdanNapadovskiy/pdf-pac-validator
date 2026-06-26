package com.netralabs.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
@JsonPropertyOrder({"element", "status", "counts", "errorMessage", "findings"})
public class CheckpointReportDTO {
  private String element;
  private CheckpointStatus status;
  private CountsDTO counts;
  private String errorMessage;
  private List<FindingEntryDTO> findings = new ArrayList<>();
}
