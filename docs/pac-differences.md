# PAC.exe vs our implementation — known differences

Snapshot of what we currently report differently from PAC's UI, categorized by type
and root cause. Companion to `docs/pac-diff-report.md`, which was a Step-8-frozen
diff against `1.pdf` only. This file tracks the **live** state and is updated as
gaps close.

**Reference PDFs:** we use PAC-side screenshots the user shares for specific PDFs
(currently `Complex_Presentation_Sample.pdf` for recent verification). Our JSON
values come from `C:\projects\pdf\report\*.report.json`.

## Legend

| Symbol | Meaning |
|---|---|
| ✅ | exact match on counts and status |
| ≈ | status matches, count differs (granularity drift) |
| ⚠️ | status matches, count noticeably off |
| ❌ | status wrong (e.g. we say `PASSED`, PAC says `NOT_APPLICABLE`) |
| 🟦 | structural / schema difference (row depth, nesting) |
| 🚫 | genuinely unimplementable without a new engine (rendering, form runtime, etc.) |

---

## 1. Count granularity mismatches (status matches, number differs)

PAC and our rule agree on the outcome, but they count "one PASSED" against different
things — per page vs per glyph vs per struct element, etc.

| Checkpoint | PAC | Ours | Ratio / delta | Root cause |
|---|---|---|---|---|
| **PDF syntax** | 481 P (Complex sample) | 541 P | +12% | We emit one PASSED per non-stream indirect dictionary (`obj.isDictionary() && !obj.isStream()`). PAC evidently filters further — likely excludes some "internal plumbing" dicts (font descriptors, XRef streams, cross-ref plumbing). Exact filter unknown without PAC source. |
| ~~**Mapping of characters to Unicode**~~ ✅ **F-7b DONE** | ~~2076 P (1.pdf)~~ | ~~2647 P (1.pdf) — was per-text-show~~ Now per-glyph | ✅ | `ValidateUnicodeMapping` now iterates each decoded char of the `PdfString` and emits one finding per character (ERROR if the char is U+FFFD, PASSED otherwise). Corpus total jumped from thousands to ~8M passes (Complex sample: 330 → 25,915). Direction now matches PAC's per-glyph granularity. |
| **Structure Elements per-role counters** (28 checkpoints: `"H1"` through `"WP"` etc.) | one PASSED per counted element, gated by semantic applicability | one PASSED per element visited | WTPDF: −41% (bracketing under PAC), Brantford: +22% (bracketing over) | Native rule fires on every struct element. PAC has opaque per-rule applicability gates we can't reverse-engineer without PAC source. Hybrid model (Step 10) already brackets PAC on both sides — improving requires PAC internals. |
| **Referenced external objects** | 13 P (Complex sample) | 13 P (Complex sample) | ✅ exact | Per-page emission (one PASSED per page with a resolved `Do` operator). |

## 2. Status mismatches

Cases where PAC says one thing and we say another.

| Checkpoint | PAC | Ours | Type | Root cause |
|---|---|---|---|---|
| ~~**veraPDF-owned checkpoints (root gap)**~~ ✅ **F-9 DONE** | `PASSED` with real counts | ~~`NOT_APPLICABLE` (0/0/0) when no failures~~ Now populated with vera PASSED counts | ✅ | Solved via two-pass validation: pass 1 captures failures (`logPassedChecks=false`, unlimited), pass 2 captures passes (`logPassedChecks=true`) and discards its failures to avoid double-counting. Trade-off: vera's ~10K per-doc assertion cap can truncate PASSED counts on very large tagged docs. Corpus totals jumped from ~1K to ~6M PASSED across 53 reports. Doubles per-PDF runtime (~4→9s). |
| **veraPDF WARNING findings** | some rules WARNING (soft "should" clauses) | `ERROR` | ❌ | `VeraRuleMapping.WARNING_RULES` seeds 5 rule IDs (`7.2-6`, `7.2-15`, `7.2-41`, `-42`, `-43`) as WARNING; the rest map to ERROR. PAC's list is broader — no complete public source. Follow-up F-8. |
| **Contrast of text (1.4.3), Non-text contrast (1.4.11), Reflow (1.4.10), Text spacing (1.4.12), Images of text (1.4.5), Content on hover/focus (1.4.13)** | PASSED with per-glyph or per-region counts | `NOT_IMPLEMENTED` | 🚫 | Rendering-required. Needs pixel-level rasterization (PDFBox render + color sampling) — a new dependency. Deferred as option B in the WCAG plan; ask before adding. |
| **Name, Role, Value (4.1.2)**, **Status Messages (4.1.3)** | PASSED or ERROR per form widget | `NOT_IMPLEMENTED` | 🚫 | Form-runtime introspection. Smaller than rendering scope but still ~1 day of AcroForm walker code. |

