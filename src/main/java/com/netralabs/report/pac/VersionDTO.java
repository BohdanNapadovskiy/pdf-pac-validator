package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Report format version stamp. Matches PAC's {@code { "major": 2, "minor": 0 }}.
 */
@JsonPropertyOrder({"major", "minor"})
public record VersionDTO(int major, int minor) {
    public static VersionDTO current() {
        return new VersionDTO(2, 0);
    }
}
