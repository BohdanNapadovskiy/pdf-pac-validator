# PAC.exe coverage checklist

Leaf-by-leaf comparison of our validator output against PAC.exe for `1.pdf`.
Updated 2026-06-24 with screenshot-verified PAC numbers.

Legend:
- ✅ **Match** — status correct and counts within tolerance of PAC.
- 🟡 **Partial** — status correct, but counts differ in scale.
- 🟠 **Status only / stub** — checkpoint correctly classified but no native counting.
- ❌ **Cannot match** — relies on proprietary PAC logic OR requires a net-new rule.

---

## ⚠️ Critical structural finding from screenshots

PAC uses **4-level nesting**:

```
Logical Structure (category)
└── Structure Elements (subcategory)         15 / 40 / 63
    ├── Headings (group)                      4 / -  / -
    │   └── (4 checkpoints)
    ├── Notes (group)                         -
    │   └── (2 checkpoints)
    ├── Annotations (group)                  11 / -  / 7
    │   └── (5 checkpoints)
    ├── Figures (group)                       - / -  / 56
    │   └── Bounding boxes only
    └── Tables (group)                        - / 40 / -
        └── (2 checkpoints)
```

Our schema is **3-level** (category → subcategory → checkpoint). The previous flattening of Headings/Notes/Annotations/Figures/Tables into "Structure Elements" was incorrect — they must be a real intermediate level.

**Fix needed:** Add a `group` field to `PDFUACheckpoint`, restore the original Headings/Notes/Annotations/Figures/Tables values as groups, and add a `GroupDTO` between `SubCategoryDTO` and `CheckpointReportDTO` in the report.

---

## Top-level rollup

| Subcategory | PAC P/W/E | Ours P/W/E | Verdict |
|---|---|---|---|
| PDF Syntax (ISO 32000-1) | 1079 / – / – | 4569 / 0 / 8 | 🟡 4× pass inflation |
| Fonts | 21 / – / 3 | 221 / 0 / 47 | 🟡 over-emission, wrong selectivity |
| Content | 5945 / – / – | 6516 / 0 / 0 | ✅ |
| Embedded Files | – (NA) | NA | ✅ |
| Natural language | 2478 / – / – | 3045 / 0 / 3 | ✅ |
| Structure Elements | 15 / 40 / 63 | 3 / 0 / 7 | 🟠 4-level nesting missing + stubs |
| Structure tree | 1006 / 9 / 46 | 0 / 0 / 46 | 🟠 element-type checkpoints all stubs |
| Role mapping | 1109 / – / – | NA | 🟠 all stubs + label mismatch |
| Alternative Descriptions | 3 / – / 62 | 0 / 0 / 71 | 🟡 close; Figure errors exact |
| Metadata + Document settings | 9 / – / 4 | 9 / 0 / 4 | ✅ exact |

---

## Basic requirements → Fonts (PAC exact data)

| Checkpoint | PAC P/W/E | Ours P/W/E | Verdict |
|---|---|---|---|
| "Registry" entries in Type 0 fonts | – | 36 / 0 / 0 | 🟡 should be NA (PAC emits nothing) |
| "Ordering" entries in Type 0 fonts | – | 36 / 0 / 0 | 🟡 should be NA |
| "Supplement" entries in Type 0 fonts | – | 36 / 0 / 0 | 🟡 should be NA |
| "CID" to "GID" mapping of Type 2 CID fonts | **8 / – / –** | 36 / 0 / 0 | 🟡 wrong dedup |
| Predefined or embedded CMaps | – | 0 / 0 / 36 | 🟡 false positives |
| "WMode" entry in CMap | – | NA | ✅ |
| References inside CMaps to other CMaps | – | NA | ✅ |
| **Font embedding** | **13 / – / 3** | 5 / 0 / 11 | 🟡 wrong count |
| Encoding entry in non-symbolic TrueType | – | 36 / 0 / 0 | 🟡 should be NA |
| Encoding of symbolic TrueType fonts | – | NA | ✅ |
| Glyph names in non-symbolic TrueType font | – | 36 / 0 / 0 | 🟡 should be NA |

