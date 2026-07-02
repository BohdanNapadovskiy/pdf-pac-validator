# Implementation plan

Stepwise plan to deliver the PAC-style accessibility report. Cross-references to `REPORT_REQUIREMENTS.md` (§), `docs/verapdf-adapter.md`, and `docs/pac-diff-report.md`.

**Status: all 8 planned steps landed (as of 2026-06-23), plus a Step 9 session on 2026-06-25 added PAC-style general info, short summary, warning-level severity, the first Figure bounding-box rule, and three language-rule bug fixes. Step 10 on 2026-06-26 applied a hybrid per-element / per-document emission model to the 5 Structure Elements rules. Step 11 on 2026-06-26 ran a dead-code sweep — reduced `Rule` to a single-method interface, dropped 51 dead `phases()` overrides, and closed a fourth NPE bug of the same pattern. Step 12 on 2026-06-28 added PAC's WCAG 2.2 tab as a second output tree (`reports."WCAG"`) by remapping the existing PDF/UA findings — 94 leaves across `Principle → Guideline → Criterion → Leaf`, 51 mapped, 43 stay `NOT_IMPLEMENTED` (out-of-scope / rendering-required / form-runtime). Step 12 also regrouped the JSON shape under a `reports.{document, info, "PDF/UA", "WCAG"}` wrapper. Step 13 on 2026-06-29 deleted the remaining pre-Step-7 listener helpers and one unreachable native rule that Step 11's conservative sweep had left behind. Step 14 on 2026-06-30 through 2026-07-01 unified the JSON `checkpoints[]` model (dropped `GroupDTO`), wrote a live schema tracker + validator script, and burned down eight priority backlog items in one session (W-1, F-1, F-9, F-6, F-4, F-7b, F-14 residual, F-13, F-12). Highlights: veraPDF now runs both PDFUA_1 and PDFUA_2 profiles with a two-pass strategy that captures PASSED counts (~1K → ~6M corpus-wide); UTF-16-encoded `/Lang` values decode correctly (63% drop in `Natural language of text objects` false errors); `Mapping of characters to Unicode` switched to per-glyph emission; Figures now check geometric BBox containment via content-stream walking. The validator produces a PAC-shape JSON report with rolled-up counts, per-finding pages, messages, content-rule bboxes, and veraPDF-backed coverage for stub checkpoints; the WCAG branch reuses every PDF/UA finding without re-running anything.** Remaining work is in the **Follow-up backlog** at the bottom.

---

## Step 1 — PAC label & structure parity (§3.8) ✅ DONE

**Goal:** every category / sub-category / checkpoint string in `PDFUACheckpoint` matches PAC verbatim.

**Outcome:**
- All Basic Requirements / Logical Structure / Metadata category, sub-category and element labels match PAC verbatim (verified against the 11-screenshot PAC reference for `1.pdf`).
- Role-name labels switched from `'X' structure elements` to `"X" structure elements` (47 entries via perl batch transform, then escape-corrected manually).
- Unambiguous typo bugs fixed: `is`→`in` in encoding/glyph rules; `fro`→`for` in TAB_ORDER; stray apostrophe in `BIBENTRY`; copy-pasted `Glyph names` string on `F_UF_FILE_SPECIFICATION` replaced.
- Late fix in Step 8: `PRINTER_MARK_ANNOTATIONS.subCategory` migrated from a stray `"Annotation"` to `"Annotations"` (single missed `replace_all` candidate).

**Known limitation flagged for §3.8a:** PAC uses **4–5 level** trees in places (`Logical Structure → Structure Elements → Headings/Notes/Annotations/Figures/Tables → checkpoint`, and `Content → Optional Content → OCCD entries`). Our current 3-level model can't express these wrapper groups. Documented in `REPORT_REQUIREMENTS.md §3.8a`.

---

## Step 2 — Counts and message field in the report layer (§3.1, §3.3) ✅ DONE

**Outcome:**
- New `CountsDTO { passed, warning, error }`; attached to every level (`ReportDTO`, `CategoryDTO`, `SubCategoryDTO`, `CheckpointReportDTO`).
- `ReportBuilder` tallies per checkpoint and rolls sums to parents.
- `FindingDTO.message` field added with a backwards-compatible 4-arg constructor (no churn on the 129 existing rule callsites).
- `FindingEntryDTO` carries `message`, `severity`, `page`, `bBox` (in that order via `@JsonPropertyOrder`).
- Property order pinned via `@JsonPropertyOrder` so the JSON reads `name → status → counts → children` at every node.

---

## Step 3 — veraPDF adapter bootstrap ✅ DONE

