package com.netralabs.vera;

import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.report.FindingDTO;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VeraValidationResults {

  private static final VeraValidationResults EMPTY = new VeraValidationResults(Collections.emptyMap());

  private final Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint;

  public VeraValidationResults(Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint) {
    this.byCheckpoint = byCheckpoint;
  }

  public List<FindingDTO> findingsFor(PDFUACheckpoint cp) {
    return byCheckpoint.getOrDefault(cp, Collections.emptyList());
  }

  public boolean covers(PDFUACheckpoint cp) {
    return byCheckpoint.containsKey(cp);
  }

  public static VeraValidationResults empty() {
    return EMPTY;
  }
}
