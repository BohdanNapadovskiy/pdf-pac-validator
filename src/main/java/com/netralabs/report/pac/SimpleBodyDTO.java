package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code body} of the simple report — job identity, source-document
 * metadata, and the per-standard report trees.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"jobId", "name", "documentInformation", "reports", "creationDate"})
public class SimpleBodyDTO {
    private String jobId;
    private String name;
    private DocumentInformationDTO documentInformation;
    private List<ReportSectionDTO> reports = new ArrayList<>();
    private String creationDate;
}