**Outcome:**
- `pom.xml` carries `org.verapdf:validation-model-jakarta:1.28.2` and `org.verapdf:pdf-model:1.28.2` (locally cached jakarta variant).
- New package `com/netralabs/vera`:
  - `VeraValidationResults` — bucket holder; `findingsFor(cp)` and `empty()`.
  - `VeraRunner` — one-shot validator; `VeraGreenfieldFoundryProvider` initialised lazily, results bucketed by `PDFUACheckpoint` via `VeraRuleMapping`.
  - `VeraRuleMapping` — static map of vera rule IDs → checkpoint.
  - `VeraPdfAdapterRule` — `Rule` that drains its bucket from `ctx.veraResults()`.
- `Context` gained a `veraResults` field with overload constructor (defaults to empty).
- `Runner.runAll(pdf, pdfPath)` calls `VeraRunner.validate(pdfPath)` once and threads results through every `Context`. Synthesizes a `VeraPdfAdapterRule` for any checkpoint with `null` factory **or** an empty `Phase` set, when `VeraRuleMapping.covers(cp)` is true.

---

## Step 4 — Populate `VeraRuleMapping` for stub checkpoints ✅ DONE

**Outcome:**
- 45 vera rule IDs mapped to 29 distinct stub checkpoints, extracted from veraPDF's bundled `PDFUA-1.xml` profile.
- Rule ID format: `"ISO 14289-1:2014-<clause>-<test>"` (runtime form, not the XML's `ISO_14289_1` underscored variant — caught via debug log).
- Validator construction set to `(profile, -1, false, true, false)` — unlimited failures per rule, no pass logging (to dodge the 10K assertion cap), error messages on, no progress.
- Headline matches: `Alternative text for "Figure" structure elements` → 53 errors (PAC: 53 exact).
- Late fix in Step 8: `7.18.5-2` migrated from `NESTING_LINK_ANNOTATIONS` to `ALTERNATIVE_DESCRIPTION_FOR_ANNOT`. Brings Link nesting to 7 errors (PAC: 7 exact).

**Known trade-off:** `logPassedChecks=false` means vera-backed checkpoints have no `passed` counts. This explains the ~9K-pass gap at the root vs PAC for vera-owned rows.

---

## Step 5 — Real page on every finding (§3.4 — page only) ✅ DONE

**Outcome:**
- New `StructUtils.pageNumOf(pdf, structElemDict)` helper walks `/Pg` on the struct element or any MCR child; defensive against unindexed page dicts.
- Updated rules: `CorePdfSyntaxCheck`, `ValidateLogicalStructureSyntax`, `ValidateParentsOfStructureElements`, `ValidateStructuralParentTree`, `ValidateFirstHeadingLevel`, `ValidateNestingOfHeadingLevels`, `ValidateHeadingInsideStructureNode`, `ValidateNoteIdPresence`, `ValidateLangOfAltText`.
- `ValidateFontsEmbedding` was already page-aware.
- Coverage: **2854 of 2982 findings** (95.7%) carry a positive `page`. The remaining 4.3% are veraPDF-sourced (no geometry data) or genuinely document-level (metadata, document-wide language, bookmarks, embedded files).

---

## Step 6 — Per-finding messages from native rules (§3.3) ✅ DONE

**Outcome:**
- The 10 rules touched in Step 5 now emit short PAC-style messages (`"Font not embedded"`, `"Structure element role is not standard or mapped"`, `"Heading level skipped"`, `"Alternative text has no /Lang"`, etc.).
- veraPDF messages flow through automatically from `TestAssertion.getMessage()` (verbose ISO-spec quotes — could be post-processed for parity with PAC's terse phrasing).
- `ReportBuilder.toEntry` falls back to `cp.getErrorMessage()` when neither rule nor vera supplies a message.

---

## Step 7 — BBox on content findings (§3.4 — bbox) ✅ DONE

**Outcome:**
- `ContentWalker` rewritten to drive an iText `PdfCanvasProcessor` with a `ScopeAwareListener`. Marked-content BMC/EMC pulses synthesized from canvas-tag-hierarchy diffs between consecutive render events.
- `Hook` interface refactored: `onPainted(BBoxDTO)`, `onShowText(PdfString, BBoxDTO)`; `onShowTextArray` removed.
- Geometry helpers compute bbox in PDF user-space for text (baseline ∪ ascent ∪ descent), images (unit square × CTM), and paths (piecewise-linear subpath approximation).
- 4 content rules refactored from per-page summaries to per-item findings with bbox: `ValidateTaggedCoverage`, `ValidateArtifactsInsideTagged`, `ValidateTaggedInsideArtifacts`, `ValidateUnicodeMapping`.
- Verified pipeline produces real geometry — sampled `top=799.09, left=100.58, width=6.67, height=13.28` for the first glyph on page 1 of 1.pdf.
- PAC count match: `Tagged content and artifacts` 3234 (exact), `Artifacts inside tagged content` 14 (exact), `Tagged content inside artifacts` 621 (exact). `Mapping of chars to Unicode` 2647 vs PAC 2076 (per-text-show vs per-glyph drift).

---

## Step 8 — Diff-against-PAC validation ✅ DONE

**Outcome:**
- Full row-by-row diff in `docs/pac-diff-report.md` against the 11-screenshot PAC reference for `1.pdf` (only PDF with usable reference data).
- Each discrepancy classified by root cause and assigned to a follow-up step.
- Two easy wins landed in-step: the stray "Annotation" sub-category and the Link-vs-Alt-description bucketing.

**Acceptance:** ✓ tree shape and labels match PAC. ⚠️ counts within tolerance on content rules + metadata + alt descriptions; systemic gaps elsewhere documented and ranked.

---

## Step 9 — PAC general info, short summary, warning severity, BBox rule, lang fixes ✅ DONE (2026-06-25)

**Goal:** mirror PAC's report header (general info + 11-row category summary), introduce the third severity tier (WARNING), close the obvious Figures coverage gap, and fix three rule bugs surfaced by running on real test PDFs.

**Outcome — schema additions (`com.netralabs.report`):**
- `DocumentInfoDTO` — title (XMP `dc:title` → Info `/Title` → "(no title)"), filename, language (`LangUtils.docLang`), pages, tags (struct-element count via `StructWalk`), `sizeBytes` + locale-stable `size` string, `compliant` flag (derived from `summary.failed == 0`).
- `DocumentInfoBuilder` — extracts the above; `Locale.ROOT` on `String.format` so the size string never picks up the system locale comma.
- `ShortSummaryEntryDTO` + `ReportDTO.shortSummary` — flat 11-row PAC-style array (one entry per sub-category) with `name`, `status`, `counts`. Built by flattening `categories[].subCategories[]` post-rollup.
- `ReportDTO.@JsonPropertyOrder` updated to `report → document → info → counts → summary → shortSummary → categories`.

**Outcome — warning-severity tier:**
- `CheckpointStatus.WARNING` inserted between `FAILED` and `PASSED`.
- `SummaryDTO.warning` counter added; `summary.total` and `tally()` updated.
- `ReportBuilder.toCheckpointDto` status order: errors > 0 → FAILED, warnings > 0 (no errors) → WARNING, passes > 0 → PASSED, else NA/NI.
- `ReportBuilder.rollup` propagates WARNING (FAILED beats WARNING beats PASSED beats NA beats NI).
- `info.compliant` flips only on `failed`, not on warnings — matches PAC's behavior of keeping warned files compliant.
- `VeraRuleMapping.WARNING_RULES` — explicit set of veraPDF rule IDs that PAC treats as warnings. Seeded with `7.2-6` (TBody container) and `7.2-15/41/42/43` (Table regularity) per `docs/pac-diff-report.md §veraPDF severity wrong`.
- `VeraRunner` consults `VeraRuleMapping.isWarning(ruleId)` when assigning severity to non-PASSED assertions.

**Outcome — new native rule:**
- `ValidateFigureBoundingBox` (`logicalstructure.structureelements.figures`) — walks struct tree, finds Figure elements, checks each carries a Layout attribute owner with a 4-number `/BBox`. Emits PASSED/ERROR per Figure. Wired on `BOUNDED_BOXES` checkpoint (was NOT_IMPLEMENTED). Note: this is the syntactic check only — PAC additionally verifies geometric containment of painted content within the declared BBox (see backlog F-12).

**Outcome — language rule fixes:**
- `ValidateLangOfAnnotationContents` — guarded two NPEs (catalog `/Lang` and annotation `/Lang` both unconditionally dereferenced). Run on WTPDF previously crashed mid-pipeline.
- `ValidateLangOfAltText` — was treating absence of `/Lang` directly on the alt-bearing struct element as an error. PDF/UA-1 §7.2 says `/Lang` is inherited from parent struct elements / document `/Lang`. Switched to `LangUtils.resolveStructElemLang(dict, rootDict, docLang)`. WTPDF: 174 false errors → 0.
- `ValidateLangOfBookmarks` — was hardcoded `String lang = null`, so every bookmark with a title failed by construction. Rewrote to read `/Lang` from the outline dict (`PdfOutline.getContent()`) and fall back to docLang. WTPDF: 183 false errors → 0.

**Outcome — tooling / docs:**
- Added "Manual test fixtures" subsection to `CLAUDE.md` — input PDFs in `C:\projects\pdf\`, JSON reports in `C:\projects\pdf\report\` (folder created).

**Acceptance:** ✓ WTPDF (`Well-Tagged-PDF-WTPDF-1.0.pdf`) now reports `compliant: true`, 0 errors (matches PAC's all-green); ✓ 1.pdf header `info` matches PAC exactly (title `(no title)`, lang `zh-CN`, 7 pages, **1061 tags**, 699 KB); ✓ Brantford run completes end-to-end without crashing.

---

## Step 10 — Hybrid per-element / per-document emission for Structure Elements ✅ DONE (2026-06-26)

**Goal:** close the Structure Elements PASSED-count gap (WTPDF: ours 5 vs PAC 628). Partial application of backlog F-7 (counting granularity).

**Process:**
- Round 1 — converted all 4 Headings rules + `ValidateNoteIdUniqueness` to emit **one PASSED per inspected element**. WTPDF jumped 5 → 738 (PAC 628 → over by 17%). Brantford jumped 4 → 1,406 (PAC 635 → over by 122%). The wildly different overshoot ratios (1.18× WTPDF vs 2.21× Brantford) proved PAC does **not** fire every rule on every heading — it gates each rule by semantic applicability that's opaque without PAC source.
- Round 2 — applied **hybrid policy (Option A)**: classify each rule as per-element or per-document based on what it actually checks.

**Final per-rule emission policy:**

| Rule | Nature | Emission |
|---|---|---|
| `ValidateUseOfEitherHOrHn` | Document-level fact ("does the doc mix H and Hn?") | **1 per document** (reverted) |
| `ValidateFirstHeadingLevel` | Document-level fact ("is the first heading H1?") | **1 per document** (reverted) |
| `ValidateNestingOfHeadingLevels` | Per heading-pair check | 1 per heading visited (per-element) |
| `ValidateHeadingInsideStructureNode` | Per-heading parent-role check | 1 per heading visited (per-element) |
| `ValidateNoteIdUniqueness` | Per-Note ID check | 1 per Note visited (per-element) |

**Outcome:**

| File | Pre-Step-10 | Post-Step-10 | PAC | Delta |
|---|---:|---:|---:|---:|
| WTPDF Structure Elements | 5 | **372** | 628 | -41% |
| Brantford Structure Elements | 4 | **772** | 635 | +22% |

The two test PDFs now **bracket** PAC's count instead of either undercounting (pre-Step-10) or wildly overcounting (Round 1). Closer-to-PAC parity for Structure Elements is now bounded by PAC's opaque per-rule applicability gates and tracked as F-7-partial in the backlog.

**Acceptance:** ✓ WTPDF `compliant: true` unchanged; ✓ Brantford `compliant: false` unchanged (same 6 failing checkpoints + 449 errors as Step 9); ✓ status-rollup logic preserved (Notes status correctly `FAILED` for Brantford because the 107 ERRORS from `ValidateNoteIdPresence` outweigh the 107 PASSED from `ValidateNoteIdUniqueness`).

---

## Step 11 — Dead-code sweep + final NPE guard ✅ DONE (2026-06-26)

**Goal:** delete code that's no longer used after Steps 9–10, simplify the `Rule` interface, and close the last NPE-bug pattern that mirrors the one fixed in Step 9.

**Outcome — `Rule` interface reduced to one method:**
Before:
```java
public interface Rule {
  EnumSet<Phase> phases();
  default boolean supportsRole(PdfName role) { return true; }
  List<FindingDTO> run(Context ctx);
}
```
After:
```java
public interface Rule {
  List<FindingDTO> run(Context ctx);
}
```
- `Rule.phases()` was dead: `Runner` reads `cp.getPhases()` off the enum (the load-bearing path) and threads it through an `Entry` record — it never calls the interface method. Verified by grep — every `.phases()` call in the codebase is on `Entry`, not `Rule`.
- `Rule.supportsRole()` had exactly one override (`CorePdfSyntaxCheck`) that just delegated back to `Rule.super.supportsRole(role)`, and zero callers.

**Outcome — 51-file sweep of dead overrides:**
- Removed the `@Override public EnumSet<Phase> phases() { ... }` block from every rule class (51 files).
- Removed the no-op `supportsRole` override in `CorePdfSyntaxCheck`.
- Cleaned orphaned `import java.util.EnumSet;` and `import com.netralabs.domain.Phase;` from every file where they became unreferenced after the `phases()` removal. Imports preserved in files where they're still used elsewhere (`Runner.java`, `PDFUACheckpoint.java`, `ContentWalker.java`, `MultiCanvasListener.java`).
- Delegated to a general-purpose agent for the mechanical sweep; agent reported `BUILD SUCCESS` across 95 compiled source files.

**Outcome — Tier 1 quick wins:**
- `ValidateLangOfFormFieldAltNames.java` — deleted dead local `String doc = docLang(pdf)` from `run()` (same dead-assignment pattern Step 9 fixed in `ValidateLangOfAnnotationContents`).
- `ValidateFirstHeadingLevel.java` — removed 3 unused imports (`IStructureNode`, `TagStructureContext`, `TagTreeIterator`) left over from earlier iterations.

**Outcome — NPE guards in `ValidateLangOfFormFieldAltNames`:**
- `pdf.getCatalog().getLang().getValue()` → `LangUtils.docLang(pdf)` (null-safe walker). Crashed on every PDF with form fields whose catalog has no `/Lang`.
- `field.getPdfObject().getAsString(PdfName.Lang).getValue()` → guarded for null. Crashed on every form field without a `/Lang` attribute.
- Same defect pattern Step 9 fixed in `ValidateLangOfAnnotationContents` — completes that cleanup.

**Intentionally NOT deleted:**
- The 11 unwired `PDFUACheckpoint` enum constants (TRAP_NET_ANNOTATIONS, NESTING_*, TABLE_REGULARITY, etc.) — they're placeholder slots that surface as `NOT_IMPLEMENTED` rows in the JSON report. Load-bearing for the report schema, not dead.
- `Context.pageNum() / ttp() / runContent()` — LOW-confidence "unused" finds, but they're Lombok-style accessors / framework-callable. Kept.
- The default-package `PDFValidator.java` legacy entry point — explicitly preserved per CLAUDE.md.

**Acceptance:** ✓ `mvn clean compile` BUILD SUCCESS; ✓ WTPDF still `compliant: true` (0 errors, 50 PASSED checkpoints); ✓ Brantford still `compliant: false` with same 449 errors / 6 failed checkpoints. Brantford NotApplicable count went 70 → 69 because the form-field rule now executes cleanly instead of being silently swallowed by an NPE — that's the guard fix landing, not a regression.

---

## Step 12 — WCAG 2.2 view + JSON regrouping ✅ DONE (2026-06-28)

**Goal:** mirror PAC's "WCAG" tab as a second output tree. Reuse every existing `FindingDTO` by remapping checkpoints onto WCAG Success Criteria; do not introduce new validation engines yet.

**Outcome — taxonomy (`com.netralabs.wcag.WCAGCriterion`):**
- 94 leaf constants modelled as `Principle → Guideline → Criterion → Leaf` (PAC-canonical labels, verified byte-for-byte against the 15-screenshot WCAG reference set).
- Each constant declares its source `PDFUACheckpoint`s (often 1, sometimes 0, occasionally N — e.g. the per-tag counters under 4.1.1 each map to one `*_STRUCTURE_ELEMENT` checkpoint).
- `leaf == null` signals "this criterion has no named sub-leaves in PAC" (e.g. 1.2.1, 2.1.1, 4.1.2) — the criterion row itself carries status. Otherwise leaves roll up under the criterion (1.1.1 / 1.3.1 / 2.4.2 / 3.1.2 / 4.1.1 are the multi-leaf criteria).

**Mapping density:**
- **51 leaves mapped** to existing checkpoints. Notably: 1.3.1 Info and Relationships sources 27 leaves (Mark for tagged, Artifacts in/out of tagged, role mappings, table regularity, bounding boxes, Unicode mapping, all font checks); 4.1.1 Parsing sources 43 leaves (PDF syntax + per-tag counters for H1–H6, P, Document/Sect/Caption/LBody/Lbl/TOC/Link/Span/etc., annotation-nesting rules); 2.4.2 Page Titled = XMP metadata × 3; 3.1.2 Language of Parts = 7 language rules.
- **43 leaves stay NOT_IMPLEMENTED**, in three buckets:
  - Out-of-scope for static PDFs (~30 leaves): all of 1.2 Time-based Media, 2.1 Keyboard, 2.2 Enough Time, 2.3.1 Seizures, 2.5 Input Modalities, 3.2 Predictable, 3.3 Input Assistance, plus 1.4.1/1.4.2/2.4.1/2.4.4/2.4.5/2.4.7. PAC also leaves these N/A.
  - Rendering-required (7 leaves): 1.4.3 Contrast of text, 1.4.4 Resize text, 1.4.5 Images of Text, 1.4.10 Reflow, 1.4.11 Non-text Contrast, 1.4.12 Text Spacing, 1.4.13 Hover/Focus. Needs pixel-level analysis.
  - Form-runtime (2 leaves): 4.1.2 Name/Role/Value, 4.1.3 Status Messages.
  - Plus 1.3.2–1.3.5 and 2.4.6 Headings and Labels — partially closable in future work (see W-1, W-2 in backlog).

**Outcome — DTOs (`com.netralabs.report.wcag`):**
- New package: `WCAGReportDTO → PrincipleDTO → GuidelineDTO → CriterionDTO → WCAGLeafDTO`. Each level mirrors the PDF/UA `@JsonPropertyOrder({"name","status","counts",…})` invariant.
- `CriterionDTO` carries both `findings` (used when the criterion is its own leaf) and `leaves` (used when sub-leaves exist).

**Outcome — builder (`WCAGReportBuilder`):**
- Single-pass: groups input `FindingDTO`s by `PDFUACheckpoint`, fans each WCAG leaf to its source checkpoints, applies the same status policy as `ReportBuilder.toCheckpointDto`, rolls counts Leaf → Criterion → Guideline → Principle → root.
- Wired in `PDFValidator` after the existing `ReportBuilder.build(…)` call: `report.getReports().setWcag(WCAGReportBuilder.build(findings))`. No second pass over the PDF; pure remap.

**Outcome — JSON shape regrouping:**
- New `ReportsDTO` wrapper with `@JsonPropertyOrder({"document","info","PDF/UA","WCAG"})` and Jackson `@JsonProperty` annotations for the literal `PDF/UA` and `WCAG` keys.
- New `PdfUaSectionDTO` carries the PDF/UA branch (counts + summary + shortSummary + categories) — extracted out of `ReportDTO`.
- `ReportDTO` slimmed to a single `reports` field. Old top-level keys (`report`, `document`, `info`, `counts`, `summary`, `shortSummary`, `categories`, `wcag`) are gone.

**Acceptance:** ✓ 52/52 corpus PDFs produce well-formed reports. ✓ Tagged sample (AoDTagging Benchmark) flowed real data through WCAG: figure alt = 29, 1.3.1 Info and Relationships rolled up 23,730 events across its 27 leaves, per-tag counters present (H1=1, H2=17, H3=56, Sect=16, Caption=5, Link=48, LBody=50, Lbl=47, TOC=1, TOCI=37, Span=120-class), 3.1.2 language coverage = 8,316, 4.1.1 correctly FAILED on Link-nesting (7) and Widget-nesting (16) and Structural parent tree (6). ✓ Existing PDF/UA tree unchanged (110/44/1/5/60/0); ✓ CLAUDE.md updated with WCAG invariants + architecture section.

---

## Step 13 — Dead-code purge (round 2) ✅ DONE (2026-06-29)

**Goal:** Remove dead code surfaced while investigating WCAG implementation gaps. Step 11 swept once but missed (a) transitive helpers reachable only through other dead code, and (b) one fully-written native rule that was unreachable because of an enum wiring mistake.

**Deleted (6 files):**
- `com/netralabs/ContentRuleListener`, `MultiCanvasListener`, `PageRuleCheck`, `DocumentRuleCheck` — content-handling helpers from the pre-Step-7 era. Superseded by the `Rule` interface + `Hook` callbacks driven by `ContentWalker`. No callers anywhere in the codebase.
- `com/netralabs/metadata/MetadataSettingsChecker` — empty interface, zero implementations and zero references.
- `com/netralabs/basic/content/ValidateReferencedExternalObjects` — 132-line fully-written native rule that was unreachable: the `REFERENCED_EXTERNAL_OBJECT` enum constant declared `Phase=null`, so `Runner` skipped it (precondition is `factory != null && !phases.isEmpty()`). veraPDF rule `7.20-1` already covered the checkpoint, so the deletion is behaviour-neutral.

**Edited (2 files):**
- `domain/PDFUACheckpoint.java` — narrowed `import com.netralabs.basic.content.*` to explicit imports (dropping `ValidateReferencedExternalObjects`); switched `REFERENCED_EXTERNAL_OBJECT` factory from `ValidateReferencedExternalObjects::new, null` → `null, null`. The checkpoint stays vera-only.
- `basic/content/Context.java` — removed `runContent(ContentRuleListener…)` and the now-unused imports (`PdfCanvasProcessor`, `IEventListener`, `ContentRuleListener`, `MultiCanvasListener`, `FindingDTO`, `ArrayList`, `Collections`). It was the sole caller of the deleted listeners; Step 11 had kept it as "framework-callable" but grep proves it has zero call sites.

**Why this was missed before:** Step 11's sweep was conservative about deletions when it couldn't fully prove a public method was unused (Lombok-style accessors, framework callbacks). The `runContent` helper was tagged "LOW-confidence unused — kept" and the four listener types stayed alive solely as parameter / dependency types of that one method. Deleting `runContent` first would have made them obviously dead. `ValidateReferencedExternalObjects` was a different miss: the wiring bug made it look "wired" at the enum level even though Runner skipped it; only by reading the precondition did the dead-code status become clear.

**Acceptance:** ✓ Clean build (`mvn clean package`); ✓ 52/52 corpus PDFs validate end-to-end; ✓ PDF/UA summary on AoD sample unchanged (110 total / 44 passed / 1 warning / 5 failed / 60 NA / 0 not-implemented); ✓ `Referenced external objects` still reports NOT_APPLICABLE / via vera.

---

## Follow-up backlog (ranked by impact-per-effort)

Pulled from `docs/pac-diff-report.md` "Priority fixes to land". All optional from the perspective of "the plan is done"; these close remaining PAC-parity gaps.

### Quick wins (single edit / single file)
- **F-1 — PDF/UA-2 / ISO 32005 profile** — add `org.verapdf.pdfa.validation.PDFUA-2-ISO32005.xml` to `VeraRunner` and extend `VeraRuleMapping` with ISO 32005 rule IDs. Unlocks `"Span" structure elements` (518 P / 46 E), additional `"Figure"` checks (54 P / 2 W). (Step 9 added a native syntactic BBox rule, but ISO 32005 adds the deeper geometric checks — see F-12.)
- **F-2 — `ValidateFontsEmbedding.isEmbedded` bug** — currently returns `true` for any non-null `FontDescriptor`. Restore the commented-out `containsKey(FontFile|FontFile2|FontFile3)` check. Will collapse 36 false errors → ≤3.

### Medium (multi-line, one rule)
- **F-3 — Native `Logical structure syntax` over-strictness** — emits 2128 false errors. Audit `isValidRole` and the `/K` content reference logic.
- **F-4 — Native `Natural language of text objects` over-strictness** — 644 false errors.
- **F-5 — Native `Natural language of alternative text` inverted** — ⚠️ Step 9 fixed the inverted case for WTPDF (`ValidateLangOfAltText` now respects PDF/UA-1 inheritance). Re-verify against `1.pdf` and close if confirmed.
- **F-6 — `Dynamic XFA form` / `Security settings` emit PASSED when NA** — guard the success branch behind a "feature present" check.
- **F-7 — Counting granularity** — ⚠️ Step 10 applied the hybrid (per-element / per-document) model to the 5 Structure Elements rules and now brackets PAC's count (WTPDF -41%, Brantford +22%). Remaining: (a) `Mapping of characters to Unicode` 2647 vs PAC 2076 — switch from per-text-show to per-decoded-glyph emission; (b) per-rule semantic gates to match PAC's idiosyncratic applicability logic (requires PAC source) for true parity on Structure Elements.

### veraPDF parity (touches `VeraRunner` / `VeraRuleMapping`)
- **F-8 — veraPDF WARNING severity heuristic** — ⚠️ Step 9 added the plumbing (`CheckpointStatus.WARNING`, `SummaryDTO.warning`, `VeraRuleMapping.WARNING_RULES`) and seeded 5 rule IDs (`7.2-6`, `7.2-15`, `7.2-41`/`42`/`43`). Brantford still shows 0 warnings vs PAC's 144 in Structure tree → the seed list is incomplete. **Remaining work:** discover the rest of PAC's "should"-clause IDs by running the corpus against PAC and diffing per rule.
- **F-9 — veraPDF PASSED counts** — biggest gap (~9K passes at root). Options: (a) two-pass strategy (one with `logPassedChecks=true` against an empty buffer for counting, one for failures), (b) pre-evaluate the profile's pass predicate directly, (c) accept the gap and document for downstream consumers.

### Model change (cross-cutting)
- **F-10 — Variable-depth path** — replace `(category, subCategory, element)` triple in `PDFUACheckpoint` with `String[] path`. Lets `ReportBuilder` emit "Optional Content" and "Structure Elements" wrapper levels. Documented in `REPORT_REQUIREMENTS.md §3.8a`.

### Cross-PDF validation
- **F-11 — Expand the diff corpus** — `pac-diff-report.md` baselines against `1.pdf` only. Step 9 added WTPDF and Brantford to the manual corpus (`C:\projects\pdf\`) but not yet to the formal diff report. Re-run the comparison there and document deltas; treat persistent mismatches as bugs, idiosyncratic ones as out-of-scope.

### New from Step 9
- **F-12 — Geometric BBox containment for Figures** — Step 9's `ValidateFigureBoundingBox` only checks that `/BBox` exists and is a 4-number rectangle. PAC additionally validates that the painted content under the Figure's MCID range fits within the declared BBox. Implementation: walk content stream, accumulate per-MCID painted bboxes, union them, compare to declared `/BBox`. Reuse `ContentWalker` geometry pipeline from Step 7.
- **F-13 — Per-cell Table regularity** — Brantford emits 246 `Tables header cell assignments` errors via veraPDF (`7.5-1`/`7.5-2`), but PAC's 972 Structure-Elements failures suggest PAC also emits per-row/per-cell findings we don't get. Native rule that walks each Table → TR → (TH|TD) and flags missing `Headers`/`Scope`/`Span` would close the gap.
- **F-14 — Annotation tree nesting rules** — 5 checkpoints under `Structure Elements / Annotations` (TrapNet, Widget/Form, Link/Link, Annot, PrinterMark) all show `NOT_APPLICABLE` because no native rule fires per-annotation. PAC visits each annotation individually. Implement per-annotation walkers that check nesting parent role.
- **F-15 — Tagged-coverage full-page false-positive audit** — `ValidateTaggedCoverage` reports one "Object is not tagged" per page on Brantford (93 errors, one per page, all bbox = full page). Likely caused by page-background paint operations that aren't wrapped in `/Artifact … BMC`. Decide policy: (a) suppress paint events where the bbox equals the page MediaBox (matches PAC's leniency), or (b) keep as-is and document the divergence.

### New from Step 12 (WCAG branch)
- **W-1 — Map 2.4.6 Headings and Labels** — we already run `ValidateUseOfEitherHOrHn`, `ValidateFirstHeadingLevel`, `ValidateNestingOfHeadingLevels`, `ValidateHeadingInsideStructureNode`. Add a new `PDFUACheckpoint` (or reuse the existing heading checkpoints by adding them as additional sources on a new `WCAGCriterion`), point `2.4.6` at them. Pure mapping change, ~30 min, closes one WCAG NOT_IMPLEMENTED.
- **W-2 — Implement 1.3.2 Meaningful Sequence** — derivable from struct tree DFS. PAC's check is "tagged document has a non-empty struct tree with a reading order". Native rule that walks the tree and emits one PASS per page (or FAIL if struct tree is empty / disconnected). ~1–2 hours.
- **W-3 — Map 1.3.5 Identify Input Purpose** — for AcroForm-bearing PDFs, check `/TU` (tooltip) and `/TM` (mapping name) presence on each form field; absent → WARNING. Needs new native rule + form-walker. ~2–3 hours.
- **W-4 — Option B: rendering-based contrast checks** — 1.4.3 Contrast of text, 1.4.10 Reflow, 1.4.11 Non-text Contrast, 1.4.12 Text Spacing, 1.4.5 Images of Text, 1.4.13 Hover/Focus. Requires a real rendering dependency (PDFBox glyph rasterization + color sampling, or iText `PdfDocument` → `Image`). Largest new piece; biggest scope risk. Multi-day. **Ask before adding the dependency.**
- **W-5 — Form-runtime checks** — 4.1.2 Name/Role/Value, 4.1.3 Status Messages. Walk AcroForm widgets, verify each has a usable `/T` (partial name), `/TU` (tooltip), and `/Subtype` mapping to a role assistive tech recognises. Smaller than W-4 but still ~1 day.
- **W-6 — Per-tag counter consolidation** — today the WCAG 4.1.1 per-tag leaves all source from individual `*_STRUCTURE_ELEMENT` checkpoints, which means 28+ separate `StructElementByRoleRule` instances fire from the enum. They each walk the entire struct tree. Consolidate to one rule that emits a `PASSED` per element keyed by role, then dispatch to all 28 checkpoints from one walk. Pure perf win; no behaviour change. ~1 hour.
- **W-7 — Audit "criterion-as-leaf" decisions** — a handful of WCAG criteria are modelled with `leaf == null` (criterion IS the leaf, e.g. 1.3.2, 2.4.6, 3.1.1). If we later add named sub-leaves under them (e.g. 3.1.1 currently sources `CORRECTNESS_LANGUAGE_ATR` — should it also source the document `/Lang` presence check?), revisit the schema to ensure the criterion vs leaf distinction matches PAC.

---

## How to re-run the validator

```bash
mvn -q -DskipTests package
java -cp target/classes:$(cat /tmp/cp.txt) PDFValidator <path-to-pdf> [-o <output.json>]
# default output: <input>.report.json next to the PDF
```

`/tmp/cp.txt` is the dependency classpath produced by `mvn dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt` (regenerate when `pom.xml` changes).
