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
@JsonPropertyOrder({"name", "status", "counts", "subCategories"})
public class CategoryDTO {
  private String name;
  private CheckpointStatus status;
  private CountsDTO counts;
  private List<SubCategoryDTO> subCategories = new ArrayList<>();
}
