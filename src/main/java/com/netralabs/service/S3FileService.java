package com.netralabs.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * AWS S3 utility methods for uploading and downloading files
 */
@Service
public class S3FileService {
    private static final Logger logger = LoggerFactory.getLogger(S3FileService.class);

    private final AmazonS3 s3Client;

    /**
     * Creates an S3 file service with a custom S3 client.
     *
     * @param s3Client The Amazon S3 client to use
     * @throws NullPointerException if s3Client is null
     */
    public S3FileService(AmazonS3 s3Client) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
    }

    /**
     * Downloads an S3 object to a local file path.
     * <p>
     * Creates parent directories automatically if they don't exist.
     * Replaces existing files at the target path.
     *
     * @param bucket    The S3 bucket name
     * @param key       The S3 object key
     * @return The Path to the downloaded file
     * @throws RuntimeException if download fails or I/O error occurs
     */
    public Path downloadToFile(String bucket, String key, String localPath) {

        try (S3Object s3Object = s3Client.getObject(bucket, key);
             InputStream in = s3Object.getObjectContent()) {

            if (s3Object == null || s3Object.getObjectContent() == null) {
                throw new IllegalStateException("S3 object or its content is null for key: " + key);
            }

            Path target = Paths.get(localPath);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);

            return target;

        } catch (AmazonS3Exception e) {
            throw new RuntimeException(
                    "Failed to download file from S3: " + e.getMessage() + ", Error Code: " + e.getErrorCode(),
                    e
            );
        } catch (IOException e) {
            throw new RuntimeException("I/O error while downloading from S3", e);
        }
    }

    /**
     * Uploads a local file to an S3 bucket.
     *
     * @param bucket The S3 bucket name
     * @param key    The S3 object key (destination path)
     * @param file   The local file to upload
     * @throws IllegalArgumentException if the file does not exist
     * @throws RuntimeException         if upload fails
     */
    public void uploadFile(String bucket, String key, File file) {
        logger.info("Uploading file to S3. bucket={}, key={}, file={}", bucket, key, file.getAbsolutePath());
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }

        try {
            s3Client.putObject(bucket, key, file);
            logger.info("Successfully uploaded file to S3: {}/{}", bucket, key);
        } catch (AmazonS3Exception e) {
            logger.info("Failed to upload file to S3: {}, Error Code: {}", e.getMessage(), e.getErrorCode());
            throw new RuntimeException(
                    "Failed to upload file to S3: " + e.getMessage() + ", Error Code: " + e.getErrorCode(),
                    e
            );
        }
    }

}