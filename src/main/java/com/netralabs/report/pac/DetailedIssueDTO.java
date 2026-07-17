package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One failing issue as it appears in the detailed report — the aggregate
 * caption / count plus a per-instance {@code details} array.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"issueId", "severity", "severityId", "checkId", "caption", "count", "details"})
public class DetailedIssueDTO {
    private String issueId;
    private String severity;
    private int severityId;
    private String checkId;
    private String caption;
    private int count;
    private List<IssueDetailDTO> details = new ArrayList<>();
}
