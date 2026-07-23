package com.netralabs.report.pac;

/**
 * PAC-canonical severity strings and their numeric identifiers used in the
 * simple / detailed report output. Order matches PAC's export.
 */
public enum PacSeverity {
    ERROR("Error", 0),
    WARNING("Warning", 1),
    PASSED("Passed", 2),
    SKIPPED("Skipped", 3);

    private final String label;
    private final int id;

    PacSeverity(String label, int id) {
        this.label = label;
        this.id = id;
    }

    public String label() { return label; }

    public int id() { return id; }
}
