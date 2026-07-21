package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry in {@code body.issues[]} of the detailed report — groups all
 * failing issues under a single report type ("PDF/UA" or "WCAG").
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"type", "issues"})
public class DetailedTypeDTO {
    private String type;
    private List<DetailedIssueDTO> issues = new ArrayList<>();
}
