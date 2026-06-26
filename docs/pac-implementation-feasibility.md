# PAC checkpoint implementation feasibility

For every PAC checkpoint, classifies the implementation path:

- **iText** — can be done with iText directly (PDF object/structure tree walks)
- **veraPDF** — covered by veraPDF's `PDFUA_1` rule profile (106 rules available; 45 currently mapped)
- **iText + veraPDF** — best done combining: veraPDF for the ERROR detection, iText to count PASSED objects (since veraPDF only reports failures)
- **❌ Cannot implement** — requires PAC's proprietary heuristics, visual rendering, or PDF features iText/veraPDF don't expose

Verified against vera profile in `core-jakarta-1.28.2.jar` → `org/verapdf/pdfa/validation/PDFUA-1.xml` (106 rules).

---

## Basic requirements → PDF Syntax (ISO 32000-1)

| Checkpoint | Path | Notes |
|---|---|---|
| PDF syntax | **iText** | Catalog/Pages/MediaBox checks. Done via `CorePdfSyntaxCheck`. |
| Parents of structure elements | **iText** | Walk struct tree, check `/P` references. Done. |
| Logical structure syntax | **iText** | Walk struct tree, validate `/S` + `/K`. Done. veraPDF 7.1-11/7.1-12 also cover. |
| Structural parent tree | **iText** | Walk `/StructParents` and verify ParentTree. Done. |

**All 4 implementable with iText alone. ✅ Already covered.**

---

## Basic requirements → Fonts

| Checkpoint | Path | Notes |
|---|---|---|
| "Registry" entries in Type 0 fonts | **iText + veraPDF (7.21.3.1-1)** | Vera catches violations; iText counts qualifying Type 0 fonts for PASSED. |
| "Ordering" entries in Type 0 fonts | **iText + veraPDF (7.21.3.1-1)** | Same vera rule covers all 3 CIDSystemInfo entries. |
| "Supplement" entries in Type 0 fonts | **iText + veraPDF (7.21.3.1-1)** | Same. |
| "CID" to "GID" mapping of Type 2 CID fonts | **iText + veraPDF (7.21.3.2-1)** | Vera available; iText to count Type 2 CID fonts. |
| Predefined or embedded CMaps | **iText + veraPDF (7.21.3.3-1)** | Vera available; PAC suppresses counts when not applicable. |
| "WMode" entry in CMap | **iText + veraPDF (7.21.3.3-2)** | Vera available. |
| References inside CMaps to other CMaps | **iText + veraPDF (7.21.3.3-3)** | Vera available. |
| **Font embedding** | **iText + veraPDF (7.21.4.1-1)** | Currently done in iText. Need to gate to embeddable types only. |
| Encoding entry in non-symbolic TrueType font | **iText + veraPDF (7.21.6-2)** | Done in iText. |
| Encoding of symbolic TrueType fonts | **iText + veraPDF (7.21.6-3, 7.21.6-4)** | Done in iText. |
| Glyph names in non-symbolic TrueType font | **iText + veraPDF (7.21.6-1)** | Done in iText. |

**All 11 implementable. Gap is selectivity** — PAC only emits counts when checkpoint is *applicable* (e.g. only counts Type 0 fonts in the "Type 0" checkpoints). Refactor needed: gate emission on applicability.

Bonus vera rules we're not using: **7.21.4.2-1/2** (CharSet/CIDSet validation), **7.21.5-1** (glyph widths), **7.21.8-1** (notdef references), **7.21.4.1-2** (referenced glyphs defined). These have no PAC checkpoint we surface — could create new ones if desired.

---

## Basic requirements → Content

| Checkpoint | Path | Notes |
|---|---|---|
| Tagged content and artifacts | **iText** | `ContentWalker` + scope tracking. Done. veraPDF 7.1-3 also covers. |
| Artifacts inside tagged content | **iText** | Done. veraPDF 7.1-1 also covers. |
| Tagged content inside artifacts | **iText** | Done. veraPDF 7.1-2 also covers. |
| Mapping of characters to Unicode | **iText** | Done via `ValidateUnicodeMapping`. veraPDF 7.21.7-1/2 covers font-side. |
| Referenced external objects | **veraPDF (7.20-1)** | Already mapped. |
| Name entry in OCCDs | **iText + veraPDF (7.10-1)** | Done in iText. |
| AS entry in OCCDs | **iText + veraPDF (7.10-2)** | Done in iText. |

**All 7 implementable. ✅ Covered.**

---

## Basic requirements → Embedded Files

| Checkpoint | Path | Notes |
|---|---|---|
| "F" and "UF" entries in file specifications | **iText + veraPDF (7.11-1)** | Done. |

---

## Basic requirements → Natural language

