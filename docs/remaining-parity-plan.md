# Remaining PAC-parity gaps — fix plan

Status snapshot after the `fix/wcag-parity` branch (9 commits on top of the
`fix/pac-parity-batch` merge):

| PDF | PDF/UA match | WCAG criterion match |
|---|---:|---:|
| all_in_one_pdf | 20/20 (100%) | 4/4 (100%) |
| Complex_Presentation_Sample | 47/51 (92%) | 5/7 (71%) |
| CalSAWS FY 2024-25 Financial Statements | 35/37 (94%) | 3/6 (50%) |
| Filled_Graduate_Financial_Verification_Form_2026-2027 | 46/52 (88%) | 4/7 (57%) |

Every row that still diverges from PAC bucket into one of three cluster:
**1.4.3 Contrast**, **4.1.1 Parsing**, or **1.3.1 Info and Relationships**. This
document lists what we already know, what the residual delta is per PDF, the
hypothesis for each root cause, and a concrete implementation plan we'd try
next. Ordered by impact.

Where a row also appears in the PDF/UA report the fix lives in the PDF/UA
rule; the WCAG number then falls out via `WCAGCriterion` remapping.

---

## Cluster 1 — WCAG 1.4.3 Contrast (Minimum)

### Where the deltas are (updated 2026-07-17)

| PDF | PAC (P/E) | Ours (P/E) | delta |
|---|---:|---:|---|
| OP_AoD Benchmark | 1208 / 38 | 2063 / 136 | **P+855, E+98** |
| Filled_Graduate | 2593 / 392 | 2996 / 364 | **P+403, E-28** (pre-Tj-batch, re-measure) |
| CalSAWS | 11205 / 0 | 11553 / 1 | **P+348, E+1** (pre-Tj-batch, re-measure) |
| Complex_Presentation_Sample | 182 / 146 | 191 / 138 | **P+9, E-8** |
| all_in_one_pdf | N/A | N/A | ✓ exact |

**Per-Tj batching landed** (commit `feat(contrast): batch RENDER_TEXT events
per Tj/TJ operator`). Before the fix, iText's `RENDER_TEXT` per-glyph events
inflated OP_AoD's contrast tally to 8959P/423E (9382 total) vs PAC's 1246.
Wrapping the `Tj/TJ/'/"'` operators via `PdfCanvasProcessor.registerContentOperator`
now collapses those events to one finding per operator (2199 total, ~77%
reduction). Residual over-count is ~76% vs PAC (2199 vs 1246 on OP_AoD).

### Residual root-cause hypotheses after per-Tj batching

**Diagnostic run 2026-07-17 on OP_AoD Benchmark** — instrumented the rule to
bucket emissions by scope (MCID-tagged vs untagged, inside `Do` vs outside):

| Bucket | Count | Errors |
|---|---:|---:|
| Total emissions | 2199 | 136 |
| Tagged (has MCID in canvas hierarchy) | 979 | **0** |
| Untagged (no MCID anywhere) | 1220 | **136** |
| Inside `Do` XObject scope | 0 | — |
| `Do` invocations (with 25 form XObjects invoked) | 25 | — |

Two clear findings:

1. **Form XObject recursion is NOT a source.** iText's default `Do` handler
   for Form XObjects is a no-op unless a custom `IXObjectDoHandler` is
   registered — we don't register one, so nested Tj events never fire.
   Hypothesis 2 (below) is ruled out for this corpus.
2. **All 136 errors are in untagged content.** All 979 tagged emissions
   pass contrast. PAC reports 38 errors + 1208 passes on the same doc;
   since our tagged-only would be 0E/979P (drops all errors PAC classifies),
   PAC clearly includes SOME untagged content. From 979 + X = 1246 →
   X ≈ 267, PAC includes ~22% of our 1220 untagged emissions.

