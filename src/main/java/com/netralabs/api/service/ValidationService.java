package com.netralabs.api.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.netralabs.Runner;
import com.netralabs.report.FindingDTO;
import com.netralabs.report.ReportBuilder;
import com.netralabs.report.ReportDTO;
import com.netralabs.report.ReportWriter;
import com.netralabs.report.quality.QualityReportBuilder;
import com.netralabs.report.wcag.WCAGReportBuilder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ValidationService {

  public Path validate(String sourcePdfPath, String outputFolder) throws Exception {
    String path = normalizePath(sourcePdfPath);
    String explicitOutput = resolveExplicitOutput(path, outputFolder);
    try (PdfDocument pdf = new PdfDocument(new PdfReader(new File(path)))) {
      Runner runner = new Runner();
      List<FindingDTO> findings = runner.runAll(pdf, path);
      ReportDTO report = ReportBuilder.build(pdf, path, findings);
      report.getReports().setWcag(WCAGReportBuilder.build(findings));
      report.getReports().setQuality(QualityReportBuilder.build(findings));
      return ReportWriter.write(report, path, explicitOutput);
    }
  }

  private static String resolveExplicitOutput(String sourcePath, String outputFolder) {
    if (outputFolder == null || outputFolder.isBlank()) return null;
    Path in = Paths.get(sourcePath).toAbsolutePath();
    String name = in.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    return Paths.get(normalizePath(outputFolder), base + ".report.json").toString();
  }

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
