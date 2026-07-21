import com.netralabs.api.service.ValidationService;
import com.netralabs.api.service.ValidationService.DetailedResult;
import com.netralabs.api.service.ValidationService.SimpleResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Legacy CLI entry point. Kept as a thin wrapper around {@link ValidationService} for
 * backwards compatibility with the manual test workflow (see CLAUDE.md). The primary
 * runtime is now the Spring Boot REST API in {@code com.netralabs.api}.
 * <p>
 * Usage:
 * <pre>
 * PDFValidator &lt;path-to-pdf&gt; [-o &lt;output-folder&gt;] [--legacy]
 * </pre>
 * The CLI runs both stages of the API contract for convenience: it generates
 * the simple report, then immediately builds the detailed report from cached
 * findings and writes both to disk. Passing {@code --legacy} additionally
 * emits the pre-PAC combined report {@code <name>.report.json}.
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
      // Accept -o as either an existing folder or a file path. When it's a
      // folder we use it directly; otherwise fall back to its parent so a
      // path like "out/report.json" resolves to "out/".
      if (Files.isDirectory(p)) {
        outputFolder = p.toString();
      } else {
        outputFolder = p.getParent() != null ? p.getParent().toString() : ".";
      }
    }

    log.info("Starting PDF validation for: {}", path);
    try {
      ValidationService service = new ValidationService();
      SimpleResult simple = service.generateSimple(path, outputFolder, emitLegacy);
      log.info("Simple report written to:   {}", simple.simplePath());
      if (simple.legacyPath() != null) log.info("Legacy report written to:  {}", simple.legacyPath());

      Optional<DetailedResult> detailed = service.buildDetailed(simple.jobId(), /* persistToDisk= */ true);
      detailed.ifPresent(r -> log.info("Detailed report written to: {}", r.detailedPath()));
      log.info("Job id: {}", simple.jobId());
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", path, e.getMessage(), e);
      System.exit(1);
    }
  }
}
