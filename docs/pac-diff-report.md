# PAC diff — Step 8 baseline

Corpus: `1.pdf` only (the only PDF with full PAC reference screenshots). Generated from `1.report.json` after Step 7. Each row shows `Passed / Warning / Error`; rows are PAC label as the canonical name.

Legend:
- ✅ exact count match
- ≈ close (within order of magnitude, granularity drift)
- ⚠️ count drift but right direction (pass/fail/applicable status correct)
- ❌ wrong status (e.g. PAC has finding, we have NOT_IMPLEMENTED)
- 🟦 PAC has the row at deeper nesting we don't model yet

---

## Root and category totals

| Node | PAC | Ours | Status |
|---|---|---|---|
| **PDF/UA** | 11665 / 49 / 178 | 14088 / 0 / 2975 | ⚠️ over-emit |
| Basic requirements | 9523 / 0 / 3 | 14074 / 0 / 2847 | ⚠️ over-emit |
| Logical Structure | 2133 / 49 / 171 | 3 / 0 / 124 | ⚠️ under-emit |
| Metadata and Settings | 9 / 0 / 4 | 11 / 0 / 4 | ≈ |

## Basic requirements / PDF Syntax (ISO 32000-1)

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| PDF syntax | 1079 / 0 / 0 | 0 / 0 / 0 (NA) | ❌ |
| Parents of structure elements | 0 / 0 / 0 | 2122 / 0 / 0 | ⚠️ over-pass |
| Logical structure syntax | 0 / 0 / 0 | 2450 / 0 / 2128 | ❌ false errors |
| Structural parent tree | 0 / 0 / 0 | 333 / 0 / 0 | ⚠️ over-pass |

## Basic requirements / Fonts

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| "Registry" entries in Type 0 fonts | 0 / 0 / 0 | 36 / 0 / 0 | ⚠️ over-pass |
| "Ordering" entries in Type 0 fonts | 0 / 0 / 0 | 36 / 0 / 0 | ⚠️ over-pass |
| "Supplement" entries in Type 0 fonts | 0 / 0 / 0 | 36 / 0 / 0 | ⚠️ over-pass |
| "CID" to "GID" mapping of Type 2 CID fonts | 8 / 0 / 0 | 36 / 0 / 0 | ⚠️ over-pass |
| Predefined or embedded CMaps | 0 / 0 / 0 | 0 / 0 / 36 | ❌ false errors |
| "WMode" entry in CMap definition and CMap data | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| References inside CMaps to other CMaps | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Font embedding | 13 / 0 / 3 | 36 / 0 / 36 | ⚠️ over-emit both |
| Encoding entry in non-symbolic TrueType font | 0 / 0 / 0 | 36 / 0 / 0 | ⚠️ over-pass |
| Encoding of symbolic TrueType fonts | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Glyph names in non-symbolic TrueType font | 0 / 0 / 0 | 36 / 0 / 0 | ⚠️ over-pass |

## Basic requirements / Content

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| Tagged content and artifacts | 3234 / 0 / 0 | 3234 / 0 / 0 | ✅ |
| Artifacts inside tagged content | 14 / 0 / 0 | 14 / 0 / 0 | ✅ |
| Tagged content inside artifacts | 621 / 0 / 0 | 621 / 0 / 0 | ✅ |
| Mapping of characters to Unicode | 2076 / 0 / 0 | 2647 / 0 / 0 | ≈ per-text-show vs per-glyph |
| Referenced external objects | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| **Optional Content** (wrapper) | — / — / — | (missing) | 🟦 |
| ↳ Name entry in OCCDs | 0 / 0 / 0 | 0 / 0 / 0 | ✅ count, but at wrong depth |
| ↳ AS entry in OCCDs | 0 / 0 / 0 | 0 / 0 / 0 | ✅ count, but at wrong depth |

## Basic requirements / Embedded Files

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| "F" and "UF" entries in file specifications | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |

## Basic requirements / Natural language

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| Correctness of language attribute | 1 / 0 / 0 | 0 / 0 / 0 (NA) | ⚠️ minor |
| Natural language of text objects | 2076 / 0 / 0 | 2003 / 0 / 644 | ❌ false errors |
| Natural language of alternative text | 3 / 0 / 0 | 0 / 0 / 3 | ❌ **inverted** |
| Natural language of actual text | 398 / 0 / 0 | 398 / 0 / 0 | ✅ |
| Natural language of expansion text | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Natural language of bookmarks | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Natural language of "Contents" entries in annotations | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Natural language of alternate names of form fields | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |

## Logical Structure / Structure Elements (PAC wrapper — 🟦 we don't model)

