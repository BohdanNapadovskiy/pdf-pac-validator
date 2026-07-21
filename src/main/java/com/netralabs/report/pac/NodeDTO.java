package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a PAC report tree — either a branch (with {@code children}) or a
 * leaf (optionally with {@code issues} when the leaf failed).
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"value", "children", "issues"})
public class NodeDTO {
    private NodeValueDTO value;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<NodeDTO> children = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<IssueSummaryDTO> issues = new ArrayList<>();
}
