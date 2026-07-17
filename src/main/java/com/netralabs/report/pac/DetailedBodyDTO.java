package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code body} of the detailed report — job identity, per-type failing
 * issues, page-cropbox ranges, and generation timestamp.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"jobId", "name", "issues", "cropBoxRanges", "creationDate"})
public class DetailedBodyDTO {
    private String jobId;
    private String name;
    private List<DetailedTypeDTO> issues = new ArrayList<>();
    private List<CropBoxRangeDTO> cropBoxRanges = new ArrayList<>();
    private String creationDate;
}
