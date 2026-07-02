# Accessibility Report — Requirements & Gap Analysis

Reference UI: PAC-style checkpoint tree (Checkpoint | Passed | Warning | Error columns) — see screenshot `Screenshot 2026-06-22 190356.png`.

> **Status (2026-06-23):** Sections §3.1–§3.7 are landed (Steps 2–7 in `IMPLEMENTATION_PLAN.md`). §3.8 (PAC label parity) landed. §3.8a (variable-depth hierarchy for wrapper levels) is the last open item from the original requirements — tracked as **F-10** in the implementation plan backlog. Current PAC parity status per row: `docs/pac-diff-report.md`.

---

## 1. What is implemented today

Pipeline lives in `com.netralabs.Runner` and emits `List<FindingDTO>`. Report layer in `com.netralabs.report`:

- **`PDFUACheckpoint` enum** holds the full PDF/UA checklist (~110 entries) with `category`, `subCategory`, `element`, `errorMessage`. ~46 have rule implementations; ~64 are stubs (`null` factory).
- **`ReportBuilder`** groups findings by `category → subCategory → element`, preserves enum order, rolls statuses upward, and tallies a top-level summary.
- **`ReportWriter`** serializes to pretty-printed JSON next to the input PDF (`<input>.report.json`), overridable via `-o`.
- **Status model** (`CheckpointStatus`): `PASSED`, `FAILED`, `NOT_APPLICABLE`, `NOT_IMPLEMENTED`.
- **Rollup**: a parent is `FAILED` if any child failed, else `PASSED` if any child passed, else `NOT_APPLICABLE`, else `NOT_IMPLEMENTED`.
- **`FindingDTO`** carries `(severity, checkpoint, page, bBox)`; the report only emits `ERROR`/`WARNING` findings into the per-checkpoint `findings` array (passed findings collapse into status).

Current JSON shape per node:
```json
{ "element": "...", "status": "FAILED|PASSED|NOT_APPLICABLE|NOT_IMPLEMENTED",
  "errorMessage": "...", "findings": [{ "severity": "ERROR", "page": 1, "bBox": {...} }] }
```

---

## 2. What the customer wants (from the PAC screenshot)

The UI is a 4-column tree:

| Column     | Meaning                                                                  |
|------------|--------------------------------------------------------------------------|
| Checkpoint | Hierarchical label                                                       |
| Passed     | Count of passed sub-checks at this node (`1079`, `8`, `13`, …)           |
| Warning    | Count of warnings                                                        |
| Error      | Count of errors                                                          |

Per-row visual state:
- **Green check** + counts in *Passed* — node ran and has at least one passing sub-check.
- **Red X** + counts in *Error* — node has at least one failing sub-check; row is expandable into individual error instances (e.g. "Font not embedded" appearing as a leaf twice, one per occurrence).
- **Gray ⊘** + no counts — node did not run / not applicable / skipped (NA and NOT_IMPLEMENTED both render as gray).

Hierarchy depth observed: **5 levels**:
```
PDF/UA                                  (report root)
└── Basic requirements                  (category)
    └── Fonts                           (subCategory)
        └── Font embedding              (checkpoint / element)
            └── Font not embedded       (finding instance — leaf)
                └── page + bbox         (selection metadata for the right pane)
```

Counts roll up: a parent's `Passed` = sum of children's `Passed`, same for `Warning`/`Error`.

Right pane (not part of the JSON, but JSON must support it): when a finding leaf is selected, UI shows page thumbnail with the bbox highlighted. So each finding needs `(page, bBox)`.

---

## 3. Gap analysis — what we need to change

### 3.1 Add pass/warn/error counts at every node ✅ DONE (Step 2)
**Current**: each node has a single `status` enum.
**Needed**: every node (root, category, subCategory, checkpoint) carries `{ passed, warning, error }` counts. Counts roll up from leaves.

Required because: PAC shows totals at every depth (`PDF/UA: 11665/49/178`), and the high passed counts (1079, 8, 13) imply rules currently producing one PASSED-per-page need to start counting per sub-check (per glyph, per tag, per font lookup, etc.).

### 3.2 Promote findings to first-class tree nodes ⚠️ PARTIAL