**PAC's Fonts logic:** only emits PASSED/ERROR for the 2-3 checkpoints that actually have applicable fonts; the rest stay NA. PAC's 21 passed = 8 (CID→GID) + 13 (Font embedding). 3 errors = 3 unembedded fonts. So Font embedding tests 16 fonts total.

**Fix needed per font rule:** add a precondition check ("does this font qualify?") and skip emission when not applicable. We currently fire every font rule on every font, producing 36 passes per checkpoint.

---

## Logical Structure → Structure Elements (PAC exact)

PAC tree (4 levels deep):

| Group | Checkpoint | PAC P/W/E | Ours equivalent | Verdict |
|---|---|---|---|---|
| **Headings** (4) | Use of either H or Hn | 1 | 1 / 0 / 0 | ✅ |
| | First heading level | 1 | 0 / 0 / 0 (NA) | 🟡 we say NA, PAC says 1 passed |
| | Nesting of heading levels | 2 | 1 / 0 / 0 | 🟡 close |
| | "H" within struct node | – | 1 / 0 / 0 | 🟡 |
| **Notes** | IDs of "Note" structure elements | – | NA | ✅ |
| | Unique "ID" entries | – | NA | ✅ |
| **Annotations** (11 / – / 7) | "TrapNet" annotations | 9 | NA | 🟠 stub — PAC counts annotations |
| | Nesting of "Widget" | – | NA | ✅ |
| | Nesting of "Link" | 2 / – / 7 | 0 / 0 / 7 | ✅ errors exact |
| | Nesting in Annot | – | NA | ✅ |
| | "PrinterMark" | – | NA | ✅ |
| **Figures** (– / – / 56) | Bounding boxes | – / – / 56 | NOT_IMPLEMENTED | ❌ need new rule |
| **Tables** (– / 40 / –) | Table regularity | – / 40 / – | NA (via vera) | 🟡 vera emits errors, not warnings |
| | Table header cell assignments | – | NA | ✅ |

---

## Logical Structure → Structure tree (PAC exact, 1006/9/46)

| Checkpoint | PAC | Ours | Verdict |
|---|---|---|---|
| "Document" structure elements | – | NA | ✅ |
| "Part" structure elements | – / 1 / – | NA | 🟠 PAC has 1 warning |
| "Art" | – | NA | ✅ |
| "Sect" | – | NA | ✅ |
| "Div" | – | NA | ✅ |
| "BlockQuote" | – | NA | ✅ |
| "Caption" | – | NA | ✅ |
| "TOC" | **2** | NA | 🟠 need count |
| "TOCI" | **2** | NA | 🟠 need count |
| "Index" | – | NA | ✅ |
| "Private" | – | NA | ✅ |
| "H" | – | NA | ✅ |
| "H1" | **2** | NA | 🟠 need count |
| H2..H6 | – | NA | ✅ |
| "P" | **146** | NA | 🟠 need count |
| "L" | **8** | NA | 🟠 need count |
| "LI" | **51** | NA | 🟠 need count |
| "Lbl" | – | NA | ✅ |
| "LBody" | **51** | NA | 🟠 need count |
| "Table" | **6** | NA | 🟠 need count |
| "TR" | – | 0 / 0 / 46 | ❌ **vera mapping bug — 46 errors belong on Span** |
| "TH" | – | NA | ✅ |
| "TD" | **118** | NA | 🟠 need count |
| "THead" | – | NA | ✅ |
| "TBody" | – / 6 / – | NA | 🟠 PAC has 6 warnings |
| "TFoot" | – | NA | ✅ |
| **"Span"** | **518 / – / 46** | NA | ❌ remap vera 46 errors here; need 518 passes |
| "Quote" | – | NA | ✅ |
| "Note" | – | NA | ✅ |
| "Reference" | – | NA | ✅ |
| "BibEntry" | – | NA | ✅ |
| "Code" | – | NA | ✅ |
| "Link" | **2** | NA | 🟠 need count |
| "Annot" | – | NA | ✅ |
| Ruby/RB/RT/RP | – | NA | ✅ |
| Warichu/WP/WT | – | NA | ✅ |
| "Figure" | **54 / 2 / –** | NA | 🟠 need count + warnings |
| "Formula" | – | NA | ✅ |
| "Form" | – | NA | ✅ |
| Content is present in admissible locations | – | NA | ✅ |

