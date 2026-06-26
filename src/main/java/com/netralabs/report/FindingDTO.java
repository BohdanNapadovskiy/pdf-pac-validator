package com.netralabs.report;

import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FindingDTO {

  public Severity severity;
  public PDFUACheckpoint checkpoint;
  public Integer page;
  public BBoxDTO bBox;
  public String message;

  public FindingDTO(Severity severity, PDFUACheckpoint checkpoint, Integer page, BBoxDTO bBox) {
    this(severity, checkpoint, page, bBox, null);
  }
}
