package com.netralabs.report;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"name", "status", "counts"})
public class ShortSummaryEntryDTO {
  private String name;
  private CheckpointStatus status;
  private CountsDTO counts;
}
