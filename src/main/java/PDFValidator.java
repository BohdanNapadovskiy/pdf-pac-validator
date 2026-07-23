import com.netralabs.api.service.ValidationService;
import com.netralabs.api.service.ValidationService.S3Result;
import lombok.extern.slf4j.Slf4j;

/**
 * Legacy CLI entry point. Kept as a thin wrapper around {@link ValidationService}
 * for backwards compatibility. The primary runtime is the Spring Boot REST API
 * in {@code com.netralabs.api}.
 * <p>
 * Usage:
 * <pre>
 * PDFValidator &lt;s3-pdf-key&gt; [-o &lt;s3-output-folder&gt;] [-b &lt;bucket&gt;]
 * </pre>
 * Both the source PDF and the generated reports live in the same S3 bucket.
 */
@Slf4j
public class PDFValidator {

  private static final String DEFAULT_BUCKET = "aod-main-v1-staging";

  public static void main(String[] args) {
    if (args == null || args.length == 0) {
      log.error("No input path provided. Usage: java -cp ... PDFValidator <s3-pdf-key> [-o <s3-output-folder>] [-b <bucket>]");
      System.exit(1);
      return;
    }
    String bucketName = DEFAULT_BUCKET;
    String pdfPath = args[0];
    String outputFolderPath = null;
    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      if ("-o".equals(arg) && i + 1 < args.length) {
        outputFolderPath = args[i + 1];
        i++;
      } else if ("-b".equals(arg) && i + 1 < args.length) {
        bucketName = args[i + 1];
        i++;
      }
    }

    log.info("Starting PDF validation for: s3://{}/{}", bucketName, pdfPath);
    try {
      ValidationService service = new ValidationService();
      S3Result result = service.validate(bucketName, pdfPath, outputFolderPath);
      log.info("Simple report:   {}", result.simpleReportS3Uri());
      log.info("Detailed report: {}", result.detailedReportS3Uri());
      log.info("Job id: {}", result.jobId());
    } catch (Exception e) {
      log.error("Validation failed for {}: {}", pdfPath, e.getMessage(), e);
      System.exit(1);
    }
  }
}