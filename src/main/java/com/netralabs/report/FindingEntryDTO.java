package com.netralabs.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.netralabs.domain.Severity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"message", "severity", "page", "bBox"})
public class FindingEntryDTO {
  private String message;
  private Severity severity;
  private Integer page;
  // Without @JsonProperty Jackson lowercases getBBox() to "bbox", which contradicts
  // @JsonPropertyOrder and the documented schema. Pin the wire name explicitly.
  @JsonProperty("bBox")
  private BBoxDTO bBox;
}