**What we don't know**: which 267 of our 1220 untagged emissions PAC
includes. The untagged bucket contains untyped `/Artifact` scopes, non-MCID
BDCs, and bare content — no aggregate-count signal distinguishes the ones
PAC counts from the ones it drops.

### Screenshot analysis 2026-07-18 — the two error sets are largely disjoint

Customer supplied axesPDF-PAC screenshots of one failing "Text with
insufficient contrast" row on OP_AoD page 3:

- The failing text is **"Tag Category"**, the visible white heading of the
  "Document Coverage Summary" table.
- PDF-XChange Editor confirms the text sits inside a `<TH>` struct element
  (**tagged content**), on a dark-blue table-header rectangle.

That single data point contradicts the untagged-subset story:

- PAC classifies this **tagged** row as failing.
- Our diagnostic showed **all 979 tagged emissions pass** and **all 136 of
  our errors are on untagged emissions**.
- Therefore **PAC's 38 errors and our 136 errors are largely non-overlapping
  populations**: PAC flags tagged text we classify as passing (or don't see
  at this location); we flag untagged text PAC classifies as passing.

Attempted fix 2026-07-18: Tr=3 (invisible-anchor text) inclusion hypothesis.
Removed the `renderMode == 3` early-return in `emitFinding` on the theory
that PAC processes render-mode-3 tagging anchors for contrast while we
skip them. **No change to the counts (still 136E/2063P)** — either the doc
has no Tr=3 events or they're already dropped by another filter (widget
`/Rect`, pure-white-on-white). Reverted.

### Revised diagnosis

Full count parity would require reproducing at least two PAC-specific
quirks simultaneously:

