package com.netralabs.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"title", "filename", "language", "pages", "tags", "sizeBytes", "size", "compliant"})
public class DocumentInfoDTO {
  private String title;
  private String filename;
  private String language;
  private int pages;
  private int tags;
  private long sizeBytes;
  private String size;
  private Boolean compliant;
}