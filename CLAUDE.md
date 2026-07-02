why # CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and run

Maven project, Java 21 source/target in `<properties>` but the `maven-compiler-plugin` is pinned to source/target 17 — keep both in mind when bumping language features.

```bash
mvn -q -DskipTests package
mvn dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp "target/classes;$(cat /tmp/cp.txt)" PDFValidator <path-to-pdf> [-o <output.json>]
```

Default output is `<input>.report.json` next to the source PDF. The entry point lives in the **default package** at `src/main/java/PDFValidator.java`.

### Manual test fixtures

- **Input PDFs:** `C:\projects\pdf\` — use these for manual / ad-hoc runs against real-world samples.
- **Output reports:** `C:\projects\pdf\report\` — always pass `-o C:\projects\pdf\report\<name>.report.json` so the generated JSON lands here instead of polluting the input folder. Create the folder if it doesn't exist.

```bash
java -cp "target/classes;$(cat /tmp/cp.txt)" PDFValidator "C:/projects/pdf/<name>.pdf" -o "C:/projects/pdf/report/<name>.report.json"
```

`src/test/` exists but is empty — `mvn test` is a no-op.

## Coding standards

### Language level
- **Compiler target is 17**, even though `<properties>` says Java 21. Write only Java 17 language features.
- OK (Java ≤17): `record`, `sealed` classes/interfaces, `instanceof` pattern matching, text blocks, switch expressions.
- NOT OK until the compiler target is bumped: pattern matching for `switch`, record patterns, virtual threads, sequenced collections. If a newer feature is genuinely needed, flag the target bump instead of silently using it.

### Java specifics
- Prefer `record` for DTOs/value objects (`BBoxDTO`, `CountsDTO`, `FindingDTO`, finding leaves) — immutable, value-based `equals`/`hashCode`.
- Immutability by default: `final` fields; expose collections via `List.copyOf` / unmodifiable views.
- `Optional` only as a return type — never a field or parameter; no `.get()` without a guard.
- Streams for collection transforms; no side effects inside a stream pipeline.
- `try-with-resources` for every closeable (iText `PdfDocument`, readers, streams).
- New code goes in a named package (`com.netralabs.*`). The default-package `PDFValidator` entry point is legacy — don't grow it.
- **Avoid reflection.** `Runner.safeStructPageNumber` and `metadata/XMPMetaHelper` use it only for iText 7.x/9.x compat; on the next iText bump convert to direct calls rather than adding more.

### Principles
- Correctness and readability over cleverness; long-term maintainability first.
- SOLID: one responsibility per `Rule`; constructor injection with `final` fields.
- Composition over inheritance — new behaviour is a new `Rule`, not a subclass tree.
- Fail-fast: guard clauses / `Objects.requireNonNull` at method entry, not deep inside.
- Small methods, early returns, shallow nesting.
- No magic numbers/strings → named constants. (Exception: PAC-canonical labels on the enum are intentionally verbatim — see Invariants.)

### Logging & error handling
- SLF4J parameterized logging (`log.debug("page={} mcid={}", pageNum, mcid)`); never string concatenation, never `System.out`.
- Throw specific exceptions; never swallow a catch; don't use exceptions for control flow.
- A failing rule should emit a finding, not crash the pipeline — keep `Runner` resilient per rule.

### Patterns — follow the ones already here
- **Strategy** = `Rule` (`phases()` + `run(Context)`). Add behaviour as a new `Rule`.
- **Factory** via `Supplier<? extends Rule>` on `PDFUACheckpoint`. Wire a native rule with the existing recipe (implement `Rule`, replace the `null, null` pair with `YourRule::new, Phase.X`).
- **Adapter** for the external engine = `VeraPdfAdapterRule`. New external-engine coverage goes through an adapter, not inline calls in `Runner`.
- **Visitor** for traversal = `StructWalk.walk(pdf, visitor)`. Reuse it for flat DOCUMENT-phase structure walks instead of hand-rolling DFS.
- Declare a rule's **natural `Phase`** and let `Runner` iterate, rather than looping pages inside a `DOCUMENT` rule.

### Patterns — avoid
- Manual singletons; large `if`/`switch` ladders where the enum registry already dispatches.
- Premature abstractions / interface-per-class with no second implementation or test boundary (YAGNI).
- Anemic classes where domain behaviour belongs — but DTOs crossing the JSON boundary stay plain `record`s.

### Invariants — do not break
- `PDFUACheckpoint` and `WCAGCriterion` label strings are **PAC-canonical** (PDF/UA verified against the 11-screenshot reference for `1.pdf`; WCAG verified against the 15-screenshot reference set). Keep byte-identical; changing them silently breaks `docs/pac-diff-report.md` and breaks the WCAG mapping.
- JSON property order is pinned via `@JsonPropertyOrder`: `name → status → counts → children/findings`, finding leaf `message → severity → page → bBox`. Preserve it for downstream consumers.
- Top-level JSON shape is `{ "reports": { "document", "info", "PDF/UA", "WCAG" } }`. The keys `"PDF/UA"` and `"WCAG"` are emitted via `@JsonProperty` on `ReportsDTO`; keep them stable.
- `CountsDTO` and the discrete `status` enum are kept in sync — update both together.

## Architecture

PDF/UA + WCAG 2.2 accessibility validator. Each `PDFUACheckpoint` is either implemented by a native iText 9 rule, delegated to veraPDF via an adapter, or still a stub. The output is a PAC-style JSON tree with counts rolled up at every level, grouped under `reports."PDF/UA"` and `reports."WCAG"` — the WCAG branch remaps the same findings onto PAC's WCAG 2.2 tree. Companion docs: `IMPLEMENTATION_PLAN.md`, `REPORT_REQUIREMENTS.md`, `docs/verapdf-adapter.md`, `docs/pac-diff-report.md`.

### The four-phase pipeline (`com.netralabs.Runner`)

Every rule declares which `Phase`s it participates in (`DOCUMENT`, `PAGE`, `CONTENT`, `STRUCT` — see `domain/Phase.java`). `Runner.runAll(pdf, pdfPath)`:

1. Runs **veraPDF once** on `pdfPath` (via `VeraRunner.validate`) and stores the results in a `VeraValidationResults` bag, passed through every `Context`. veraPDF is invoked even when no rules are mapped — keeps the pipeline uniform.
2. Materializes one `Rule` per `PDFUACheckpoint`. Precedence:
   - If `cp.getFactory() != null && !cp.getPhases().isEmpty()` → native rule.
   - Else if `VeraRuleMapping.covers(cp)` → synthesize a `VeraPdfAdapterRule(cp)`.
   - Else → skipped (becomes `NOT_IMPLEMENTED` in the report).
3. Runs `DOCUMENT`-phase rules once.
4. Iterates pages, running `PAGE`-phase then `CONTENT`-phase rules per page.
5. Performs a DFS over the structure tree via `TagTreePointer`, running `STRUCT`-phase rules at each `PdfStructElem`.

The shared input to every rule is `basic.content.Context`, which carries `(pdf, page, ttp, pageNum, veraResults)`. Fields not relevant for the current phase are null.

Most rules currently declare `Phase.DOCUMENT` even when they internally loop over pages (e.g. `ValidateTaggedCoverage` walks every page itself). Prefer declaring the natural phase so `Runner` does the iteration.

### The checkpoint registry (`domain/PDFUACheckpoint`)

`PDFUACheckpoint` is the **single source of truth** for the validator's checklist. Each constant carries:
- ISO 14289 categorization strings (`category`, `subCategory`, `element`, `errorMessage`) — used verbatim as JSON labels.
- A `Supplier<? extends Rule>` factory — `null` means "delegate to veraPDF or treat as not implemented".
- A varargs `Phase...` declaring participation — empty means "not run natively".

The enum's label strings are **PAC-canonical** (verified against the 11-screenshot reference for `1.pdf`). When editing labels, keep them byte-identical to PAC's so the diff report stays meaningful.

To wire up a new native rule:
1. Implement `com.netralabs.Rule` with appropriate `phases()` and `run(Context)`.
2. Replace the `null, null` pair on the matching enum constant with `YourRule::new, Phase.X`.

`Runner` falls back to veraPDF when the native factory is null but `VeraRuleMapping` covers the checkpoint.

### veraPDF adapter (`com.netralabs.vera`)

Closes coverage gaps for stub checkpoints without abandoning the native engine. See `docs/verapdf-adapter.md`.

- `VeraRunner` parses the PDF once with veraPDF's greenfield foundry against the `PDFUA_1` profile. Constructor flags: unlimited per-rule failures (`-1`), **passes NOT logged** (avoids veraPDF's 10K total assertion cap blowing up failure capture), error messages on. The trade-off — vera-owned checkpoints have no PASSED counts — is documented and may be revisited.
- `VeraRuleMapping` maps runtime rule IDs (form: `"ISO 14289-1:2014-<clause>-<test>"`, **with a space**, not the XML profile's `ISO_14289_1` underscored form) to `PDFUACheckpoint`. 45 entries → 29 distinct checkpoints today.
- `VeraPdfAdapterRule(cp)` drains its bucket via `ctx.veraResults().findingsFor(cp)`.

### Content stream walking (`basic.content`)

**Single mechanism since Step 7:** `ContentWalker.walkPage(pdf, pageNo, hook)` drives an iText `PdfCanvasProcessor` with a `ScopeAwareListener` that:
- Synthesizes BMC/EMC pulses to the `Hook` by diffing `getCanvasTagHierarchy()` between consecutive render events.
- Computes a `BBoxDTO` per `RENDER_TEXT` / `RENDER_IMAGE` / `RENDER_PATH` event in PDF user-space (origin bottom-left).

`Hook` callbacks: `onBeginArtifact / onBeginTaggedMcid(mcid, pgDict) / onBeginOtherMarked(tag) / onEndMarked / onPainted(bbox) / onShowText(pdfString, bbox)`. The legacy custom tokenizer was removed.

The four content rules (`ValidateTaggedCoverage`, `ValidateArtifactsInsideTagged`, `ValidateTaggedInsideArtifacts`, `ValidateUnicodeMapping`) emit **per-item findings**, one finding per painted/text-show event with its bbox on error. Per-page summary findings are also emitted for status determination.

### Structure tree helpers

- `logicalstructure.structureelements.StructWalk` — `walk(pdf, visitor)` DFS, role normalization via the catalog's `RoleMap`, heading-level extraction (handles `H1`–`H6` and generic `H` with `/Lvl`).
- `basic.pdfsyntax.StructUtils.pageNumOf(pdf, structElemDict)` resolves the page number for a struct element by walking `/Pg` on the element or any MCR child. Used by Step-5 page plumbing across heading/note/lang/syntax rules.
- `Runner.dfsStruct` does its own DFS using `TagTreePointer` for STRUCT-phase rules; `StructWalk` is the helper used by DOCUMENT-phase rules that need a flat structure traversal.

### Report layer (`com.netralabs.report`)

Two sibling builders produce one combined JSON, wrapped by `ReportsDTO` (`{ "reports": { document, info, "PDF/UA", "WCAG" } }`):

- `ReportBuilder.build(pdf, documentPath, findings)` builds the PDF/UA branch (`PdfUaSectionDTO` = counts + summary + shortSummary + categories). Findings are grouped by `(category → subCategory → element)`, `CheckpointStatus` derived per node, counts rolled up. Each level carries a `CountsDTO { passed, warning, error }` plus the discrete `status` enum (kept in sync for downstream consumers).
- `WCAGReportBuilder.build(findings)` builds the WCAG branch — see "WCAG 2.2 view" below.

`PDFValidator` calls both: `ReportBuilder.build(...)` then `report.getReports().setWcag(WCAGReportBuilder.build(findings))`.

Status policy in `ReportBuilder.toCheckpointDto` (also used by `WCAGReportBuilder.evaluateLeaf`):
- ERROR or WARNING findings → `FAILED`
- PASSED findings only → `PASSED`
- No findings + (native rule runnable OR vera covers the checkpoint) → `NOT_APPLICABLE`
- Otherwise → `NOT_IMPLEMENTED`

JSON property order is pinned via `@JsonPropertyOrder` so every node reads `name → status → counts → children/findings`.

`FindingEntryDTO` (the per-finding leaf in the JSON) has fields `message → severity → page → bBox`. `ReportBuilder.toEntry` falls back to `cp.getErrorMessage()` when a rule didn't set a message of its own; veraPDF messages flow through from `TestAssertion.getMessage()`.

### WCAG 2.2 view (`com.netralabs.wcag`, `com.netralabs.report.wcag`)

Pure remapping layer — no new validation. Every WCAG finding is sourced from an existing `PDFUACheckpoint`.

- `WCAGCriterion` enum lists ~94 leaves across `Principle → Guideline → Criterion → Leaf` (PAC-canonical labels). Each constant declares its source `PDFUACheckpoint`s; leaves with no source resolve to `NOT_IMPLEMENTED`.
- `leaf == null` on an entry signals "this criterion has no named sub-leaves in PAC" (e.g. 1.2.1, 2.1.1, 4.1.2) — the criterion row itself carries the status. Otherwise the criterion has multiple named child leaves (1.1.1, 1.3.1, 2.4.2, 3.1.2, 4.1.1) and counts/status roll up from them.
- `WCAGReportBuilder` fans `FindingDTO`s out to leaves via the source mapping, then rolls Leaf → Criterion → Guideline → Principle → root using the same status policy as `ReportBuilder.toCheckpointDto`.
- DTO tree: `WCAGReportDTO → PrincipleDTO → GuidelineDTO → CriterionDTO → WCAGLeafDTO`. Each level mirrors the PDF/UA invariants (`@JsonPropertyOrder({"name","status","counts",...})`).

Rendering-dependent leaves (`1.4.3 Contrast of text`, `1.4.10 Reflow`, `1.4.11 Non-text Contrast`, `1.4.12 Text Spacing`, `4.1.2 Name/Role/Value`, `4.1.3 Status Messages`) intentionally stay `NOT_IMPLEMENTED` — they require pixel-level rendering or form-runtime analysis we don't currently do.

To wire a new WCAG leaf to an existing checkpoint: add a `WCAGCriterion` constant pointing at the source `PDFUACheckpoint`(s). To add a new WCAG-only validation: implement it as a `Rule` against a new `PDFUACheckpoint`, then map the checkpoint into a `WCAGCriterion`.

### Reflective iText calls

`Runner.safeStructPageNumber` and `metadata/XMPMetaHelper` probe iText APIs by reflection to stay compatible across iText 7.x / 9.x. When upgrading iText, fix these to direct calls rather than adding more reflection.

## Testing

`src/test/` exists but is empty; `mvn test` is currently a no-op. When adding tests:

- JUnit 5; AssertJ for fluent assertions; Mockito only for genuinely external collaborators (not for value objects / DTOs).
- Naming `method_givenCondition_expectedResult`; structure Arrange-Act-Assert.
- Use small fixture PDFs under `src/test/resources`; assert on the report tree (`CountsDTO`, `status`) and on per-finding bbox/message — not on log output.
- Cover error and edge paths (untagged content, missing `/Pg`, generic `H` with `/Lvl`, vera-only checkpoints), not just the happy path.

## Working agreements

- For nontrivial work (new rule, refactor across files, phase/pipeline change) propose a short plan first, then implement.
- Don't add Maven dependencies without asking.
- Don't change the public JSON shape, enum labels, or `Phase` semantics without an explicit request (see Invariants).
- Match the style of surrounding code; if it conflicts with these rules, ask.

## Resources

- `src/main/resources/logback.xml` — SLF4J + Logback config.
- `src/main/resources/mapping.json` — declarative mapping from external rule taxonomies (ISO 14289-2 clauses, ISO 32005, tag hints) into category/subcategory/element triples. **Not loaded today** — reserved for future downstream report mapping.

## Docs

- `IMPLEMENTATION_PLAN.md` — status of the 8-step delivery plan and the follow-up backlog.
- `REPORT_REQUIREMENTS.md` — original gap analysis driving the build (some items now resolved, see §3.x).
- `docs/verapdf-adapter.md` — design sketch for the veraPDF adapter (now implemented as described).
- `docs/pac-diff-report.md` — row-by-row diff of our JSON output vs PAC's tree on `1.pdf`, with discrepancies categorized and assigned to follow-up steps.
   