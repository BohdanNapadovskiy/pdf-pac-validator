package com.netralabs.report.pac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serialises the simple / detailed PAC reports to disk using indented JSON.
 * Emits {@code <name>.simple.json} and {@code <name>.detailed.json} next to
 * the source PDF (or in the caller-supplied output folder).
 */
public final class PacReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private PacReportWriter() {}

    public static Path writeSimple(SimpleReportDTO report, String inputPdfPath, String outputFolder) throws IOException {
        return write(report, inputPdfPath, outputFolder, ".simple.json");
    }

    public static Path writeDetailed(DetailedReportDTO report, String inputPdfPath, String outputFolder) throws IOException {
        return write(report, inputPdfPath, outputFolder, ".detailed.json");
    }

    private static Path write(Object report, String inputPdfPath, String outputFolder, String suffix) throws IOException {
        Path out = resolvePath(inputPdfPath, outputFolder, suffix);
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        MAPPER.writeValue(out.toFile(), report);
        return out;
    }

    private static Path resolvePath(String inputPdfPath, String outputFolder, String suffix) {
        Path in = Paths.get(inputPdfPath).toAbsolutePath();
        String name = in.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String fileName = base + suffix;
        if (outputFolder == null || outputFolder.isBlank()) {
            return in.resolveSibling(fileName);
        }
        return Paths.get(outputFolder).resolve(fileName);
    }
}
