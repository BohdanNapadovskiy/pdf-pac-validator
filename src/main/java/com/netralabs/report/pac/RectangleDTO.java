package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Axis-aligned rectangle in PDF user-space (origin bottom-left) — property
 * order matches PAC ({@code top, bottom, left, right}).
 */
@JsonPropertyOrder({"top", "bottom", "left", "right"})
public record RectangleDTO(double top, double bottom, double left, double right) {}
