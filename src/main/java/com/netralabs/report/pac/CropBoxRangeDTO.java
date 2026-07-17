package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Contiguous range of pages sharing the same crop box, used by the detailed
 * report so viewers can map a per-issue bbox back to the page's coordinate space.
 */
@JsonPropertyOrder({"startPageIndex", "endPageIndex", "cropBox"})
public record CropBoxRangeDTO(int startPageIndex, int endPageIndex, RectangleDTO cropBox) {}
