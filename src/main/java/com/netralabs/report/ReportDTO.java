package com.netralabs.report;

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
@JsonPropertyOrder({"report", "document", "info", "counts", "summary", "shortSummary", "categories"})
public class ReportDTO {
  private String report;
  private String document;
  private DocumentInfoDTO info;
  private CountsDTO counts;
  private SummaryDTO summary;
  private List<ShortSummaryEntryDTO> shortSummary = new ArrayList<>();
  private List<CategoryDTO> categories = new ArrayList<>();
}
