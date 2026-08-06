// Copyright 2026 Bohdan Napadovskyi
// Licensed under the Apache License, Version 2.0

package com.netralabs.api;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.netralabs.Runner;
import com.netralabs.report.FindingDTO;
import com.netralabs.report.pac.CropBoxRangeDTO;
import com.netralabs.report.pac.DetailedBodyDTO;
import com.netralabs.report.pac.DetailedReportBuilder;
import com.netralabs.report.pac.DetailedReportDTO;
import com.netralabs.report.pac.FindingBboxEnricher;
import com.netralabs.report.pac.PacDocumentInfoBuilder;
import com.netralabs.report.pac.ReportSectionDTO;
import com.netralabs.report.pac.SimpleBodyDTO;
import com.netralabs.report.pac.SimpleReportBuilder;
import com.netralabs.report.pac.SimpleReportDTO;
import com.netralabs.report.pac.VersionDTO;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure-library entry point for the validation pipeline — no Spring, no S3,
 * no CLI. Consumers pass a PDF (as a {@link File}, byte array, or already-open
 * {@link PdfDocument}) and get back the PAC-shaped {@link SimpleReportDTO} +
 * {@link DetailedReportDTO} in-memory. Serialization to PAC's JSON format
 * remains available via {@link com.netralabs.report.pac.PacReportWriter#serialize}.
 *
 * <p>Design goal: let external Java services (e.g. EAIDOS {@code pdf-core})
 * embed this validator without inheriting Spring Boot Web / AWS SDK from
 * the standalone REST + S3 layer.
 *
 * <p>For the standalone Spring Boot + S3 flow use
 * {@link com.netralabs.api.service.ValidationService} instead.
 */
public final class PdfValidator {

    private PdfValidator() {}

    /** In-memory result of one validation run. Both DTOs are pre-populated
     *  with a fresh {@code jobId} and a UTC {@code creationDate}. */
    public record Result(
            String jobId,
            String documentName,
            String creationDate,
            SimpleReportDTO simple,
            DetailedReportDTO detailed,
            List<FindingDTO> findings) {}

    /** Runs the pipeline against a file on disk.
     *
     *  @param pdfFile path to a readable PDF file
     *  @param documentName human display name for the report body — pass
     *                      {@code null} to derive from the file name
     *  @return in-memory reports; never null
     *  @throws IOException if the file cannot be read or parsed
     */
    public static Result validate(File pdfFile, String documentName) throws IOException {
        Objects.requireNonNull(pdfFile, "pdfFile");
        if (!pdfFile.isFile()) {
            throw new IOException("Not a readable file: " + pdfFile.getAbsolutePath());
        }
        try (PdfDocument pdf = new PdfDocument(new PdfReader(pdfFile))) {
            String name = documentName != null ? documentName : displayName(pdfFile.getAbsolutePath());
            return run(pdf, pdfFile.getAbsolutePath(), name);
        }
    }

    /** Convenience wrapper that derives the display name from the file. */
    public static Result validate(File pdfFile) throws IOException {
        return validate(pdfFile, null);
    }

    /** Runs the pipeline against an in-memory byte array. A temp file is
     *  created because veraPDF's greenfield foundry API expects a file path;
     *  the temp file is deleted before this method returns. */
    public static Result validate(byte[] pdfBytes, String documentName) throws IOException {
        Objects.requireNonNull(pdfBytes, "pdfBytes");
        if (pdfBytes.length == 0) throw new IOException("pdfBytes is empty");
        Path tmp = Files.createTempFile("pdf-validator-", ".pdf");
        try {
            Files.write(tmp, pdfBytes);
            try (PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)))) {
                String name = documentName != null ? documentName : "document";
                return run(pdf, tmp.toString(), name);
            }
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    /** Runs the pipeline against an already-open {@link PdfDocument}. The
     *  caller owns the document lifecycle (this method does not close it).
     *  {@code pdfPath} must point at the SAME bytes the document was opened
     *  from — veraPDF re-parses from disk in a separate JVM path.
     *
     *  Use this when your service already has a {@link PdfDocument} in hand
     *  (e.g. inside an EAIDOS pdf-core edit session) and can materialize
     *  the same bytes on disk without a round-trip. */
    public static Result validate(PdfDocument pdf, String pdfPath, String documentName)
            throws IOException {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(pdfPath, "pdfPath");
        String name = documentName != null ? documentName : displayName(pdfPath);
        return run(pdf, pdfPath, name);
    }

    // --- internals ----------------------------------------------------------

    private static Result run(PdfDocument pdf, String pdfPath, String name) throws IOException {
        String jobId = UUID.randomUUID().toString();
        String creationDate = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Runner runner = new Runner();
        List<FindingDTO> findings = runner.runAll(pdf, pdfPath);
        FindingBboxEnricher.enrich(pdf, findings);

        SimpleReportDTO simple = buildSimpleReport(pdf, pdfPath, findings, jobId, name, creationDate);
        List<CropBoxRangeDTO> cropBoxRanges = DetailedReportBuilder.buildCropBoxRanges(pdf);
        DetailedReportDTO detailed = buildDetailedReport(findings, cropBoxRanges, jobId, name, creationDate);

        return new Result(jobId, name, creationDate, simple, detailed, findings);
    }

    private static SimpleReportDTO buildSimpleReport(
            PdfDocument pdf, String pdfPath, List<FindingDTO> findings,
            String jobId, String name, String creationDate) {
        SimpleBodyDTO body = new SimpleBodyDTO();
        body.setJobId(jobId);
        body.setName(name);
        // PacDocumentInfoBuilder needs the file path to compute sizeInKb.
        // All PdfValidator entry points ensure a valid path — the byte[]
        // overload writes a temp file first.
        body.setDocumentInformation(PacDocumentInfoBuilder.build(pdf, pdfPath));
        List<ReportSectionDTO> sections = SimpleReportBuilder.build(findings);
        body.setReports(sections);
        body.setCreationDate(creationDate);
        return new SimpleReportDTO(body, VersionDTO.current());
    }

    private static DetailedReportDTO buildDetailedReport(
            List<FindingDTO> findings, List<CropBoxRangeDTO> cropBoxRanges,
            String jobId, String name, String creationDate) {
        DetailedBodyDTO body = DetailedReportBuilder.build(findings, cropBoxRanges);
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

    /** Legacy overload with just a {@link PdfDocument} — kept null-safe for
     *  the {@code documentName} arg. */
    public static Result validate(PdfDocument pdf, String pdfPath) throws IOException {
        return validate(pdf, pdfPath, null);
    }
}
