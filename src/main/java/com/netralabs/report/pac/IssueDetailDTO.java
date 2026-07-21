package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-instance detail of a failing issue in the detailed report — the page it
 * occurred on and the bbox that highlights it.
 */
@JsonPropertyOrder({"pageIndex", "rectangle"})
public record IssueDetailDTO(int pageIndex, RectangleDTO rectangle) {}