## 3. Structural differences (schema depth)

Historical model differences that we've now closed. Documented for anyone reading old
`pac-diff-report.md` observations.

| Item | Old state | Current state |
|---|---|---|
| 4-level path (`Content → Optional Content → …`) | Only 3-level, groups couldn't be modeled | ✅ Fixed. `PDFUACheckpoint` carries an optional `group` argument; `ReportBuilder` synthesizes container `CheckpointReportDTO` nodes with nested `checkpoints[]`. |
| WCAG 2.2 tree missing | We only emitted PDF/UA | ✅ Fixed. `reports."WCAG"` now emitted via `WCAGReportBuilder`. |
| `checkpoints[]` vs `groups[]` split | Two arrays on `SubCategoryDTO` | ✅ Unified. Only `checkpoints[]` now, with recursive nesting. |
| Nested container under `"Correctness of language attribute"` (`"Document language metadata contains a syntax error"`) | Missing | ✅ Fixed by adding a group name to `CORRECTNESS_LANGUAGE_ATR`. |

## 4. False emissions (bug-class)

Cases where our rule reports the wrong thing because of a real code defect.

| Checkpoint | Symptom on `1.pdf` (Step 8) | Fixed? | Notes |
|---|---|---|---|
| `PDF syntax` NOT_APPLICABLE when doc is valid | 0/0/0 vs PAC 1079/0/0 | ✅ (this session) | Rule now emits PASSED per indirect dict; count off but status correct. |
| `Logical structure syntax` PASSED per struct element (over-emit) | 2450/0/2128 vs PAC 0/0/0 | ✅ (this session) | Reduced to errors-only. Status now `NOT_APPLICABLE` when tree is clean, matching PAC. |
| `Correctness of language attribute` NOT_APPLICABLE even when doc has no `/Lang` | 0/0/0 vs PAC 1 error | ✅ (this session) | Rule rewritten; was a no-op that built a local `problems` list and threw it away. |
| `Natural language of text objects` findings have no message and no bbox | text object findings were message-null with `bBox: null` | ✅ (this session) | `ContentListener` now computes bbox from `TextRenderInfo`. Message renamed to `"Natural language for text object cannot be determined"`. |
| `Natural language of alternative text` findings have no bbox | bbox null | ✅ (this session) | Rule now reads Layout `/BBox` attribute from struct element (same lookup as `ValidateFigureBoundingBox`). |
| `Font embedding` false positives | 0/0/36 vs PAC 13/0/3 | ✅ (pre-session, per git) | `isEmbedded` correctly checks `FontFile|FontFile2|FontFile3` in current code. |
| `REFERENCED_EXTERNAL_OBJECT` unreachable native rule | rule existed but enum wired `Phase=null` → never invoked | ✅ (this session) | Rule rewritten as a per-page walker for resolved `Do` operators. Matches PAC exactly (13 on Complex sample). |
| `NESTING_LINK_ANNOTATIONS` NOT_APPLICABLE (missing PASSED counts) | 0/0/16 vs PAC 2/0/7 | ✅ done | F-14 native rule emits PASSED per correctly-nested annotation. `7.18.5-2` ("Link must have Contents") is intentionally **unmapped** in `VeraRuleMapping` — the same defect is already reported by `7.18.1-2` → `ALTERNATIVE_DESCRIPTION_FOR_ANNOT`; double-mapping would inflate the alt-description error count. Corpus: Link nesting 1,038 P / 858 E; Alt description 650 P / 736 E — no duplication. |
| `Natural language of text objects` over-strict | 2003/0/644 vs PAC 2076/0/0 | ✅ (this session — F-4) | Root cause: iText's `PdfString.getValue()` returns raw bytes as Latin-1, garbling UTF-16-BE-encoded `/Lang` values (BOM `FE FF` + two-byte chars → "?? E N - U S" for "EN-US"). Fixed by routing every `/Lang` read through a new `LangUtils.pdfStringValue(PdfString)` helper that calls `toUnicodeString()`. Applied to Catalog, page, AcroForm, annot, outline, and struct-elem `/Lang` sites. Corpus impact: false errors dropped 63% (1.4M → 523K); 12 docs flipped `Correctness of language attribute` from FAILED to PASSED. |
| `Natural language of alternative text` inverted (Step 8 state) | 0/0/3 vs PAC 3/0/0 | ✅ | Fixed in Step 9 (`resolveStructElemLang` inheritance chain). |
| `Dynamic XFA form` / `Security settings` emit PASSED when N/A | 1/0/0 vs PAC 0/0/0 | ✅ (this session — F-6) | Verified current corpus: `Dynamic XFA form` = 51 NA + 2 FAILED (the 2 docs that actually contain XFA), `Security settings` = 53 NA (no `/Encrypt` dicts in corpus). Neither rule has a PASSED-when-N/A branch in current source — the defect the Step 8 doc described was fixed before this session. Removed the leftover unused `import static … PASSED` in `DynamicXfaFormIdentifier`. |
| ~~ISO 32005 checks absent (Bounding boxes, Span, Figure extended checks)~~ ✅ F-1 done + F-12 done | 0/0/0 for `Bounding boxes` on 1.pdf vs PAC 0/0/56 | ✅ | F-1 loaded the ISO 32005 profile and wired 1723 rules. F-12 additionally added native geometric containment (walks content stream, unions painted bboxes per MCID, compares to declared Layout `/BBox`). Corpus totals on `Bounding boxes`: 194 P / 1,036 E. |

