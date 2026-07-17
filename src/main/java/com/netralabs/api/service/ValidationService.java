package com.netralabs.api.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.netralabs.Runner;
import com.netralabs.report.FindingDTO;
import com.netralabs.report.ReportBuilder;
import com.netralabs.report.ReportDTO;
import com.netralabs.report.ReportWriter;
import com.netralabs.report.pac.DetailedBodyDTO;
import com.netralabs.report.pac.DetailedReportBuilder;
import com.netralabs.report.pac.DetailedReportDTO;
import com.netralabs.report.pac.FindingBboxEnricher;
import com.netralabs.report.pac.PacDocumentInfoBuilder;
import com.netralabs.report.pac.PacReportWriter;
import com.netralabs.report.pac.ReportSectionDTO;
import com.netralabs.report.pac.SimpleBodyDTO;
import com.netralabs.report.pac.SimpleReportBuilder;
import com.netralabs.report.pac.SimpleReportDTO;
import com.netralabs.report.pac.VersionDTO;
import com.netralabs.report.quality.QualityReportBuilder;
import com.netralabs.report.wcag.WCAGReportBuilder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ValidationService {

  /**
   * In-memory registry mapping a job id to the pair of report files written for
   * that job. Populated on every successful {@link #validatePair} run and read by
   * the controller's GET-by-jobId endpoints. This is process-local — restarting
   * the JVM clears the registry, so callers that need durability should persist
   * paths themselves.
   */
  private final ConcurrentMap<String, ValidationResult> jobs = new ConcurrentHashMap<>();

  public Path validate(String sourcePdfPath, String outputFolder) throws Exception {
    ValidationResult result = validatePair(sourcePdfPath, outputFolder, false);
    return result.simplePath();
  }

  /** Look up a previously generated job by its id. */
  public Optional<ValidationResult> findJob(String jobId) {
    if (jobId == null || jobId.isBlank()) return Optional.empty();
    return Optional.ofNullable(jobs.get(jobId));
  }

  /**
   * Runs the full validation pipeline once, emitting the two PAC-shaped reports
   * (simple + detailed) side by side. Optionally also writes the legacy combined
   * report ({@code <name>.report.json}) when {@code emitLegacy} is true.
   *
   * @param sourcePdfPath source PDF (Windows path; MSYS-style paths accepted)
   * @param outputFolder  destination folder for output files; null = alongside source
   * @param emitLegacy    when true, additionally writes the pre-PAC combined report
   */
  public ValidationResult validatePair(String sourcePdfPath, String outputFolder, boolean emitLegacy) throws Exception {
    String path = normalizePath(sourcePdfPath);
    String folder = normalizePath(outputFolder);
    String jobId = UUID.randomUUID().toString();
    String creationDate = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    String name = displayName(path);

    try (PdfDocument pdf = new PdfDocument(new PdfReader(new File(path)))) {
      Runner runner = new Runner();
      List<FindingDTO> findings = runner.runAll(pdf, path);
      // Enrich findings whose emitting rule couldn't attach page/bbox — used
      // for veraPDF-driven annotation-nesting errors so the detailed report
      // can highlight them.
      FindingBboxEnricher.enrich(pdf, findings);

      SimpleReportDTO simple = buildSimple(pdf, path, findings, jobId, name, creationDate);
      DetailedReportDTO detailed = buildDetailed(pdf, findings, jobId, name, creationDate);
      Path simplePath = PacReportWriter.writeSimple(simple, path, folder);
      Path detailedPath = PacReportWriter.writeDetailed(detailed, path, folder);

      Path legacyPath = null;
      if (emitLegacy) {
        ReportDTO legacy = ReportBuilder.build(pdf, path, findings);
        legacy.getReports().setWcag(WCAGReportBuilder.build(findings));
        legacy.getReports().setQuality(QualityReportBuilder.build(findings));
        legacyPath = ReportWriter.write(legacy, path, resolveLegacyOutput(path, folder));
      }
      ValidationResult result = new ValidationResult(jobId, simple, simplePath, detailedPath, legacyPath);
      jobs.put(jobId, result);
      return result;
    }
  }

  private static SimpleReportDTO buildSimple(PdfDocument pdf, String path, List<FindingDTO> findings,
                                             String jobId, String name, String creationDate) {
    SimpleBodyDTO body = new SimpleBodyDTO();
    body.setJobId(jobId);
    body.setName(name);
    body.setDocumentInformation(PacDocumentInfoBuilder.build(pdf, path));
    List<ReportSectionDTO> sections = SimpleReportBuilder.build(findings);
    body.setReports(sections);
    body.setCreationDate(creationDate);
    return new SimpleReportDTO(body, VersionDTO.current());
  }

  private static DetailedReportDTO buildDetailed(PdfDocument pdf, List<FindingDTO> findings,
                                                 String jobId, String name, String creationDate) {
    DetailedBodyDTO body = DetailedReportBuilder.build(pdf, findings);
    body.setJobId(jobId);
    body.setName(name);
    body.setCreationDate(creationDate);
    return new DetailedReportDTO(body, VersionDTO.current());
  }

  private static String displayName(String sourcePath) {
    String fileName = Paths.get(sourcePath).getFileName().toString();
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  private static String resolveLegacyOutput(String sourcePath, String outputFolder) {
    if (outputFolder == null || outputFolder.isBlank()) return null;
    Path in = Paths.get(sourcePath).toAbsolutePath();
    String fileName = in.getFileName().toString();
    int dot = fileName.lastIndexOf('.');
    String base = dot > 0 ? fileName.substring(0, dot) : fileName;
    return Paths.get(outputFolder, base + ".report.json").toString();
  }

  /**
   * Result of one validation run — carries the shared {@code jobId}, the parsed
   * simple report (returned inline on POST), and the file paths for downstream
   * lookup via GET /api/report/{jobId}/...
   */
  public record ValidationResult(String jobId,
                                 SimpleReportDTO simpleReport,
                                 Path simplePath,
                                 Path detailedPath,
                                 Path legacyPath) {}

  /**
   * Convert MSYS / Git Bash style mount paths like "/c/foo/bar" to Windows "C:/foo/bar".
   */
  private static String normalizePath(String p) {
    if (p != null && p.length() >= 3
        && p.charAt(0) == '/'
        && Character.isLetter(p.charAt(1))
        && p.charAt(2) == '/') {
      return Character.toUpperCase(p.charAt(1)) + ":" + p.substring(2);
    }
    return p;
  }
}