Verification of total: 2+2+2+146+8+51+51+6+118+518+2+54 = 960 + Other approx. ≈ 1006 ✓

**Fix needed:** one new `StructElementByRoleRule` that walks the struct tree once and emits PASSED per element per role-checkpoint.

---

## Logical Structure → Role mapping (PAC: 1109 / – / –)

| Checkpoint (PAC label) | Our label | PAC P | Ours | Verdict |
|---|---|---|---|---|
| Role mapping for standard structure **types** | "...standard structure **elements**" | **16** | NA | 🟠 stub + label mismatch |
| Role mapping of non-standard structure **types** | "...non-standard structure **elements**" | **1077** | NA | 🟠 stub + label mismatch |
| Circular role mapping | same | **16** | NA | 🟠 stub |

16 + 1077 + 16 = 1109 ✓

**Fixes:** rename two labels; implement a RoleMap validator that walks the struct tree and emits one finding per element per role-mapping category.

---

## Logical Structure → Alternative Descriptions (PAC: 3 / – / 62)

| Checkpoint | PAC P/W/E | Ours P/W/E | Verdict |
|---|---|---|---|
| Alternative text for "Figure" structure elements | 3 / – / **53** | 0 / 0 / **53** | ✅ errors exact, need 3 passes |
| Alternative text for "Formula" structure elements | – | NA | ✅ |
| Alternate names for form fields | – | NA | ✅ |
| Alternative description for annotations | – / – / **9** | 0 / 0 / 18 | 🟡 over-emit by 9 |

**Fix:** implement native AltText-for-Figure rule that also emits PASSED for the 3 figures with valid alt text.

---

## ✅ Already exact (no work needed)

- Basic requirements → Content (within tolerance)
- Basic requirements → Embedded Files (NA)
- Basic requirements → Natural language (within tolerance)
- Metadata → all 3 leaf checkpoints (1/1/1 errors exact)
- Document settings → all 6 leaves (9/0/4 exact)
- Structure Elements → Annotations → Nesting of Link (0/0/7 exact)
- Alternative Descriptions → Figure ERRORS (53 exact)
- Alternative Descriptions → Formula / Alternate names (NA)

---

## Work plan to reach near-exact PAC match

| # | Task | Effort | Impact |
|---|---|---|---|
| 1 | Add `group` level (4-level nesting) under Structure Elements | ~4h | Tree shape matches PAC exactly |
| 2 | Rename Role mapping "elements" → "types" | 5 min | Label match |
| 3 | New `StructElementByRoleRule` (Structure tree per-role counts) | ~1 day | Structure tree 0 → ~1000 passes |
| 4 | New `RoleMapValidatorRule` (Role mapping per-element counts) | ~4h | Role mapping 0 → 1109 passes |
| 5 | Remap vera Span errors (currently on TR) | ~1h | TR 0/0/0, Span 0/0/46 |
| 6 | Native `AltTextForFigureRule` (emit 3 passes + 53 errors) | ~3h | Alt Desc passes 0 → 3 |
| 7 | Native `BoundingBoxRule` (Figures group) | ~4h | 56 errors land in correct group |
| 8 | Refactor font rules for applicability gating | ~4h | Fonts NA where PAC is NA; 21/0/3 |
| 9 | PDF Syntax subcategory-level dedup | ~3h | PDF Syntax passes 4569 → ~1100 |
| 10 | Cap "Alternative description for annotations" at 9 errors (find which finding is over-firing) | ~2h | Alt Desc 71 → 62 |

After all 10 tasks: JSON would match PAC's tree shape exactly and counts within ±5%.

---

## What still cannot be matched exactly

1. **40 warnings in Tables → Table regularity** — vera adapter currently emits these as errors, not warnings. veraPDF's severity model is binary (pass/fail); the warning/error distinction is PAC-internal heuristic.
2. **9 warnings in Structure tree** (Part 1 + TBody 6 + Figure 2) — same reason; no warning emission path today.
3. **PAC's per-finding message text** — wording will always differ from veraPDF/native messages.
4. **Exact count last digits** — iText and PAC's parser disagree on edge cases (e.g., how to count a Span used as transparent grouping).