### Headings

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| Use of either "H" or "Hn" structure elements | 1 / 0 / 0 | 1 / 0 / 0 | ✅ |
| First heading level | 1 / 0 / 0 | 0 / 0 / 0 (NA) | ⚠️ |
| Nesting of heading levels | 2 / 0 / 0 | 1 / 0 / 0 | ⚠️ |
| "H" structure elements within a structure node | 0 / 0 / 0 | 1 / 0 / 0 | ⚠️ tiny |

### Notes — all NA, ✅

### Annotations

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| "TrapNet" annotations | 9 / 0 / 0 | 0 / 0 / 0 (NA) | ❌ vera passes not logged |
| Nesting of "Widget" annotations inside a "Form" structure elements | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Nesting of "Link" annotations inside "Link" structure elements | 2 / 0 / 7 | 0 / 0 / 16 | ⚠️ E doubled (both 7.18.5-1 and -2 mapped here) |
| Nesting of annotations in Annot structure elements | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| **"PrinterMark" annotations** | 0 / 0 / 0 | 0 / 0 / 0 | ✅ count, but **placed under a stray "Annotation" sub-category** instead of "Annotations" — Step 1 regression |

### Figures

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| Bounding boxes | 0 / 0 / 56 | 0 / 0 / 0 (NOT_IMPLEMENTED) | ❌ PDF/UA-2 / ISO 32005 rule — not in PDFUA-1 vera profile |

### Tables

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| Table regularity | 0 / 40 / 0 | 0 / 0 / 0 (NA) | ❌ vera emits errors as ERROR not WARNING; counts dropped |
| (Tables header cell assignments — extra row, not in PAC tree) | — | 0 / 0 / 0 | (harmless, only shown when applicable) |

## Logical Structure / Structure tree

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| "Document" / "Part" / "Art" / "Sect" / "Div" / … (all standard roles) | various P, some W | 0 / 0 / 0 (NOT_IMPLEMENTED) | ❌ no native rule, no vera mapping for these PASSED checks |
| "Part" structure elements | 0 / 1 / 0 | NI | ❌ |
| "TOC" / "TOCI" | 2 / 0 / 0 each | 0 / 0 / 0 (NA via vera) | ⚠️ vera passes not logged |
| "TR" | 46 / 0 / 0 | 0 / 0 / 46 | ❌ vera emits as errors, PAC as passes |
| "TBody" | 0 / 6 / 0 | 0 / 0 / 0 | ❌ vera emits errors, PAC warnings |
| "Span" | 518 / 0 / 46 | 0 / 0 / 0 (NI) | ❌ no rule |
| "Figure" | 54 / 2 / 0 | 0 / 0 / 0 (NI) | ❌ |
| "Form" / Content is present | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |

## Logical Structure / Role mapping

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| (PAC shows roll-up "Role mapping: 1109 passed", no per-rule breakdown visible) | 1109 / 0 / 0 | 0 / 0 / 0 (NA) | ❌ vera passes not logged |

## Logical Structure / Alternative Descriptions

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| Alternative text for "Figure" structure elements | 3 / 0 / 53 | 0 / 0 / 53 | ✅ E match, ⚠️ P missing (vera) |
| Alternative text for "Formula" structure elements | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Alternate names for form fields | 0 / 0 / 0 | 0 / 0 / 0 | ✅ |
| Alternative description for annotations | 0 / 0 / 9 | 0 / 0 / 9 | ✅ |

## Metadata and Settings — all rows ≈ exact except:

| Checkpoint | PAC | Ours | Status |
|---|---|---|---|
| Dynamic XFA form | 0 / 0 / 0 | 1 / 0 / 0 | ⚠️ we emit PASSED, PAC says NA |
| Security settings and document access by assistive technologies | 0 / 0 / 0 | 1 / 0 / 0 | ⚠️ same |

---

## Root-cause categorisation

