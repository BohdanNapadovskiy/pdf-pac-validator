package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-issue summary line attached to a failing leaf in the simple report.
 * Mirrors PAC's {@code issues[]} entries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"issueId", "severity", "severityId", "caption", "count"})
public class IssueSummaryDTO {
    private String issueId;
    private String severity;
    private int severityId;
    private String caption;
    private int count;
}
