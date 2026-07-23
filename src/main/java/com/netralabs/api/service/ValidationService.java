package com.netralabs.api.service;

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
import com.netralabs.report.pac.PacReportWriter;
import com.netralabs.report.pac.ReportSectionDTO;
import com.netralabs.report.pac.SimpleBodyDTO;
import com.netralabs.report.pac.SimpleReportBuilder;
import com.netralabs.report.pac.SimpleReportDTO;
import com.netralabs.report.pac.VersionDTO;
import com.netralabs.service.S3FileService;
import com.netralabs.service.TempFileSupport;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a PDF validation run.
 *
 * <p>
 * Single-shot flow: download the source PDF from S3, run the pipeline, then
 * upload both the simple and detailed PAC reports back to the same bucket
 * under the caller-supplied folder prefix. The response only reports the
 * S3 URIs where the two reports were stored — clients read the JSON directly
 * from S3.
 */
@Slf4j
@Service
public class ValidationService {

        private static final String CONTENT_TYPE_JSON = "application/json";

        @Autowired
        private S3FileService s3FileService;

        /**
         * Runs the full validation pipeline and uploads both PAC reports to S3.
         *
         * @param bucketName       S3 bucket for the source PDF and the destination reports
         * @param pdfPath          S3 key of the source PDF
         * @param outputFolderPath S3 key prefix under {@code bucketName} where the reports
         *                         are written; blank means the bucket root
         */
        public S3Result validate(String bucketName, String pdfPath, String outputFolderPath) throws Exception {

                UUID tempUuid = UUID.randomUUID();
                String localInputPath = TempFileSupport.buildTempPath(
                                tempUuid + "_" + TempFileSupport.sanitizeFileName(pdfPath));

                try {
                        s3FileService.downloadToFile(bucketName, pdfPath, localInputPath);
                } catch (Exception e) {
                        throw new IOException(e.getMessage());
                }

                String path = normalizePath(localInputPath);
                String jobId = UUID.randomUUID().toString();
                String creationDate = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                String name = displayName(pdfPath);

                try (PdfDocument pdf = new PdfDocument(new PdfReader(new File(path)))) {
                        Runner runner = new Runner();
                        List<FindingDTO> findings = runner.runAll(pdf, path);
                        FindingBboxEnricher.enrich(pdf, findings);

                        SimpleReportDTO simple = buildSimpleReport(pdf, path, findings, jobId, name, creationDate);
                        String simpleKey = joinKey(outputFolderPath, PacReportWriter.simpleReportFileName(pdfPath));
                        s3FileService.uploadBytes(bucketName, simpleKey,
                                        PacReportWriter.serialize(simple), CONTENT_TYPE_JSON);

                        List<CropBoxRangeDTO> cropBoxRanges = DetailedReportBuilder.buildCropBoxRanges(pdf);
                        DetailedReportDTO detailed = buildDetailedReport(findings, cropBoxRanges, jobId, name, creationDate);
                        String detailedKey = joinKey(outputFolderPath, PacReportWriter.detailedReportFileName(pdfPath));
                        s3FileService.uploadBytes(bucketName, detailedKey,
                                        PacReportWriter.serialize(detailed), CONTENT_TYPE_JSON);

                        return new S3Result(jobId, toS3Uri(bucketName, simpleKey), toS3Uri(bucketName, detailedKey));
                } finally {
                        deleteFile(localInputPath);
                }
        }

        private void deleteFile(String filePath) {
                if (filePath == null || filePath.isBlank()) return;
                try {
                        Files.deleteIfExists(Paths.get(filePath));
                        log.debug("Deleted temp file: {}", filePath);
                } catch (IOException e) {
                        log.warn("Unable to delete temp file: {}", filePath, e);
                }
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

        private static DetailedReportDTO buildDetailedReport(List<FindingDTO> findings,
                        List<CropBoxRangeDTO> cropBoxRanges, String jobId, String name, String creationDate) {
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

        private static String joinKey(String prefix, String fileName) {
                if (prefix == null || prefix.isBlank()) return fileName;
                String trimmed = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
                return trimmed + "/" + fileName;
        }

        private static String toS3Uri(String bucket, String key) {
                return "s3://" + bucket + "/" + key;
        }

        /** Result of {@link #validate} — the job id and the two S3 URIs. */
        public record S3Result(String jobId, String simpleReportS3Uri, String detailedReportS3Uri) {
        }

        /**
         * Convert MSYS / Git Bash style mount paths like "/c/foo/bar" to Windows
         * "C:/foo/bar".
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