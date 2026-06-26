import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.netralabs.Runner;
import com.netralabs.report.FindingDTO;
import com.netralabs.report.ReportBuilder;
import com.netralabs.report.ReportDTO;
import com.netralabs.report.ReportWriter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class PDFValidator {

  public static void main(String[] args) throws RuntimeException {
    if (args == null || args.length == 0) {
      log.error("No input path provided. Usage: java -jar pdf-validator.jar <path-to-pdf> [-o <output.json>]");
      System.exit(1);
      return;
    }
    String path = args[0];
    String output = null;
    for (int i = 1; i < args.length - 1; i++) {
      if ("-o".equals(args[i])) output = args[i + 1];
    }

    log.info("Starting PDF validation for: {}", path);
    try (PdfDocument pdf = new PdfDocument(new PdfReader(path))) {
      Runner runner = new Runner();
      List<FindingDTO> findings = runner.runAll(pdf, path);
      ReportDTO report = ReportBuilder.build(pdf, path, findings);
      Path written = ReportWriter.write(report, path, output);
      log.info("Report written to: {}", written);
    } catch (Exception e) {
      String errMsg = "Validation failed for " + path + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
      log.error(errMsg, e);
      System.exit(1);
    }
  }

}
