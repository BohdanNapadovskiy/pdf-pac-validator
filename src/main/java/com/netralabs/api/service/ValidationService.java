package com.netralabs.api.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.netralabs.Runner;
import com.netralabs.report.FindingDTO;
import com.netralabs.report.ReportBuilder;
import com.netralabs.report.ReportDTO;
import com.netralabs.report.ReportWriter;
import com.netralabs.report.pac.CropBoxRangeDTO;
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

/**
 * Orchestrates a PDF validation run.
 *
 * <p><strong>Two-step lifecycle</strong> matching the customer's API contract:
 * <ol>
 *   <li>{@link #generateSimple} runs the full pipeline once, writes the
 *       PAC-shaped simple report to disk, and caches the findings + page
 *       CropBox ranges keyed by {@code jobId}. The PDF is opened and closed
 *       during this call.</li>
 *   <li>{@link #buildDetailed} looks up the cached state for a given
 *       {@code jobId} and produces the detailed report on demand — no PDF
 *       re-open, no pipeline re-run.</li>
 * </ol>
 *
 * The registry is a process-local {@link ConcurrentHashMap}; a restart clears
 * it, so callers that need durability should persist the simple report and
 * regenerate.
 */
@Service
public class ValidationService {

  private final ConcurrentMap<String, CachedJob> jobs = new ConcurrentHashMap<>();

  /**
   * Runs the full validation pipeline, writes {@code <name>.simple.json},
   * and caches the state needed to build the detailed report later. When
   * {@code emitLegacy} is true, additionally writes the pre-PAC combined
   * report {@code <name>.report.json}.
   *
   * @param sourcePdfPath source PDF (Windows path; MSYS-style paths accepted)
   * @param outputFolder  destination folder for output files; null = alongside source
   * @param emitLegacy    when true, additionally writes the pre-PAC combined report
   */
  public SimpleResult generateSimple(String sourcePdfPath, String outputFolder, boolean emitLegacy) throws Exception {
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

      SimpleReportDTO simple = buildSimpleReport(pdf, path, findings, jobId, name, creationDate);
      Path simplePath = PacReportWriter.writeSimple(simple, path, folder);

      Path legacyPath = null;
      if (emitLegacy) {
        ReportDTO legacy = ReportBuilder.build(pdf, path, findings);
        legacy.getReports().setWcag(WCAGReportBuilder.build(findings));
        legacy.getReports().setQuality(QualityReportBuilder.build(findings));
        legacyPath = ReportWriter.write(legacy, path, resolveLegacyOutput(path, folder));
      }

      // Cache everything the detailed report needs so we don't have to
      // re-open the PDF when the client fetches it.
      List<CropBoxRangeDTO> cropBoxRanges = DetailedReportBuilder.buildCropBoxRanges(pdf);
      jobs.put(jobId, new CachedJob(jobId, name, path, folder, creationDate,
              findings, cropBoxRanges, simplePath));

      return new SimpleResult(jobId, simple, simplePath, legacyPath);
    }
  }

  /**
   * Build the detailed report body from cached state. Optionally also
   * persists it to disk as {@code <name>.detailed.json} in the same
   * output folder used at {@link #generateSimple} time.
   *
   * @return {@code Optional.empty()} when the {@code jobId} is unknown
   *         (never generated, or evicted by a restart).
   */
  public Optional<DetailedResult> buildDetailed(String jobId, boolean persistToDisk) throws Exception {
    if (jobId == null || jobId.isBlank()) return Optional.empty();
    CachedJob cached = jobs.get(jobId);
    if (cached == null) return Optional.empty();

    DetailedBodyDTO body = DetailedReportBuilder.build(cached.findings, cached.cropBoxRanges);
    body.setJobId(cached.jobId);
    body.setName(cached.name);
    body.setCreationDate(cached.creationDate);
    DetailedReportDTO detailed = new DetailedReportDTO(body, VersionDTO.current());

    Path writtenPath = null;
    if (persistToDisk) {
      writtenPath = PacReportWriter.writeDetailed(detailed, cached.sourcePath, cached.outputFolder);
    }
    return Optional.of(new DetailedResult(detailed, writtenPath));
  }

  /** Look up a previously generated job by its id. */
  public Optional<CachedJob> findJob(String jobId) {
    if (jobId == null || jobId.isBlank()) return Optional.empty();
    return Optional.ofNullable(jobs.get(jobId));
  }

  private static SimpleReportDTO buildSimpleReport(PdfDocument pdf, String path, List<FindingDTO> findings,
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
   * Cached state for a generated job — enough to build the detailed report
   * lazily without re-opening the source PDF.
   */
  public record CachedJob(String jobId,
                          String name,
                          String sourcePath,
                          String outputFolder,
                          String creationDate,
                          List<FindingDTO> findings,
                          List<CropBoxRangeDTO> cropBoxRanges,
                          Path simplePath) {}

  /** Result of {@link #generateSimple} — the simple report + written paths. */
  public record SimpleResult(String jobId,
                             SimpleReportDTO simpleReport,
                             Path simplePath,
                             Path legacyPath) {}

  /**
   * Result of {@link #buildDetailed} — the report body and, when the caller
   * asked to persist, the file path on disk.
   */
  public record DetailedResult(DetailedReportDTO detailedReport, Path detailedPath) {}

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