1. **PAC's local-background detection misses the covering rectangle for
   tagged headings** and compares white heading text against page-white
   → 1:1 → fails. On the "Tag Category" case: WCAG contrast of pure-white
   on typical dark blue (~#003366) is ~13:1, well over 4.5:1 — the fact
   that PAC flags it as failing implies its background reader is broken
   for this pattern.
2. **PAC's untagged filter drops ~953 of our 1220 untagged emissions**
   (still unknown criterion; still blocked on PAC's per-event export).

Both changes are regressions from a spec-correct implementation: (1)
introduces a false negative on the background detection to match PAC's
false negative; (2) drops untagged text that WCAG 1.4.3 technically applies
to (only artifacts are formally exempt).

### Residual hypotheses (deprioritised)

1. **Untagged-subset criterion (high, blocked on data).** Still unresolved.
   Would require PAC's per-event export to reverse-engineer. Customer
   provided screenshots of the failing side only; the passing side isn't
   itemised in PAC's UI.
2. **Bbox-union background misclassification (medium).** Per-Tj bbox unions
   catch darker paints per-glyph bboxes wouldn't. Would move some of our
   136 errors → passes, but wouldn't reduce the total count. Not a fix for
   the +855 pass over-count.
3. **Local-background detection difference (new, medium confidence).**
   PAC misses the covering dark rectangle for table headings on tagged
   content; we detect it correctly. Explains why the two error sets differ.
   Reproducing PAC's behavior would require intentionally regressing our
   background lookup — not recommended.
4. **Image-pixel misclassification at bbox centre (low, Complex-only).**
   Single centre-pixel sample under-approximates worst-case for text over
   photos. Complex's 9-event delta is within noise.

### Recommendation

**Accept the current state.** Our rule is more WCAG-spec-correct than PAC's
on this row. The 77% event-count reduction from per-Tj batching (9382 →
2199) is already the biggest structural improvement possible without
reverse-engineering PAC's per-event export. Further parity work is
gated on:

- Getting PAC's per-event "PDF report" export (the passing-side data),
  **or** a written spec of PAC's contrast algorithm from axes4;
- Deciding whether the customer prefers bit-for-bit PAC parity (which
  requires shipping their false-negative background reader) or WCAG-spec
  correctness (which is what we do today).

### Implementation plan (historical — Step 1 & 2 landed 2026-07-17)

**Step 1 — Verify hypothesis with a Tj-operator count probe.** ✅ Done.
Instrumented ValidateContrastOfText via a custom `IContentOperator` wrapper
around Tj/TJ/'/". Confirmed iText fires per-glyph, PAC fires per-Tj.

**Step 2 — Fold per-glyph events into per-Tj events at the source.** ✅ Done.
See commit `fix(contrast): batch RENDER_TEXT events per Tj/TJ operator`.
Result: 9382 → 2199 events on OP_AoD (~77% reduction).

**Step 3 — Multi-pixel image sampling with local worst-case.** Deferred.
Tried 2026-07-10 with 3×3 grid worst-case; fired too many false errors and
was reverted. Only useful for Complex_Presentation_Sample's 9-event delta.

**Step 4 — Per-event ground truth discovery.** Blocked. Requires PAC's "PDF
report" export or a written spec of PAC's algorithm from axes4. Customer
supplied UI screenshots only, which itemise failures but not passes.

---

## Cluster 2 — WCAG 4.1.1 Parsing

### Where the deltas are

| PDF | PAC (P/W/E) | Ours (P/W/E) | delta |
|---|---:|---:|---|
| Complex_Presentation_Sample | 875 / 45 / 0 | 860 / 45 / 0 | **P-15** |
| CalSAWS | 126 / 1 / 0 | 124 / 1 / 0 | **P-2** |
| Filled_Graduate | 438 / 0 / 32 | 439 / 0 / 32 | **P+1** |
| all_in_one_pdf | 14 / 0 / 0 | 14 / 0 / 0 | ✓ exact |

Two distinct root causes:

- **Complex / CalSAWS deltas propagate from the PDF/UA "PDF syntax" row.**
  4.1.1 Parsing includes `PDF_SYNTAX` as its first leaf. The corresponding
  PDF/UA row on Complex is 466 vs PAC 481 (−15); CalSAWS 90 vs 92 (−2). Both
  are the same deferred parity gap documented in
  `memory/project_pdf_syntax_parity_gap.md`.
- **Filled P+1 is Link-nesting granularity.**
  `ValidateAnnotationNesting` emits one PASSED per Link _annotation_ on the
  page /Annots (Filled has 4 Link annots). PAC's WCAG 4.1.1 row shows 3P
  matching the 3 Link _struct elements_ that wrap those annots — one Link
  annotation isn't wrapped by a Link struct so PAC drops its pass but still
  emits the vera error.

### Implementation plan

**Step A — Filled Link-nesting fix (small, high confidence).**
- In `ValidateAnnotationNesting`, change the Link-annotation pass emission
  from "one per page /Annots Link" to "one per struct-tree Link element whose
  `/K` chain reaches an OBJR to a Link annotation".
- The existing `linkWrapsAnnotation(d)` predicate in
  `StructElementByRoleRule.severityFor` already computes this; extract to a
  shared helper on `com.netralabs.logicalstructure.structureelements.StructWalk`
  or copy the logic.
- Risk: PAC's PDF/UA "Nesting of Link" row also currently matches at 4P/1E on
  Filled — that row aggregates from same checkpoint. Verify with a diff that
  changing the emission doesn't regress the PDF/UA row. If the PDF/UA row also
  drops to 3P/1E and PAC's PDF/UA is actually 3P/1E, both align.

**Step B — PDF syntax gap (deferred, memory:`project_pdf_syntax_parity_gap.md`).**
- The current tally (Catalog + Pages root + Page dicts + per-page /Resources
  + per-page /Annots + StructElements) matches all_in_one_pdf (14) and
  Filled_Graduate (221) exactly but under-counts CalSAWS (90 vs 92) and
  Complex (466 vs 481).
- Best next investigation: obtain PAC's "PDF report" export for one file and
  grep for the individual per-object assertions PAC emits. That reveals the
  exact object set PAC counts. Without that data the search is unbounded —
  we tried Fonts, FontDescriptor, Info, intermediate Pages-tree nodes, OCGs
  and none produce all four PDFs' numbers simultaneously.
- Do not chase +15 on Complex by adding buckets that regress the exact-match
  files.

---

## Cluster 3 — WCAG 1.3.1 Info and Relationships

### Where the deltas are

| PDF | PAC (P/W/E) | Ours (P/W/E) | delta |
|---|---:|---:|---|
| Filled_Graduate | 8315 / 9 / 2 | 8318 / 9 / 0 | **P+3, E-2** |
| CalSAWS | 4185 / 27 / 19718 | 4186 / 27 / 19718 | **P+1** |
| Complex_Presentation_Sample | 1538 / 20 / 26 | 1538 / 20 / 26 | ✓ exact |
| all_in_one_pdf | 1 / 0 / 7 | 1 / 0 / 7 | ✓ exact |

### Root cause

- **Filled_Graduate E-2** — the pre-existing PDF/UA fringes:
  `Artifacts inside tagged content` (PAC 48P/1E, ours 49P/0E) and
  `Tagged content inside artifacts` (PAC 111P/1E, ours 112P/0E). Both rows
  have exactly one MCID buried in a Widget appearance stream that our page
  content walker doesn't visit but PAC's does.
- **Filled_Graduate P+3** — small over-counts distributed across the same
  Content sub-checkpoints. Traceable to individual events without a clear
  systematic pattern.
- **CalSAWS P+1** — Font embedding row: PAC 54P/1E, ours 54P/1E (matches),
  but somewhere in the 1.3.1 aggregate one extra pass appears.

### Implementation plan

**Step I — Detect the 1 missing artifact-nesting error via appearance-stream MCID walk.**
- Extend `ValidateArtifactsInsideTagged` and `ValidateTaggedInsideArtifacts` to
  also process Widget-annotation appearance streams (`/AP /N`) when the parent
  is a Widget annotation. Use `PdfCanvasProcessor#processContent(bytes, resources)`
  on the appearance stream form XObject.
- Track a virtual page-level scope: an appearance stream is a Form XObject, so
  BMC/BDC/EMC inside it push nested scope. If a Widget's appearance opens an
  `/Artifact` BMC and inside it contains an MCID BDC, flag as
  "Tagged content inside artifact" error.
- Symmetrically for "Artifacts inside tagged content".
- Risk: iText's `PdfCanvasProcessor#processContent` needs correct
  `PdfCanvasProcessor` setup with the Form XObject's own `/Resources`.
- Expected result: Filled `Tagged content inside artifacts` moves 112P/0E →
  111P/1E (matches PAC); `Artifacts inside tagged content` moves 49P/0E →
  48P/1E (matches PAC).

**Step II — Track down the Filled P+3.**
- After Step I lands, the residual P+3 will be down to a specific set of
  events. Instrument each Content-subcategory rule to emit findings with
  page+MCID+source-tag context; produce a per-finding diff report against
  PAC's PDF-report export.

**Step III — CalSAWS P+1.**
- Diminishing returns — 1 event out of 4186 is noise-level. Investigate only
  if the source can be identified quickly via a per-row breakdown against
  PAC's PDF-report export.

---

## Cross-cutting: get PAC's PDF report export

Multiple steps above depend on **per-event data from PAC**, not just aggregated
counts from screenshots. PAC's UI has a "PDF report" button next to "Results in
detail" that generates a full-text PDF listing every assertion — pass, warning,
and error — with page, rule ID, and object context.

Priority action item: **export the PDF report for all four corpus PDFs, both
the PDF/UA tab and the WCAG tab, and drop them into
`C:/projects/pdf/pac_results/<pdf>/pac-report.pdf`**. Every remaining
diminishing-returns investigation collapses to a text diff once we have that
ground truth.
