package com.netralabs.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netralabs.report.pac.SimpleReportDTO;

/**
 * POST /api/validate response — carries the {@code jobId} that identifies the
 * run, the file paths of the emitted reports on disk, the inlined simple
 * report body for convenience, and a summary {@code status} for failure
 * detection without inspecting the body.
 * <p>
 * The detailed report is not inlined here; callers fetch it via
 * {@code GET /api/report/{jobId}/detailed}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidateResponse(
        String jobId,
        String sourceFileName,
        String simpleReportPath,
        String detailedReportPath,
        String status,
        SimpleReportDTO simpleReport) {

    public static ValidateResponse success(String jobId,
                                           String sourceFileName,
                                           String simpleReportPath,
                                           String detailedReportPath,
                                           SimpleReportDTO simpleReport) {
        return new ValidateResponse(jobId, sourceFileName, simpleReportPath, detailedReportPath,
                "success", simpleReport);
    }

    public static ValidateResponse failed(String sourceFileName) {
        return new ValidateResponse(null, sourceFileName, null, null, "failed", null);
    }
}
