package com.netralabs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem temp-file helpers shared by the tagging pipelines.
 * <p>
 * All methods are stateless. {@link #buildTempPath(String)} resolves against
 * {@code java.io.tmpdir}; {@link #sanitizeFileName(String)} strips path separators
 * and characters that would be illegal as filenames on Windows; {@link #deleteTempFile(String)}
 * is a quiet best-effort delete that logs a warning on failure.
 */
public final class TempFileSupport {

    private static final Logger logger = LoggerFactory.getLogger(TempFileSupport.class);

    private TempFileSupport() {
    }

    public static String buildTempPath(String fileName) {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        return tempDir.resolve(sanitizeFileName(fileName)).toString();
    }

    public static String sanitizeFileName(String name) {
        String safeName = Path.of(name).getFileName().toString();
        return safeName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static void deleteTempFile(String path) {
        if (path == null) return;
        File f = new File(path);
        if (f.exists() && !f.delete()) {
            logger.warn("Failed to delete temp file: {}", path);
        }
    }
}