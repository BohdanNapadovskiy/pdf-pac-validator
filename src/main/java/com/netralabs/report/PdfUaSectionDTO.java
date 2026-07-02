package com.netralabs.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The PDF/UA branch of the report — short PAC-style summary plus the category tree.
 * Lives under {@code reports."PDF/UA"} in the output JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"shortSummary", "categories"})
public class PdfUaSectionDTO {
  private List<ShortSummaryEntryDTO> shortSummary = new ArrayList<>();
  private List<CategoryDTO> categories = new ArrayList<>();
}