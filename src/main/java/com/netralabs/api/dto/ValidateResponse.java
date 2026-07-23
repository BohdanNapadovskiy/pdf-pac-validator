package com.netralabs.api.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netralabs.report.pac.SimpleReportDTO;

/**
 * POST /api/validate response — carries the {@code jobId} that identifies the
 * run, the file path of the simple report on disk, the inlined simple report
 * body for convenience, and a summary {@code status}.
 * <p>
 * The detailed report is <em>not</em> generated during POST. Fetch it later
 * via {@code GET /api/report/{jobId}/detailed}, which builds it on demand
 * from the cached findings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidateResponse(
        String jobId,
        String sourceFileName,
        String simpleReportPath,
        String status,
        SimpleReportDTO simpleReport,
        Optional<String> message
    ) {

    public static ValidateResponse success(String jobId,
                                           String sourceFileName,
                                           String simpleReportPath,
                                           SimpleReportDTO simpleReport) {
        return new ValidateResponse(jobId, sourceFileName, simpleReportPath,
                "success", simpleReport, null);
    }

    public static ValidateResponse failed(String sourceFileName, String message) {
        return new ValidateResponse(null, sourceFileName, null, "failed", null, Optional.ofNullable(message));
    }
}
