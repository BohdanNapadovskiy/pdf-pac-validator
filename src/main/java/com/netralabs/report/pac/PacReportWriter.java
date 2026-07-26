package com.netralabs.report.pac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Serialises the simple / detailed PAC reports as JSON bytes for the S3 upload path,
 * and derives the canonical {@code <base>.simple.json} / {@code <base>.detailed.json}
 * file names from an input path.
 */
public final class PacReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private PacReportWriter() {}

    public static byte[] serialize(Object report) throws IOException {
        return MAPPER.writeValueAsBytes(report);
    }

    public static String simpleReportFileName(String inputPath) {
        return baseName(inputPath) + ".simple.json";
    }

    public static String detailedReportFileName(String inputPath) {
        return baseName(inputPath) + ".detailed.json";
    }

    private static String baseName(String inputPath) {
        String name = Paths.get(inputPath).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
