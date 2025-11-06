import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.netralabs.Runner;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;

import java.util.List;

@Slf4j
public class PDFValidator {

  static {
    VeraGreenfieldFoundryProvider.initialise();
  }

  public static void main(String[] args) throws RuntimeException {
    if (args == null || args.length == 0) {
      String msg = "No input path provided. Usage: java -jar pdf-validator.jar <path-to-pdf> [debug-options]";
      log.error(msg);
      System.exit(1);
      return;
    }
    String path = args[0];
    log.info("Starting PDF validation for: {}", path);
    try (PdfDocument pdf = new PdfDocument(new PdfReader(path))) {
      Runner runner = new Runner();
      List<FindingDTO> findings  = runner.runAll(pdf);
      int a=1;
    } catch (Exception e) {
      String errMsg = "Validation failed for " + path + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
      log.error(errMsg, e);
      System.exit(1);
    }
  }

}
