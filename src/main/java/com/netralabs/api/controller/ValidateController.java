package com.netralabs.api.controller;

import com.netralabs.api.dto.ValidateRequest;
import com.netralabs.api.dto.ValidateResponse;
import com.netralabs.api.service.ValidationService;
import com.netralabs.api.service.ValidationService.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ValidateController {

  private final ValidationService validationService;

  /**
   * Run the validator against {@code request.pdfPath()} and return the simple
   * report inline together with a {@code jobId}. The detailed report is written
   * to disk during the same run and can be fetched with
   * {@code GET /api/report/{jobId}/detailed}.
   */
  @PostMapping("/validate")
  public ResponseEntity<ValidateResponse> validate(@RequestBody ValidateRequest request) {
    if (request == null || request.pdfPath() == null || request.pdfPath().isBlank()) {
      return ResponseEntity.badRequest().body(ValidateResponse.failed(null));
    }
    String sourceFileName = Paths.get(request.pdfPath()).getFileName().toString();
    try {
      ValidationResult result = validationService.validatePair(request.pdfPath(), request.outputFolder(), false);
      log.info("Job {}: simple={}, detailed={}", result.jobId(), result.simplePath(), result.detailedPath());
      return ResponseEntity.ok(ValidateResponse.success(
              result.jobId(),
              sourceFileName,
              result.simplePath().toString(),
              result.detailedPath().toString(),
              result.simpleReport()));
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", request.pdfPath(), e.getMessage(), e);
      return ResponseEntity.status(500).body(ValidateResponse.failed(sourceFileName));
    }
  }

  /** Stream the detailed report file for a previously generated job id. */
  @GetMapping("/report/{jobId}/detailed")
  public ResponseEntity<Resource> getDetailed(@PathVariable String jobId) {
    return jobFile(jobId, ValidationResult::detailedPath);
  }

  /**
   * Stream the simple report file for a previously generated job id. Present
   * mostly for symmetry — the POST already returns the simple report inline.
   */
  @GetMapping("/report/{jobId}/simple")
  public ResponseEntity<Resource> getSimple(@PathVariable String jobId) {
    return jobFile(jobId, ValidationResult::simplePath);
  }

  private ResponseEntity<Resource> jobFile(String jobId, java.util.function.Function<ValidationResult, Path> pathPicker) {
    Optional<ValidationResult> maybe = validationService.findJob(jobId);
    if (maybe.isEmpty()) return ResponseEntity.notFound().build();
    Path path = pathPicker.apply(maybe.get());
    if (path == null || !Files.exists(path)) return ResponseEntity.notFound().build();
    Resource body = new FileSystemResource(path);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + path.getFileName().toString() + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
  }
}
