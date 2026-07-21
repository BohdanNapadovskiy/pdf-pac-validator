package com.netralabs.api.controller;

import com.netralabs.api.dto.ValidateRequest;
import com.netralabs.api.dto.ValidateResponse;
import com.netralabs.api.service.ValidationService;
import com.netralabs.api.service.ValidationService.CachedJob;
import com.netralabs.api.service.ValidationService.DetailedResult;
import com.netralabs.api.service.ValidationService.SimpleResult;
import com.netralabs.report.pac.DetailedReportDTO;
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
   * Run the validator against {@code request.pdfPath()}. Writes the simple
   * report to disk and returns it inline together with a {@code jobId}. The
   * detailed report is <em>not</em> written here — fetch it later with
   * {@code GET /api/report/{jobId}/detailed}, which builds it on demand from
   * cached findings.
   */
  @PostMapping("/validate")
  public ResponseEntity<ValidateResponse> validate(@RequestBody ValidateRequest request) {
    if (request == null || request.pdfPath() == null || request.pdfPath().isBlank()) {
      return ResponseEntity.badRequest().body(ValidateResponse.failed(null));
    }
    String sourceFileName = Paths.get(request.pdfPath()).getFileName().toString();
    try {
      SimpleResult result = validationService.generateSimple(request.pdfPath(), request.outputFolder(), false);
      log.info("Job {}: simple={}", result.jobId(), result.simplePath());
      return ResponseEntity.ok(ValidateResponse.success(
              result.jobId(),
              sourceFileName,
              result.simplePath().toString(),
              result.simpleReport()));
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", request.pdfPath(), e.getMessage(), e);
      return ResponseEntity.status(500).body(ValidateResponse.failed(sourceFileName));
    }
  }

  /**
   * Build and return the detailed report for a previously generated job.
   * The report is built from cached findings — no PDF re-open, no pipeline
   * re-run. Also persists the report to disk as {@code <name>.detailed.json}
   * in the original output folder so subsequent calls are cheap.
   */
  @GetMapping("/report/{jobId}/detailed")
  public ResponseEntity<DetailedReportDTO> getDetailed(@PathVariable("jobId") String jobId) {
    try {
      Optional<DetailedResult> result = validationService.buildDetailed(jobId, /* persistToDisk= */ true);
      if (result.isEmpty()) return ResponseEntity.notFound().build();
      DetailedResult r = result.get();
      if (r.detailedPath() != null) log.info("Job {}: detailed written to {}", jobId, r.detailedPath());
      return ResponseEntity.ok(r.detailedReport());
    } catch (Exception e) {
      log.error("Failed to build detailed report for {}: {}", jobId, e.getMessage(), e);
      return ResponseEntity.status(500).build();
    }
  }

  /**
   * Stream the simple report file for a previously generated job id. Present
   * mostly for symmetry — the POST already returns the simple report inline.
   */
  @GetMapping("/report/{jobId}/simple")
  public ResponseEntity<Resource> getSimple(@PathVariable("jobId") String jobId) {
    Optional<CachedJob> maybe = validationService.findJob(jobId);
    if (maybe.isEmpty()) return ResponseEntity.notFound().build();
    Path path = maybe.get().simplePath();
    if (path == null || !Files.exists(path)) return ResponseEntity.notFound().build();
    Resource body = new FileSystemResource(path);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + path.getFileName().toString() + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
  }
}