| Checkpoint | Path | Notes |
|---|---|---|
| Correctness of language attribute | **iText + veraPDF (7.2-29)** | Done; BCP-47 validation. |
| Natural language of text objects | **iText + veraPDF (7.2-34)** | Done. |
| Natural language of alternative text | **iText + veraPDF (7.2-22, 7.2-31)** | Done. |
| Natural language of actual text | **iText + veraPDF (7.2-21, 7.2-30)** | Done. |
| Natural language of expansion text | **iText + veraPDF (7.2-23, 7.2-32)** | Done. |
| Natural language of bookmarks | **iText + veraPDF (7.2-2)** | Done. |
| Natural language of "Contents" entries in annotations | **iText + veraPDF (7.2-24)** | Done. |
| Natural language of alternate names of form fields | **iText + veraPDF (7.2-25)** | Done. |

**All 8 implementable. ✅ Covered.**

Bonus vera rule unused: **7.2-33** (XMP language metadata) — could surface as new checkpoint.

---

## Logical Structure → Structure Elements

### → Headings group

| Checkpoint | Path | Notes |
|---|---|---|
| Use of either "H" or "Hn" structure elements | **iText + veraPDF (7.4.4-2, 7.4.4-3)** | Done in iText. |
| First heading level | **iText + veraPDF (7.4.2-1)** | Done in iText. |
| Nesting of heading levels | **iText + veraPDF (7.4.4-1)** | Done in iText. |
| "H" structure elements within a structure node | **iText + veraPDF (7.4.4-1)** | Done in iText. |

**4/4 implementable.** ✅

### → Notes group

| Checkpoint | Path | Notes |
|---|---|---|
| IDs of "Note" structure elements | **iText + veraPDF (7.9-1)** | Done in iText. |
| Unique "ID" entries in Note structure elements | **iText + veraPDF (7.9-2)** | Done in iText. |

**2/2 implementable.** ✅

### → Annotations group

| Checkpoint | Path | Notes |
|---|---|---|
| "TrapNet" annotations | **veraPDF (7.18.2-1)** | Mapped. Need iText for PAC's pass count (9 on `1.pdf`). |
| Nesting of "Widget" annotations | **veraPDF (7.18.4-1)** | Mapped. |
| Nesting of "Link" annotations | **veraPDF (7.18.5-1)** | Mapped — errors match exactly. |
| Nesting of annotations in Annot | **veraPDF (7.18.1-1)** | Mapped. |
| "PrinterMark" annotations | **veraPDF (7.18.8-1)** | Mapped. |

**5/5 vera-covered. ✅** Gap: need iText to count annotations of each subtype to produce PASSED.

### → Figures group

| Checkpoint | Path | Notes |
|---|---|---|
| Bounding boxes | **❌ Cannot implement reliably** | PAC's 56 errors here are visual checks ("figure bounds outside page", "figure overlaps text", "bounding box invalid"). Some derivable from `/BBox` parsing in iText, but most need rendering. **Partial: iText can check /BBox validity**; visual overlap detection cannot. |

### → Tables group

| Checkpoint | Path | Notes |
|---|---|---|
| Table regularity | **veraPDF (7.2-15, 7.2-41, 7.2-42, 7.2-43)** | Mapped to `TABLE_REGULARITY`. PAC reports as WARNINGS — vera emits as ERRORS. **Severity mismatch is structural**: vera has no warning channel. |
| Tables header cell assignments | **iText + veraPDF (7.5-1, 7.5-2)** | Mapped. |

---

## Logical Structure → Structure tree (per-role element counts)

This subcategory is essentially **structural counting**: for each PDF struct role (Document, Part, P, H1..H6, L, LI, Table, etc.), report how many elements exist.

| Checkpoint group | Path | Notes |
|---|---|---|
| All ~46 element-type checkpoints (Document/Part/Art/Sect/Div/P/H/H1..H6/L/LI/Lbl/LBody/Table/TR/TH/TD/THead/TBody/TFoot/Span/Quote/Note/Reference/BibEntry/Code/Link/Annot/Ruby/RB/RT/RP/Warichu/WP/WT/Figure/Formula/Form/Caption/BlockQuote/TOC/TOCI/Index/Private/Content present in admissible locations) | **iText (new)** | Walk struct tree once, emit one PASSED per element per role. No PAC heuristic needed; just counting. |
| Span errors (46 on `1.pdf`) | **veraPDF — need to find which rule** | Currently miscategorized as TR errors via our mapping. Investigate. |
| Part/TBody/Figure warnings | **❌ PAC heuristic** | PAC emits warnings for nuanced issues (e.g. "Part role used without children"). No corresponding vera rule. |

**Implementable: 100% of pass counts via iText.** ❌ Cannot match PAC's 9 warnings in this subcategory (proprietary heuristics).

---

## Logical Structure → Role mapping

| Checkpoint (PAC label) | Path | Notes |
|---|---|---|
| Role mapping for standard structure **types** (16 passes) | **iText + veraPDF (7.1-7)** | Walk struct tree, for each element check RoleMap not remapping standard roles. |
| Role mapping of non-standard structure **types** (1077 passes) | **iText + veraPDF (7.1-5)** | Mapped. Need iText to count elements with non-standard roles to produce passes. |
| Circular role mapping (16 passes) | **iText + veraPDF (7.1-6)** | Mapped. Need iText counting. |

