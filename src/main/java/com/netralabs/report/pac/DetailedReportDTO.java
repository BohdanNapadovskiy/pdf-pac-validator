package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level envelope of PAC's detailed-report JSON: {@code { body, version }}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"body", "version"})
public class DetailedReportDTO {
    private DetailedBodyDTO body;
    private VersionDTO version;
}
