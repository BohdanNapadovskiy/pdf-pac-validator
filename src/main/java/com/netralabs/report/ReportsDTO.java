package com.netralabs.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.netralabs.report.quality.QualityReportDTO;
import com.netralabs.report.wcag.WCAGReportDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Groups all per-standard report sections under one container.
 * Keys "PDF/UA", "WCAG", and "Quality" are emitted verbatim — they identify
 * the report taxonomy.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"document", "info", "PDF/UA", "WCAG", "Quality"})
public class ReportsDTO {
  private String document;
  private DocumentInfoDTO info;

  @JsonProperty("PDF/UA")
  private PdfUaSectionDTO pdfUa;

  @JsonProperty("WCAG")
  private WCAGReportDTO wcag;

  @JsonProperty("Quality")
  private QualityReportDTO quality;
}