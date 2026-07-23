package com.netralabs.api.controller;

import com.netralabs.api.dto.ValidateRequest;
import com.netralabs.api.dto.ValidateResponse;
import com.netralabs.api.service.ValidationService;
import com.netralabs.api.service.ValidationService.S3Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ValidateController {

  private final ValidationService validationService;

  /**
   * Run the validator against the PDF at {@code s3://{bucketName}/{pdfPath}}.
   * Uploads both PAC reports to
   * {@code s3://{bucketName}/{outputFolderPath}/<name>.{simple,detailed}.json}
   * and returns their S3 URIs. The report bodies are not inlined — clients
   * fetch them directly from S3.
   */
  @PostMapping("/validate")
  public ResponseEntity<ValidateResponse> validate(@RequestBody ValidateRequest request) {
    if (request == null
            || request.bucketName() == null || request.bucketName().isBlank()
            || request.pdfPath() == null || request.pdfPath().isBlank()) {
      return ResponseEntity.badRequest().body(ValidateResponse.failed(null, "bucketName and pdfPath are required"));
    }
    String sourceFileName = Paths.get(request.pdfPath()).getFileName().toString();
    try {
      S3Result result = validationService.validate(
              request.bucketName(), request.pdfPath(), request.outputFolderPath());
      log.info("Job {}: simple={}, detailed={}",
              result.jobId(), result.simpleReportS3Uri(), result.detailedReportS3Uri());
      return ResponseEntity.ok(ValidateResponse.success(
              result.jobId(),
              sourceFileName,
              result.simpleReportS3Uri(),
              result.detailedReportS3Uri()));
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", request.pdfPath(), e.getMessage(), e);
      return ResponseEntity.status(500).body(ValidateResponse.failed(sourceFileName, e.getMessage()));
    }
  }
}