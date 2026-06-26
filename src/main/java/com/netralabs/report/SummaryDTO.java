package com.netralabs.report;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SummaryDTO {
  private int total;
  private int passed;
  private int warning;
  private int failed;
  private int notApplicable;
  private int notImplemented;
}
