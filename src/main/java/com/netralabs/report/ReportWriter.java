package com.netralabs.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ReportWriter {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT);

  private ReportWriter() {}

  public static Path write(ReportDTO report, String inputPdfPath, String explicitOutput) throws IOException {
    Path out = explicitOutput != null
        ? Paths.get(explicitOutput)
        : defaultOutputFor(inputPdfPath);
    Files.createDirectories(out.toAbsolutePath().getParent());
    MAPPER.writeValue(out.toFile(), report);
    return out;
  }

  private static Path defaultOutputFor(String inputPdfPath) {
    Path in = Paths.get(inputPdfPath).toAbsolutePath();
    String name = in.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    return in.resolveSibling(base + ".report.json");
  }
}