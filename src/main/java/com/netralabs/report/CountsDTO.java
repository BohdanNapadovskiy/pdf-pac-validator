package com.netralabs.report;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CountsDTO {
  private int passed;
  private int warning;
  private int error;

  public void add(CountsDTO other) {
    this.passed += other.passed;
    this.warning += other.warning;
    this.error += other.error;
  }
}
