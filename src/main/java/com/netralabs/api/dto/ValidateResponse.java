package com.netralabs.api.dto;

public record ValidateResponse(String sourceFileName, String reportPath, String status) {

  public static ValidateResponse success(String sourceFileName, String reportPath) {
    return new ValidateResponse(sourceFileName, reportPath, "success");
  }

  public static ValidateResponse failed(String sourceFileName, String reportPath) {
    return new ValidateResponse(sourceFileName, reportPath, "failed");
  }
}