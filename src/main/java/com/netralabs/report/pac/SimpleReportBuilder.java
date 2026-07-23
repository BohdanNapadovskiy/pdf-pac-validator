package com.netralabs.report.pac;

import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;
import com.netralabs.vera.VeraRuleMapping;
import com.netralabs.wcag.WCAGCriterion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code body.reports[]} array of the simple report — one entry each
 * for the "PDF/UA" and "WCAG" trees. Groups findings by {@link PDFUACheckpoint},
 * projects them onto PAC's hierarchical taxonomy, and rolls counts / severity
 * up every branch.
 * <p>
 * <strong>uaIndex formula (matched to PAC's reference export):</strong>
 * {@code 100 - (100 * errorCount + 5 * warningCount) * 100 / totalChecks},
 * where {@code totalChecks = passedCount + errorCount + warningCount}.
 * A document with only passes scores 100; each error costs 100 units of
 * fractional weight, each warning 5, expressed as a percentage of the
 * total check volume.
 */
public final class SimpleReportBuilder {

    private SimpleReportBuilder() {}

    public static List<ReportSectionDTO> build(List<FindingDTO> findings) {
        Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint = groupByCheckpoint(findings);
        List<ReportSectionDTO> sections = new ArrayList<>();
        sections.add(buildPdfUaSection(byCheckpoint));
        sections.add(buildWcagSection(byCheckpoint));
        return sections;
    }

    // ================================================================
    // PDF/UA tree
    // ================================================================

    private static ReportSectionDTO buildPdfUaSection(Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint) {
        // Build a nested map that preserves PDFUACheckpoint declaration order (which is
        // itself PAC-ordered). Structure: category → subCategory → group ("" if none)
        // → leaves in enum order.
        Map<String, Map<String, Map<String, List<PDFUACheckpoint>>>> tree = new LinkedHashMap<>();
        for (PDFUACheckpoint cp : PDFUACheckpoint.values()) {
            if (!"PDF/UA".equals(cp.getReportName())) continue;
            String groupKey = cp.getGroup() == null ? "" : cp.getGroup();
            tree.computeIfAbsent(cp.getCategory(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(cp.getSubCategory(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(groupKey, k -> new ArrayList<>())
                    .add(cp);
        }

        NodeDTO root = new NodeDTO();
        Counts rootCounts = new Counts();

        for (Map.Entry<String, Map<String, Map<String, List<PDFUACheckpoint>>>> catEntry : tree.entrySet()) {
            String category = catEntry.getKey();
            NodeDTO categoryNode = new NodeDTO();
            Counts categoryCounts = new Counts();

            for (Map.Entry<String, Map<String, List<PDFUACheckpoint>>> subEntry : catEntry.getValue().entrySet()) {
                String subCategory = subEntry.getKey();
                NodeDTO subNode = new NodeDTO();
                Counts subCounts = new Counts();

                for (Map.Entry<String, List<PDFUACheckpoint>> grpEntry : subEntry.getValue().entrySet()) {
                    String group = grpEntry.getKey();
                    List<PDFUACheckpoint> leaves = grpEntry.getValue();
                    if (group.isEmpty()) {
                        for (PDFUACheckpoint cp : leaves) {
                            NodeDTO leafNode = buildLeafNode(cp, byCheckpoint.getOrDefault(cp, List.of()), false);
                            subCounts.add(leafNode.getValue());
                            subNode.getChildren().add(leafNode);
                        }
                    } else {
                        NodeDTO groupNode = new NodeDTO();
                        Counts groupCounts = new Counts();
                        for (PDFUACheckpoint cp : leaves) {
                            NodeDTO leafNode = buildLeafNode(cp, byCheckpoint.getOrDefault(cp, List.of()), false);
                            groupCounts.add(leafNode.getValue());
                            groupNode.getChildren().add(leafNode);
                        }
                        String groupCheckId = PacCheckId.forPdfUaBranch(category, subCategory, group);
                        groupNode.setValue(makeValue(
                                groupCheckId != null ? groupCheckId : group,
                                group,
                                groupCounts));
                        subCounts.add(groupNode.getValue());
                        subNode.getChildren().add(groupNode);
                    }
                }

                String subCheckId = PacCheckId.forPdfUaBranch(category, subCategory);
                subNode.setValue(makeValue(
                        subCheckId != null ? subCheckId : subCategory,
                        subCategory,
                        subCounts));
                categoryCounts.add(subNode.getValue());
                categoryNode.getChildren().add(subNode);
            }

            String catCheckId = PacCheckId.forPdfUaBranch(category);
            categoryNode.setValue(makeValue(
                    catCheckId != null ? catCheckId : category,
                    category,
                    categoryCounts));
            rootCounts.add(categoryNode.getValue());
            root.getChildren().add(categoryNode);
        }

        root.setValue(makeValue(PacCheckId.PDFUA_ROOT, "PDF/UA", rootCounts));

        ReportSectionDTO section = new ReportSectionDTO();
        section.setType("PDF/UA");
        section.setUaIndex(computeUaIndex(rootCounts));
        section.setReport(root);
        return section;
    }

    // ================================================================
    // WCAG tree
    // ================================================================

    private static ReportSectionDTO buildWcagSection(Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint) {
        Map<String, Map<String, Map<String, List<WCAGCriterion>>>> tree = new LinkedHashMap<>();
        for (WCAGCriterion c : WCAGCriterion.values()) {
            tree.computeIfAbsent(c.getPrinciple(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(c.getGuideline(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(c.getCriterion(), k -> new ArrayList<>())
                    .add(c);
        }

        NodeDTO root = new NodeDTO();
        Counts rootCounts = new Counts();

        for (Map.Entry<String, Map<String, Map<String, List<WCAGCriterion>>>> pEntry : tree.entrySet()) {
            String principle = pEntry.getKey();
            NodeDTO principleNode = new NodeDTO();
            Counts principleCounts = new Counts();

            for (Map.Entry<String, Map<String, List<WCAGCriterion>>> gEntry : pEntry.getValue().entrySet()) {
                String guideline = gEntry.getKey();
                NodeDTO guidelineNode = new NodeDTO();
                Counts guidelineCounts = new Counts();

                for (Map.Entry<String, List<WCAGCriterion>> cEntry : gEntry.getValue().entrySet()) {
                    String criterion = cEntry.getKey();
                    List<WCAGCriterion> entries = cEntry.getValue();
                    NodeDTO criterionNode = buildCriterionNode(criterion, entries, byCheckpoint);
                    guidelineCounts.add(criterionNode.getValue());
                    guidelineNode.getChildren().add(criterionNode);
                }

                guidelineNode.setValue(makeValue(
                        resolveWcagId(guideline),
                        guideline,
                        guidelineCounts));
                principleCounts.add(guidelineNode.getValue());
                principleNode.getChildren().add(guidelineNode);
            }

            principleNode.setValue(makeValue(
                    resolveWcagId(principle),
                    principle,
                    principleCounts));
            rootCounts.add(principleNode.getValue());
            root.getChildren().add(principleNode);
        }

        root.setValue(makeValue(PacCheckId.WCAG_ROOT, "WCAG", rootCounts));

        ReportSectionDTO section = new ReportSectionDTO();
        // PAC labels the WCAG section by its root checkId, not the display name.
        section.setType(PacCheckId.WCAG_ROOT);
        section.setUaIndex(computeUaIndex(rootCounts));
        section.setReport(root);
        return section;
    }

    private static NodeDTO buildCriterionNode(String criterion,
                                              List<WCAGCriterion> entries,
                                              Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint) {
        NodeDTO node = new NodeDTO();
        String criterionCheckId = resolveWcagId(criterion);

        // Case A: criterion has no named sub-leaves — one WCAGCriterion with leaf==null.
        // The criterion node itself becomes the leaf.
        if (entries.size() == 1 && entries.get(0).getLeaf() == null) {
            WCAGCriterion only = entries.get(0);
            LeafRollup rollup = evaluateWcagLeaf(only, byCheckpoint);
            node.setValue(makeValue(criterionCheckId, criterion, rollup.counts));
            // Attach any issue summaries from the source checkpoints so the criterion
            // row itself carries the same failure summary a leaf would.
            node.getIssues().addAll(rollup.issues);
            return node;
        }

        // Case B: criterion has named sub-leaves — recurse into each source checkpoint.
        Counts counts = new Counts();
        for (WCAGCriterion entry : entries) {
            // Every named leaf under a criterion sources at least one PDFUACheckpoint;
            // when there are multiple sources the first drives the checkId (PAC also
            // groups by primary source).
            NodeDTO leafNode;
            if (entry.getSources().isEmpty()) {
                leafNode = new NodeDTO();
                leafNode.setValue(makeValue(entry.getLeaf(), entry.getLeaf(), new Counts()));
            } else {
                PDFUACheckpoint primary = entry.getSources().get(0);
                leafNode = buildLeafNode(primary, byCheckpoint.getOrDefault(primary, List.of()),
                        entry.isErrorsOnly());
            }
            counts.add(leafNode.getValue());
            node.getChildren().add(leafNode);
        }
        node.setValue(makeValue(criterionCheckId, criterion, counts));
        return node;
    }

    // ================================================================
    // Leaf-node construction
    // ================================================================

    private static NodeDTO buildLeafNode(PDFUACheckpoint cp,
                                         List<FindingDTO> findings,
                                         boolean errorsOnly) {
        NodeDTO node = new NodeDTO();
        Counts counts = new Counts();
        int errors = 0, warnings = 0;
        for (FindingDTO f : findings) {
            Severity sev = f.getSeverity();
            if (sev == Severity.ERROR) errors++;
            else if (sev == Severity.WARNING) warnings++;
            else if (sev == Severity.PASSED && !errorsOnly) counts.passed++;
        }
        counts.error = errors;
        counts.warning = warnings;

        boolean hasRunnableNative = cp.getFactory() != null && !cp.getPhases().isEmpty();
        boolean covered = hasRunnableNative || VeraRuleMapping.covers(cp);
        boolean noFindings = errors == 0 && warnings == 0 && counts.passed == 0;
        // Uncovered checkpoints with nothing to report are Skipped in PAC's sense.
        boolean skipped = noFindings && !covered;

        String checkId = PacCheckId.forLeaf(cp);
        String caption = cp.getElement();
        node.setValue(makeValueForLeaf(checkId, caption, counts, skipped));

        if (errors > 0 || warnings > 0) {
            PacIssueId.IssueCode code = PacIssueId.forCheckpoint(cp,
                    cp.getErrorMessage() != null && !cp.getErrorMessage().isEmpty()
                            ? cp.getErrorMessage() : cp.getElement());
            IssueSummaryDTO issue = new IssueSummaryDTO();
            issue.setIssueId(code.id());
            issue.setCaption(code.caption());
            int count = errors + warnings;
            issue.setCount(count);
            PacSeverity sev = errors > 0 ? PacSeverity.ERROR : PacSeverity.WARNING;
            issue.setSeverity(sev.label());
            issue.setSeverityId(sev.id());
            node.getIssues().add(issue);
        }
        return node;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static NodeValueDTO makeValue(String checkId, String caption, Counts counts) {
        NodeValueDTO v = new NodeValueDTO();
        v.setCheckId(checkId);
        v.setCaption(caption);
        v.setTerminationCount(counts.termination);
        v.setErrorCount(counts.error);
        v.setWarningCount(counts.warning);
        v.setPassedCount(counts.passed);
        PacSeverity sev = deriveSeverity(counts, false);
        v.setSeverity(sev.label());
        v.setSeverityId(sev.id());
        return v;
    }

    private static NodeValueDTO makeValueForLeaf(String checkId, String caption, Counts counts, boolean skipped) {
        NodeValueDTO v = new NodeValueDTO();
        v.setCheckId(checkId);
        v.setCaption(caption);
        v.setTerminationCount(counts.termination);
        v.setErrorCount(counts.error);
        v.setWarningCount(counts.warning);
        v.setPassedCount(counts.passed);
        PacSeverity sev = deriveSeverity(counts, skipped);
        v.setSeverity(sev.label());
        v.setSeverityId(sev.id());
        return v;
    }

    private static PacSeverity deriveSeverity(Counts c, boolean skippedFallback) {
        if (c.termination > 0) return PacSeverity.ERROR;
        if (c.error > 0) return PacSeverity.ERROR;
        if (c.warning > 0) return PacSeverity.WARNING;
        if (c.passed > 0) return PacSeverity.PASSED;
        return skippedFallback ? PacSeverity.SKIPPED : PacSeverity.SKIPPED;
    }

    private static double computeUaIndex(Counts root) {
        long total = (long) root.passed + root.error + root.warning;
        if (total == 0) return 100.0;
        // 100 - (100 * errors + 5 * warnings) * 100 / total.
        // Errors count 100 units each, warnings 5. Formula derived to match PAC's
        // reference export (42080P/21E/43W → 94.50733752620545). Clamped to [0,100]
        // — heavy-failure documents can produce arbitrarily negative raw values.
        double weightedPenalty = 100.0 * root.error + 5.0 * root.warning;
        double raw = 100.0 - weightedPenalty * 100.0 / total;
        if (raw < 0.0) return 0.0;
        if (raw > 100.0) return 100.0;
        return raw;
    }

    private static String resolveWcagId(String label) {
        String id = PacCheckId.forWcagBranch(label);
        return id != null ? id : label;
    }

    private static Map<PDFUACheckpoint, List<FindingDTO>> groupByCheckpoint(List<FindingDTO> findings) {
        Map<PDFUACheckpoint, List<FindingDTO>> map = new EnumMap<>(PDFUACheckpoint.class);
        for (PDFUACheckpoint cp : PDFUACheckpoint.values()) {
            map.put(cp, new ArrayList<>());
        }
        for (FindingDTO f : findings) {
            if (f.getCheckpoint() != null) map.get(f.getCheckpoint()).add(f);
        }
        return map;
    }

    // ================================================================
    // WCAG leaf evaluation (for criterion-as-leaf case)
    // ================================================================

    private static LeafRollup evaluateWcagLeaf(WCAGCriterion leaf,
                                               Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint) {
        Counts counts = new Counts();
        List<IssueSummaryDTO> issues = new ArrayList<>();
        for (PDFUACheckpoint cp : leaf.getSources()) {
            int errors = 0, warnings = 0;
            for (FindingDTO f : byCheckpoint.getOrDefault(cp, List.of())) {
                Severity sev = f.getSeverity();
                if (sev == Severity.ERROR) errors++;
                else if (sev == Severity.WARNING) warnings++;
                else if (sev == Severity.PASSED && !leaf.isErrorsOnly()) counts.passed++;
            }
            counts.error += errors;
            counts.warning += warnings;
            if (errors + warnings > 0) {
                PacIssueId.IssueCode code = PacIssueId.forCheckpoint(cp,
                        cp.getErrorMessage() != null && !cp.getErrorMessage().isEmpty()
                                ? cp.getErrorMessage() : cp.getElement());
                IssueSummaryDTO issue = new IssueSummaryDTO();
                issue.setIssueId(code.id());
                issue.setCaption(code.caption());
                issue.setCount(errors + warnings);
                PacSeverity sev = errors > 0 ? PacSeverity.ERROR : PacSeverity.WARNING;
                issue.setSeverity(sev.label());
                issue.setSeverityId(sev.id());
                issues.add(issue);
            }
        }
        return new LeafRollup(counts, issues);
    }

    private record LeafRollup(Counts counts, List<IssueSummaryDTO> issues) {}

    private static final class Counts {
        int termination;
        int error;
        int warning;
        int passed;

        void add(NodeValueDTO child) {
            termination += child.getTerminationCount();
            error += child.getErrorCount();
            warning += child.getWarningCount();
            passed += child.getPassedCount();
        }
    }
}