**3/3 implementable** with iText + vera (already mapped; needs counting pass emitter).

---

## Logical Structure → Alternative Descriptions

| Checkpoint | Path | Notes |
|---|---|---|
| Alternative text for "Figure" structure elements | **iText + veraPDF (7.3-1)** | Mapped — error counts match exactly (53). Need iText to count PASSED figures (3). |
| Alternative text for "Formula" structure elements | **iText + veraPDF (7.7-1)** | Mapped. |
| Alternate names for form fields | **iText + veraPDF (7.18.1-3)** | Mapped. |
| Alternative description for annotations | **iText + veraPDF (7.18.1-2, 7.18.5-2)** | Mapped. PAC reports 9 errors; we report 18 — possibly double-mapping from two vera rules. |

**4/4 implementable.**

---

## Metadata and Settings → Metadata

| Checkpoint | Path | Notes |
|---|---|---|
| XMP Metadata | **iText + veraPDF (7.1-8, 5-1)** | Done. |
| PDF/UA identifier | **iText + veraPDF (5-2, 5-3, 5-4, 5-5)** | Done. |
| Title in XMP metadata | **iText + veraPDF (7.1-9)** | Done. |

**3/3 ✅ matches PAC exactly.**

---

## Metadata and Settings → Document settings

| Checkpoint | Path | Notes |
|---|---|---|
| Display of document title in window title | **iText + veraPDF (7.1-10)** | Done. |
| Tag suspects | **iText + veraPDF (7.1-4)** | Done. |
| Mark for tagged documents | **iText + veraPDF (6.2-1)** | Done. |
| Dynamic XFA form | **iText + veraPDF (7.15-1)** | Done. |
| Security settings and document access by AT | **iText + veraPDF (7.16-1)** | Done. |
| Tab order for pages with annotations | **iText + veraPDF (7.18.3-1)** | Done. |

**6/6 ✅ matches PAC exactly (9/0/4).**

---

## Summary table

| Path | # of PAC checkpoints |
|---|---|
| ✅ Already covered (no work) | ~28 |
| **iText + veraPDF, need refactor for selectivity/counting** | ~55 |
| **iText alone (new rule needed — Struct tree counter, Role mapping counter)** | ~3 (covering ~52 stub leaves) |
| ❌ Cannot match without PAC heuristics | **~4** (Figures/Bounding boxes + 9 Structure tree warnings + 40 Tables warnings + finding-message text) |
| ❌ Cannot match without rendering | **~1** (parts of Bounding boxes) |

Net result: ~99% of leaf checkpoints are reachable using iText + veraPDF. The unreachable 1% is the **49 warnings** PAC emits across Tables/Structure tree and **most of the 56 errors in Figures → Bounding boxes** (visual checks).

---

## Concrete plan to maximize coverage

| # | Action | Path | Effort |
|---|---|---|---|
| 1 | Add 4-level group nesting (Group between SubCategory and Checkpoint) | refactor | ~4h |
| 2 | New `StructElementByRoleRule` (single struct-tree walk → emit PASSED per element per role checkpoint) | iText | ~1d |
| 3 | New `RoleMapValidatorRule` (per-element role mapping pass emitter; uses vera errors) | iText + veraPDF | ~4h |
| 4 | Native `AltTextForFigureRule` (PASSED emitter for figures with alt; vera covers errors) | iText + veraPDF | ~3h |
| 5 | Refactor font rules for applicability gating (count only applicable fonts per checkpoint) | iText | ~4h |
| 6 | Map remaining 61 unmapped vera rules → expand VeraRuleMapping | veraPDF | ~2h |
| 7 | Subcategory-level pass dedup in ReportBuilder (PDF Syntax 4569 → 1100) | refactor | ~3h |
| 8 | Investigate Span vs TR error mapping (vera rule ID 7.2-? produces the 46) | debugging | ~2h |
| 9 | Native `BoundingBoxRule` covering /BBox validity (will not match PAC's 56, but a defensible subset) | iText | ~4h |
| 10 | Cap "Alt description for annotations" by deduplicating findings from 7.18.1-2 + 7.18.5-2 | refactor | ~2h |

**Total: ~3 days for near-exact match (excluding inherently impossible 49 warnings).**

---

## What truly cannot be done

1. **40 warnings in Table regularity** — veraPDF treats these as errors; the warning severity is a PAC-only convention.
2. **9 warnings in Structure tree** (Part/TBody/Figure) — these come from PAC's proprietary "best practices" heuristics not standardized in ISO 14289-1.
3. **2 warnings on Figure structure** — same.
4. **Most of Figures → Bounding boxes** (56 errors) — visual overlap and geometric soundness checks. iText can validate `/BBox` *syntax* but cannot reproduce PAC's "figure overlaps text" detection without rendering.
5. **Finding message text** — PAC's wording is its own; vera + native will phrase the same defect differently.
6. **Exact pass-count last digits** — different parsers (iText vs PAC's internal engine) classify edge cases (Span used as grouping vs Span as inline) differently.