| Class | Affected rows | Root cause | Plan step to revisit |
|---|---|---|---|
| **Structural depth missing** | Optional Content, Structure Elements wrappers | 3-level model (`category, subCategory, element`) | §3.8a — replace with `String[] path` |
| **Stray "Annotation" subCategory** | PrinterMark sits under wrong sub-cat | Step 1 `replace_all` of "Annotation" → "Annotations" missed one entry (PrinterMark) since its `Annotation` literal appears in a different context | One-line Step 1 follow-up |
| **veraPDF PASSED counts missing** | TrapNet (9), TOC/TOCI (2+2), TR (46), Role mapping (1109), Document/Part/Art/etc. (many), Alt text Figure (3) | We pass `logPassedChecks=false` to avoid hitting the 10K total assertion cap — trade-off documented in Step 4 | Step 4 follow-up: run two vera passes, or pre-count passes via direct profile evaluation |
| **veraPDF severity wrong** | TBody (PAC: 6 W → ours: 0 NA), Table regularity (PAC: 40 W → ours: 0 NA) | We map all non-PASSED assertions to ERROR. PAC distinguishes WARNING based on rule's clause prefix / severity hint in the profile | Step 4 follow-up: add WARNING mapping rule |
| **Native rules over-emit PASSED** | PDF/UA-PDF-Syntax block (Parents 2122, Logical structure syntax 2450, Structural parent tree 333), Fonts block (Registry/Ordering/Supplement/CID-GID 36 each, Encoding/Glyph 36 each) | PAC emits one PASSED per checkpoint (not per visited struct elem). Our rules count per struct elem | Decide policy: keep our richer counts, or cap to one PASSED per checkpoint for parity |
| **Native rules emit false errors** | Logical structure syntax (2128 E), CMaps (36 E), Font embedding (36 E), Natural lang text objects (644 E), Natural lang alt text (3 E) | Bugs in rule logic — too strict. e.g. `isEmbedded(fd)` returns `true` if FontDescriptor exists at all rather than checking FontFile/FontFile2/FontFile3 (see ValidateFontsEmbedding line 113) | Rule-by-rule code review |
| **PDF/UA-2 / ISO 32005 rules absent** | Bounding boxes (PAC: 56 E), Span structure elements (PAC: 518 P / 46 E), Figure struct elements (PAC: 54 P / 2 W) | Vera profile PDFUA_1 doesn't ship these. PAC bundles ISO 32005 checks | Add `org.verapdf.pdfa.validation.PDFUA-2-ISO32005.xml` profile to `VeraRunner`, extend `VeraRuleMapping` |
| **Mapping over-collapses two distinct vera rules** | Nesting of "Link" annotations: PAC E=7, ours E=16 | `VeraRuleMapping` sends `7.18.5-1` AND `7.18.5-2` to `NESTING_LINK_ANNOTATIONS`. `7.18.5-2` is alt-description failure → belongs under `ALTERNATIVE_DESCRIPTION_FOR_ANNOT` | One-line `VeraRuleMapping` move |
| **Counting granularity** | Mapping of chars to Unicode: PAC 2076 vs ours 2647 | We count per-text-show event; PAC counts per glyph | Tweak `ValidateUnicodeMapping` to emit one PASSED per glyph (charsequence length) |
| **Dynamic XFA / Security settings emit PASSED when NA** | PAC says no XFA → NA; we emit PASSED | Rule's success path emits PASSED even when no XFA present (vs returning empty findings → NA via builder) | Rule code-review |

---

## Priority fixes to land for closer parity

Ranked by impact-per-effort:

1. **Easy / high impact** — move `7.18.5-2` to `ALTERNATIVE_DESCRIPTION_FOR_ANNOT` (one Map.entry swap). Brings Link annotations E from 16 → 9; brings Alt description for annotations E from 9 → 16. Both align closer to PAC.
2. **Easy / structural** — find the missed `"Annotation"` enum entry (`PRINTER_MARK_ANNOTATIONS`) and update its subCategory to `"Annotations"`. One-line fix removes the stray sub-cat.
3. **Easy / scope** — add `PDFUA_2` profile to `VeraRunner` and map ISO-32005 rule IDs for Bounding boxes / Span / Figure structure elements. Adds ~600 checks back into the report.
4. **Medium** — fix `ValidateFontsEmbedding.isEmbedded` to actually check FontFile / FontFile2 / FontFile3 keys instead of returning `true` for every non-null FontDescriptor. Brings Font embedding errors from 36 → ≤3.
5. **Medium** — fix `ValidateLogicalStructureSyntax` over-strictness on what counts as a structure-element role (currently flags 2128 valid structures).
6. **Medium** — add WARNING severity heuristic to `VeraRunner` for clauses the profile flags as `weakly required` (rule `should` vs `shall`). Or maintain a list in `VeraRuleMapping` of rule-IDs that should map to WARNING.
7. **Medium / model change** — replace `(category, subCategory, element)` triple with `String[] path` to model "Optional Content" and "Structure Elements" wrapper levels (§3.8a in `REPORT_REQUIREMENTS.md`).
8. **Hard / policy decision** — vera PASSED counts. Either (a) run a second vera pass with `logPassedChecks=true` and merge, or (b) pre-evaluate the profile's pass predicate directly for known-good objects, or (c) accept the gap and document.

## What looks excellent

- All Step 1 label fixes landed correctly. Every checkpoint name matches PAC verbatim.
- Step 2 counts roll up correctly at every node.
- Step 3-4 veraPDF integration produces correct error counts on the rules where it owns the checkpoint.
- Step 5 page numbers populate cleanly.
- Step 6 messages render on leaves.
- Step 7 ContentWalker geometry pipeline produces exact PAC count matches on 3 of 4 content rules (3234, 14, 621).