## 5. Root-cause categorization

| Class | Affected rows | Root cause | Fixable? |
|---|---|---|---|
| ~~**veraPDF PASSED not logged**~~ ✅ **F-9 DONE** | ~~Every checkpoint served solely by vera~~ | ~~`logPassedChecks=false`~~ Now two-pass — first pass captures all failures, second pass captures passes and drops failures. | ✅ Solved. |
| **veraPDF severity dropped to ERROR** | soft ISO "should" clauses (~144 warnings on 1.pdf) | `VeraRuleMapping.WARNING_RULES` seed list has 5 IDs; PAC's is broader. No public canonical list. | Yes — expand seed list by observing more sample-vs-PAC diffs; F-8. |
| ~~**Count granularity: per-event vs per-glyph**~~ ✅ F-7b DONE | ~~`Mapping of characters to Unicode` (+27%)~~ | ~~per `RENDER_TEXT`~~ Now per-char. | ✅ Solved. |
| **Count granularity: per-element vs per-check** | `PDF syntax` (+12%), and older Step 8 over-emit on `Parents of structure elements`, `Structural parent tree`, various font rules | Native rules count per struct-elem/per-dict; PAC counts a filtered subset we can't fully characterize. | Partially — closer alignment possible per rule; exact parity requires PAC source. |
| **Per-rule applicability gates** | 28 per-role Structure Elements counters | PAC has opaque per-rule semantic gates ("emit this row only if the element also has attribute X"). Not visible in the public output. | No — requires PAC source or heavy reverse-engineering. Hybrid model (Step 10) already brackets PAC on both sides. |
| **PDF/UA-2 / ISO 32005 profile missing** | `Bounding boxes` geometric containment; Span structure element deep checks; Figure geometric checks (54 P / 2 W on 1.pdf) | We only load `PDFUA_1`. PAC also loads `PDFUA_2` (ISO 32005). | Yes — F-1 backlog: single `.xml` profile add + extend `VeraRuleMapping` with ISO-32005 rule IDs. |
| **Rule bugs (over-strict)** | ~~`Natural language of text objects` (644 false errors)~~ ✅ F-4 fixed via UTF-16 `/Lang` decoding; ~~`Dynamic XFA form` PASSED-when-NA, `Security settings` PASSED-when-NA~~ ✅ F-6 already resolved | Rule logic read `/Lang` PdfStrings as raw Latin-1 bytes, garbling UTF-16-encoded values. | F-3/F-5 open. |
| **Rendering / colour-sampling missing** | Contrast, Reflow, Non-text contrast, Text spacing, Images of text, Content on hover/focus (7 WCAG leaves) | Static PDF inspection can't derive rendered colour. Needs actual rasterization + pixel sampling. | Yes but expensive — adds PDFBox rendering dependency; W-4 backlog; multi-day scope. Ask before starting. |
| **Form-runtime introspection missing** | 4.1.2 Name/Role/Value, 4.1.3 Status Messages | AcroForm walker over `/AcroForm/Fields`, per-widget role check. | Yes — W-5 backlog, ~1 day. Smaller than rendering. |

