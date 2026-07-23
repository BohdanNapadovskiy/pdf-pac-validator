package com.netralabs.api.dto;

public record ValidateRequest(String bucketName, String pdfPath, String outputFolderPath) {}