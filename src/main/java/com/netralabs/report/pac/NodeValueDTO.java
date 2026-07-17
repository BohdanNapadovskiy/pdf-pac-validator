package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code value} block of a PAC report node — carries the node's identity
 * (checkId, caption) and rolled-up counts / severity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
        "checkId", "caption",
        "terminationCount", "errorCount", "warningCount", "passedCount",
        "severity", "severityId"
})
public class NodeValueDTO {
    private String checkId;
    private String caption;
    private int terminationCount;
    private int errorCount;
    private int warningCount;
    private int passedCount;
    private String severity;
    private int severityId;
}
