package com.netralabs.api.controller;

import com.netralabs.api.dto.ValidateRequest;
import com.netralabs.api.dto.ValidateResponse;
import com.netralabs.api.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ValidateController {

  private final ValidationService validationService;

  @PostMapping("/validate")
  public ResponseEntity<ValidateResponse> validate(@RequestBody ValidateRequest request) {
    if (request == null || request.pdfPath() == null || request.pdfPath().isBlank()) {
      return ResponseEntity.badRequest()
          .body(new ValidateResponse(null, null, "failed"));
    }
    String sourceFileName = Paths.get(request.pdfPath()).getFileName().toString();
    try {
      Path written = validationService.validate(request.pdfPath(), request.outputFolder());
      log.info("Report written to: {}", written);
      return ResponseEntity.ok(ValidateResponse.success(sourceFileName, written.toString()));
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", request.pdfPath(), e.getMessage(), e);
      return ResponseEntity.status(500)
          .body(ValidateResponse.failed(sourceFileName, null));
    }
  }
}