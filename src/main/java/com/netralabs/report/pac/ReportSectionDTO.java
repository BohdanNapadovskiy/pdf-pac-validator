package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry in {@code body.reports[]} of the simple report — carries the report
 * type ("PDF/UA" or "WCAG"), the compliance index, and the fully-nested report
 * tree.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"type", "uaIndex", "report"})
public class ReportSectionDTO {
    private String type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double uaIndex;

    private NodeDTO report;
}
