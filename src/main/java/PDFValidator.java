import com.netralabs.api.service.ValidationService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Legacy CLI entry point. Kept as a thin wrapper around {@link ValidationService} for
 * backwards compatibility with the manual test workflow (see CLAUDE.md). The primary
 * runtime is now the Spring Boot REST API in {@code com.netralabs.api}.
 */
@Slf4j
public class PDFValidator {

  public static void main(String[] args) {
    if (args == null || args.length == 0) {
      log.error("No input path provided. Usage: java -cp ... PDFValidator <path-to-pdf> [-o <output.json>]");
      System.exit(1);
      return;
    }
    String path = args[0];
    String explicitOutput = null;
    String outputFolder = null;
    for (int i = 1; i < args.length - 1; i++) {
      if ("-o".equals(args[i])) explicitOutput = args[i + 1];
    }
    if (explicitOutput != null) {
      Path p = Paths.get(explicitOutput);
      outputFolder = p.getParent() != null ? p.getParent().toString() : ".";
    }

    log.info("Starting PDF validation for: {}", path);
    try {
      Path written = new ValidationService().validate(path, outputFolder);
      log.info("Report written to: {}", written);
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", path, e.getMessage(), e);
      System.exit(1);
    }
  }
}