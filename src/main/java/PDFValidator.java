import com.netralabs.api.service.ValidationService;
import com.netralabs.api.service.ValidationService.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Legacy CLI entry point. Kept as a thin wrapper around {@link ValidationService} for
 * backwards compatibility with the manual test workflow (see CLAUDE.md). The primary
 * runtime is now the Spring Boot REST API in {@code com.netralabs.api}.
 * <p>
 * Usage:
 * <pre>
 * PDFValidator &lt;path-to-pdf&gt; [-o &lt;output-folder-or-simple-json&gt;] [--legacy]
 * </pre>
 * By default writes {@code &lt;name&gt;.simple.json} and {@code &lt;name&gt;.detailed.json}.
 * Passing {@code --legacy} additionally emits the pre-PAC combined report
 * {@code &lt;name&gt;.report.json} for downstream consumers still on the old shape.
 */
@Slf4j
public class PDFValidator {

  public static void main(String[] args) {
    if (args == null || args.length == 0) {
      log.error("No input path provided. Usage: java -cp ... PDFValidator <path-to-pdf> [-o <output.json>] [--legacy]");
      System.exit(1);
      return;
    }
    String path = args[0];
    String explicitOutput = null;
    boolean emitLegacy = false;
    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      if ("--legacy".equals(arg)) {
        emitLegacy = true;
      } else if ("-o".equals(arg) && i + 1 < args.length) {
        explicitOutput = args[i + 1];
        i++;
      }
    }
    String outputFolder = null;
    if (explicitOutput != null) {
      Path p = Paths.get(explicitOutput);
      outputFolder = p.getParent() != null ? p.getParent().toString() : ".";
    }

    log.info("Starting PDF validation for: {}", path);
    try {
      ValidationResult result = new ValidationService().validatePair(path, outputFolder, emitLegacy);
      log.info("Simple report written to:   {}", result.simplePath());
      log.info("Detailed report written to: {}", result.detailedPath());
      if (result.legacyPath() != null) log.info("Legacy report written to:  {}", result.legacyPath());
      log.info("Job id: {}", result.jobId());
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", path, e.getMessage(), e);
      System.exit(1);
    }
  }
}