## 6. What matches PAC exactly

Included so we don't accidentally regress these.

| Checkpoint | Confirmed on | Value |
|---|---|---|
| `Tagged content and artifacts` | 1.pdf, Step 8 | 3234 P exact |
| `Artifacts inside tagged content` | 1.pdf, Step 8 | 14 P exact |
| `Tagged content inside artifacts` | 1.pdf, Step 8 | 621 P exact |
| `Alternative text for "Figure" structure elements` | 1.pdf, Step 8 | 53 errors exact |
| `Referenced external objects` | Complex sample, this session | 13 P exact |
| `Correctness of language attribute` | Complex sample, this session | 1 error exact (matches PAC's per-doc emission) |
| Report shape and label strings | Every corpus PDF | All 100+ PAC-canonical labels byte-identical |

## 7. Prioritized work to close remaining gaps

Ranked by (impact) / (effort). Highest first.

1. ~~**F-1 — ISO 32005 profile**~~ ✅ **DONE**. `VeraRunner` now runs both PDFUA_1 and PDFUA_2 profiles sequentially. `VeraRuleMapping` builds the 1723-rule PDFUA_2 map at class-init by iterating the profile and routing rules by their `object` type (via a ~90-entry `OBJECT_TO_CHECKPOINT` dict) plus one clause override (`8.2.5.28.2` → `BOUNDED_BOXES` for Figure geometric containment). Sample results on Complex_Presentation_Sample: LI structure elements +13 errors, Note +16 errors, Table +8 errors, Bounding boxes 28 PASSED, First heading level +1 error.
2. ~~**F-9 — vera two-pass PASSED counts**~~ ✅ **DONE**. `VeraRunner.runProfile` now calls `runValidation` twice per profile: pass 1 collects failures with `logPassedChecks=false, maxFailures=-1` (no cap hit); pass 2 collects passes with `logPassedChecks=true` and drops failures to avoid double-counting. Corpus totals: ~1K → ~6M passes across 53 reports. Per-PDF runtime ~doubled (accepted trade-off).
3. ~~**F-6 — Dynamic XFA / Security settings emit PASSED when N/A**~~ ✅ **DONE**. Verified in this session: current source has no PASSED-when-N/A branch — the defect described in Step 8 was fixed by earlier work. Corpus verified: `Dynamic XFA form` = 51 NA / 2 FAILED (real XFA), `Security settings` = 53 NA. Removed one leftover unused import as cleanup.
4. ~~**F-4 — Natural language of text objects false errors**~~ ✅ **DONE**. Root cause was UTF-16-encoded `/Lang` values (BOM `FE FF` prefix) being read as Latin-1 by iText's `PdfString.getValue()`. Fixed by adding `LangUtils.pdfStringValue(PdfString)` that calls `toUnicodeString()`, then routing every `/Lang` read through it (~11 call sites across 8 files). Corpus false errors dropped 63% (1.4M → 523K); 12 docs also flipped `Correctness of language attribute` FAILED→PASSED as a side effect.
5. ~~**F-7b — Mapping of chars to Unicode per-glyph**~~ ✅ **DONE**. `ValidateUnicodeMapping.onShowText` now iterates each decoded char and emits per-glyph findings (U+FFFD → ERROR, else PASSED). Corpus jumped from thousands of passes to ~8M; matches PAC's per-glyph granularity direction.
6. **F-8 — veraPDF WARNING severity expansion**. Ongoing — expand seed list as more sample-vs-PAC diffs surface.
7. ~~**F-14 residual**~~ ✅ **VERIFIED already done**. Inspected the PDFUA-1 profile: `7.18.5-2` and `7.18.1-2` fire on the same defect (Link annotation without `/Contents`). Current code deliberately leaves `7.18.5-2` unmapped and lets `7.18.1-2` → `ALTERNATIVE_DESCRIPTION_FOR_ANNOT` be the single-source-of-truth for that finding. Mapping `7.18.5-2` anywhere would double-count. Corpus tally confirms no duplication: Link nesting 1,038 P / 858 E vs Alt description 650 P / 736 E. The Step-8 doc recommendation was written before this fix; the code comment on `VeraRuleMapping.java:83-85` documents the deliberate choice.
8. ~~**F-13 — Per-cell Table header cell assignments native rule**~~ ✅ **DONE**. New `ValidateTableHeaderCellAssignments` walks the struct tree and emits one PASSED per TH/TD cell. Wired on `TABLE_HEADER_CELL_ASSIGNMENTS` (was vera-only, no PASSED counts). Corpus impact: 75,197 P added across 53 reports; Brantford went 0 P / 246 E → 9,255 P / 246 E; Complex sample 0 → 220 (14 TH + 206 TD). vera continues to drive ERRORs via `7.5-1`/`7.5-2`. Same per-element PASSED-only pattern as F-14.
9. ~~**F-12 — Geometric BBox containment for Figures**~~ ✅ **DONE**. `ValidateFigureBoundingBox` extended with a per-Figure containment check: three-pass — (a) walk struct tree collecting `{Figure, /BBox, /Pg, MCID set}`; (b) for each affected page walk content via `ContentWalker` accumulating the union of painted bboxes per MCID (skipping inside `Artifact`/non-MCID marked-content scopes but preserving parent-MCID attribution); (c) test containment. Degenerate rectangles (zero width or height, common where producers pair `/BBox` with separate `/Width`/`/Height` Layout attributes) skip containment and PASS on syntactic validity alone. Corpus totals: 194 P / 1,036 E. Findings include the declared BBox as their `bBox`. Trade-off: strict containment surfaces real sloppy-producer defects but doesn't reproduce PAC's exact tolerance.
10. **W-5 — Form-runtime 4.1.2 Name/Role/Value**. ~1 day.
11. **W-4 — Rendering-based contrast checks (option B)**. Multi-day + new dependency. **Ask before starting.**

## 8. Fundamental gaps (not fixable in current architecture)

These require either PAC's source or a substantial dependency add.

- **Exact per-rule count parity** for opaque-gated rules (Structure Elements per-role, PDF syntax). We can bracket PAC but not reproduce their internal applicability gates without their source.
- **Rendering-based checks** (contrast, reflow, text spacing). Static structural inspection insufficient; needs a rendering pipeline.
- **Colour-sampling for figures**. Same category as contrast; would piggyback on the same rendering dep.