Findings live as an array on each checkpoint with `message / severity / page / bBox` per entry (added in Steps 2/5/6/7). Rendering them as tree nodes is a UI concern; the JSON shape supports it.
**Current**: `findings` is an array attached to a checkpoint.
**Needed**: each finding renders as its own child row in the tree, with a human-readable label and its own counts (typically `error: 1`).

Suggested JSON shape:
```json
{
  "element": "Font embedding",
  "status": "FAILED",
  "counts": { "passed": 13, "warning": 0, "error": 3 },
  "findings": [
    { "message": "Font not embedded", "severity": "ERROR",
      "page": 5, "bBox": { "top": ..., "left": ..., "width": ..., "height": ... } },
    { "message": "Font not embedded", "severity": "ERROR", "page": 6, "bBox": { ... } }
  ]
}
```

### 3.3 Per-finding message text ✅ DONE (Steps 2 + 6)
**Current**: `FindingDTO` has no message field — only `(severity, checkpoint, page, bBox)`.
**Needed**: each finding carries a short human-readable message (e.g. "Font not embedded", "Font F1 not embedded in page 5"). Two options:
- (a) Use the checkpoint's `errorMessage` for every finding under that checkpoint — simpler, but loses per-instance detail like the font ID.
- (b) Add `message` to `FindingDTO` so rules can record specifics. Recommend (b); the existing logging in `ValidateFontsEmbedding` already constructs these strings — just route them through the finding.

### 3.4 Real page + bbox on findings ✅ DONE (Steps 5 + 7)

95.7% of findings carry a real page; remaining 4.3% are vera-sourced (no geometry) or document-level. BBox plumbed end-to-end for content-rule findings (pipeline verified; emits real user-space rectangles when errors fire).
**Current**: most rules emit `new FindingDTO(ERROR, ..., 0, null)` — page=0, no bbox.
**Needed**: rules must capture the actual location. This is rule-by-rule work, not report-layer work, but the report can't render the right pane without it.

