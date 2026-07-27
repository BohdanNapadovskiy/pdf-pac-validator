package com.netralabs.api.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/validate response — carries the {@code jobId} that identifies the
 * run and the S3 URIs where the simple and detailed reports were uploaded.
 * Clients read the reports directly from S3.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidateResponse(
        String jobId,
        String sourceFileName,
        String simpleReportS3Uri,
        String detailedReportS3Uri,
        String status,
        Optional<String> message
    ) {

    public static ValidateResponse success(String jobId,
                                           String sourceFileName,
                                           String simpleReportS3Uri,
                                           String detailedReportS3Uri) {
        return new ValidateResponse(jobId, sourceFileName, simpleReportS3Uri, detailedReportS3Uri,
                "success", null);
    }

    public static ValidateResponse failed(String sourceFileName, String message) {
        return new ValidateResponse(null, sourceFileName, null, null, "failed", Optional.ofNullable(message));
    }
}