package com.netralabs.api.dto;

public record ValidateRequest(String pdfPath, String outputFolder, String bucketName) {}