Page is easy (the rule knows what page it's on). BBox requires geometry: for content-stream errors, fetch the painted area; for structure errors, locate via `MCID → PdfPage` mapping (already partially exposed in `ContentWalker`).

### 3.5 Distinguish NOT_APPLICABLE from NOT_IMPLEMENTED ✅ DONE
**Current**: both exist as separate enum values; UI renders both as gray.
**Needed**: keep them separate in JSON (NOT_IMPLEMENTED = stub, NOT_APPLICABLE = ran but had nothing to flag). The UI may choose to merge them visually, but downstream consumers (e.g. coverage dashboards) need the distinction.

### 3.6 Root-level "PDF/UA" node in the tree ✅ DONE (option a)
**Current**: top-level JSON has `report: "PDF/UA"` + `summary` + flat `categories[]`.
**Needed**: the screenshot shows `PDF/UA` itself as a tree row with rolled counts. Either:
- (a) treat the top object as the root node implicitly (no schema change; UI synthesizes it), or
- (b) wrap categories under a `root: { name: "PDF/UA", counts: {...}, children: [...] }` node.

Recommend (a) — the existing `summary` already carries the counts; just have UI present it as the root.

### 3.7 Counts vs. status: keep both ✅ DONE
Counts answer "how many", `status` answers "what color". Keep `status` per node so the UI doesn't have to re-derive it (`error>0 → FAILED`, `passed>0 → PASSED`, etc.) — but the JSON should be internally consistent.

### 3.8a Hierarchy depth beyond category → sub → checkpoint ⛔ OPEN (backlog F-10)

PAC's tree has **more than three levels** in places. The current `PDFUACheckpoint` model carries a single `(category, subCategory, element)` triple, which collapses these intermediate groups. Confirmed from the 1.pdf screenshots:

- **Basic requirements → Content → Optional Content → {Name entry in OCCDs, AS entry in OCCDs}** — "Optional Content" is a wrapper group between sub-category and the two OCCD checkpoints.
- **Logical Structure → Structure Elements → {Headings, Notes, Annotations, Figures, Tables} → checkpoint** — "Structure Elements" wraps five intermediate groups, each containing its own checkpoints.
- Finding leaves under failing checkpoints (e.g. "Font not embedded" × N under "Font embedding") form an additional depth that is currently represented as a flat `findings` array, not as tree nodes — covered separately in §3.2.

**Decision needed**: either
- **(a)** add an optional `group` field to `PDFUACheckpoint` (between `subCategory` and `element`) and have `ReportBuilder` insert an extra tree level when present, or
- **(b)** drop the fixed-triple model and store a `String[] path` per checkpoint that the builder turns into nested nodes of arbitrary depth.

(b) is more flexible and matches PAC's variable-depth structure cleanly. (a) is a smaller diff. Recommend (b) since we already know we'll hit more wrappers as the rest of the checklist is filled in.

### 3.8b Structure and labels must match PAC.exe exactly ✅ DONE (Step 1)

All sub-bullets below were addressed in Step 1 (verbatim labels from the 11-screenshot PAC reference for `1.pdf`, plus the typo fixes). Sub-bullet 4 (per-finding leaf labels) addressed in Step 6 for native rules; veraPDF leaves still carry the verbose ISO spec quotes from each rule's `<description>` — acceptable for now.

The tree shape, node ordering, and **every label string** must be identical to PAC.exe so the two reports can be diffed side-by-side and downstream consumers built against PAC continue to work.

**Required parity, in this order:**
1. **Category / sub-category / checkpoint names** — copy verbatim from PAC, including quoting style and pluralization. The current enum diverges in several places, and several entries have outright bugs:

   | PAC label                                       | Current `element` in enum                          |
   |-------------------------------------------------|----------------------------------------------------|
   | Parents of structure elements                   | Parent of structure elements                       |
   | Structural parent tree                          | Structure parent tree                              |
   | "Registry" entries in Type 0 fonts              | Registry entries in Type 0                         |
   | "Ordering" entries in Type 0 fonts              | Ordering entries in Type 0                         |
   | "Supplement" entries in Type 0 fonts            | Supplement entries in Type 0                       |
   | "CID" to "GID" mapping of Type 2 CID fonts      | CID to GID mapping of Type 2 CID fonts             |
   | "WMode" entry in CMap definition and CMap data  | WMode entry in CMap definition and CMap data       |
   | Font embedding                                  | Fonts embedding                                    |

   **Outright bugs to fix while we're at it:**
   - `ENCODING_ENTRY.element` = `"Encoding entry is non-symbolic TrueType font"` — "is" → "in"
   - `GLYPH_NAMES.element` = `"Glyph names is non-symbolic TrueType font"` — "is" → "in"
   - `F_UF_FILE_SPECIFICATION.element` = wrong string copy-pasted from GLYPH_NAMES; should describe the F/UF file spec checkpoint
   - `TAB_ORDER_PAGES.element` = `"Tab order fro pages with annotations"` — "fro" → "for"
   - `BIBENTRY_STRUCTURE_ELEMENTS.element` = `"'BibEntry' structure elements'"` — stray trailing apostrophe

2. **Ordering** — checkpoints must appear in the same sequence PAC uses. Current `PDFUACheckpoint` enum order is *roughly* aligned but unverified — needs an item-by-item check against a PAC export.

3. **Hierarchy depth and grouping** — sub-category boundaries (e.g. "PDF Syntax (ISO 32000-1)", "Fonts", "Content", "Embedded files", "Natural Language") must match. If PAC nests differently anywhere, our enum must follow.

4. **Finding-instance labels** — the leaf rows (e.g. "Font not embedded") must use PAC's wording, not ours. This applies to the per-finding `message` field added in §3.3.

**How to do it:** export the full PAC checklist (PAC has an HTML/CSV report option) and treat it as the source of truth. Drive a one-shot rewrite of `PDFUACheckpoint` `category`/`subCategory`/`element`/`errorMessage` strings from that export, then keep them locked.

---

## 4. Suggested order of work — superseded

The 4-bullet order below has been replaced by the 8-step delivery in `IMPLEMENTATION_PLAN.md`. All 8 steps landed; see that file for status and follow-up backlog. The high-level remaining work is:

- **Variable-depth hierarchy (§3.8a)** — backlog item F-10.
- **UI consumer** — out of scope; the JSON contract (PAC-shape tree, counts at every node, `findings` with `message/severity/page/bBox`) is stable.
- **PAC parity gaps on counts** — see ranked list in `docs/pac-diff-report.md`.

Original 4-step plan (kept for history):
1. PAC label/structure parity — done (Step 1).
2. Counts + message in report layer — done (Step 2).
3. Rule changes for page/bbox/message — done (Steps 5/6/7).
4. UI consumer — out of scope.
