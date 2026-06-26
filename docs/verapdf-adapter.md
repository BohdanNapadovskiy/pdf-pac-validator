# veraPDF adapter — design sketch

Goal: let veraPDF's PDF/UA-1 profile produce findings for `PDFUACheckpoint` entries that don't yet have a native iText-based implementation, **without** abandoning the existing `Rule` model. veraPDF runs once per document, the per-checkpoint `Rule` instances pull from a shared bucket of results.

---

## Data flow
дуеі 
```
PDFValidator.main(path)
  │
  ├─► Runner.runAll(pdf, path)
  │     │
  │     ├─► VeraRunner.validate(path)                 ── runs veraPDF ONCE
  │     │     └── returns VeraValidationResults
  │     │           Map<PDFUACheckpoint, List<FindingDTO>>
  │     │
  │     ├─► Stash results in Context (or a sibling)
  │     │
  │     └─► For each PDFUACheckpoint cp:
  │           ├─ native rule? run as today
  │           └─ veraPDF-backed?  VeraPdfAdapterRule(cp).run(ctx)
  │                                  └── drain results[cp]
  │
  └─► ReportBuilder.build(path, findings)              ── unchanged
```

veraPDF is parsed once, iText is parsed once. They don't share state. Each rule still asks one question and returns `List<FindingDTO>`, so `ReportBuilder` keeps working without modification.

---

## Files to add

### `com/netralabs/vera/VeraValidationResults.java`
Thin wrapper around `Map<PDFUACheckpoint, List<FindingDTO>>`. Built once per document, read many times by adapter rules.

```java
public final class VeraValidationResults {
  private final Map<PDFUACheckpoint, List<FindingDTO>> byCheckpoint;

  public VeraValidationResults(Map<PDFUACheckpoint, List<FindingDTO>> m) {
    this.byCheckpoint = m;
  }
  public List<FindingDTO> findingsFor(PDFUACheckpoint cp) {
    return byCheckpoint.getOrDefault(cp, List.of());
  }
  public static VeraValidationResults empty() {
    return new VeraValidationResults(Map.of());
  }
}
```

### `com/netralabs/vera/VeraRunner.java`
Runs veraPDF once on the input file. Translates each `TestAssertion` into a `FindingDTO`, bucketed by the `PDFUACheckpoint` that the rule ID maps to.

```java
public final class VeraRunner {
  static { VeraGreenfieldFoundryProvider.initialise(); }

  public static VeraValidationResults validate(String pdfPath) throws IOException {
    Map<PDFUACheckpoint, List<FindingDTO>> bucket = new EnumMap<>(PDFUACheckpoint.class);
    ValidationProfile profile = Profiles.getVeraProfileDirectory()
        .getValidationProfileByFlavour(PDFAFlavour.PDFUA_1);

    try (InputStream in = Files.newInputStream(Paths.get(pdfPath));
         PDFAParser parser = Foundries.defaultInstance().createParser(in)) {
      PDFAValidator validator = Foundries.defaultInstance()
          .createValidator(profile, /*logPassed*/ true);
      ValidationResult result = validator.validate(parser);

      for (TestAssertion ta : result.getTestAssertions()) {
        String ruleId = formatRuleId(ta.getRuleId());      // e.g. "PDFUA1-7.1-2"
        PDFUACheckpoint cp = VeraRuleMapping.toCheckpoint(ruleId);
        if (cp == null) continue;                          // unmapped rule — skip or log
        Severity sev = (ta.getStatus() == Status.PASSED) ? Severity.PASSED : Severity.ERROR;
        FindingDTO f = new FindingDTO(sev, cp, /*page*/ pageOf(ta), /*bbox*/ null);
        // (optional) attach ta.getMessage() once FindingDTO has a message field — see §3.3
        bucket.computeIfAbsent(cp, k -> new ArrayList<>()).add(f);
      }
    } catch (ModelParsingException | EncryptedPdfException e) {
      // Document is unreadable to veraPDF — treat as no veraPDF findings, log once.
    }
    return new VeraValidationResults(bucket);
  }

  private static String formatRuleId(RuleId id) {
    return id.getSpecification().getId() + "-" + id.getClause() + "-" + id.getTestNumber();
  }
  private static Integer pageOf(TestAssertion ta) { /* parse from ta.getLocation() */ }
}
```

### `com/netralabs/vera/VeraRuleMapping.java`
The labour-intensive piece: the static map from veraPDF rule IDs to our `PDFUACheckpoint`. This is the contract surface — every entry here is a deliberate decision about which engine owns which checkpoint.

```java
public final class VeraRuleMapping {
  private static final Map<String, PDFUACheckpoint> MAP = Map.ofEntries(
    // PDF/UA-1 — examples; full mapping is the work item
    Map.entry("PDFUA1-7.1-1",  PDFUACheckpoint.LOGICAL_STRUCTURE_SYNTAX),
    Map.entry("PDFUA1-7.1-2",  PDFUACheckpoint.PARENTS_OF_STRUCTURE_ELEMENTS),
    Map.entry("PDFUA1-7.21-1", PDFUACheckpoint.FONT_EMBEDDING),
    Map.entry("PDFUA1-7.22-1", PDFUACheckpoint.MAPPING_OF_CHARACTER_TO_UNICODE)
    // ...
  );

  public static PDFUACheckpoint toCheckpoint(String veraRuleId) {
    return MAP.get(veraRuleId);
  }
  private VeraRuleMapping() {}
}
```

Source of truth for rule IDs: `org.verapdf:validation-model` ships the profile XML under `org/verapdf/pdfa/validation/profiles/PDFUA_1.xml` — extract IDs from there, not from memory.

### `com/netralabs/vera/VeraPdfAdapterRule.java`
A `Rule` that, for one assigned `PDFUACheckpoint`, drains the pre-computed findings.

```java
public final class VeraPdfAdapterRule implements Rule {
  private final PDFUACheckpoint cp;

  public VeraPdfAdapterRule(PDFUACheckpoint cp) { this.cp = cp; }

  @Override public EnumSet<Phase> phases() { return EnumSet.of(Phase.DOCUMENT); }

  @Override public List<FindingDTO> run(Context ctx) {
    return ctx.veraResults().findingsFor(cp);
  }
}
```

---

## Glue changes (small, focused)

### `Context`
Add a single field carrying the shared results:
```java
private final VeraValidationResults veraResults;
public VeraValidationResults veraResults() { return veraResults; }
```
Defaulted to `VeraValidationResults.empty()` when veraPDF isn't run.

### `Runner.runAll`
Accept the input path so it can drive veraPDF:
```java
public List<FindingDTO> runAll(PdfDocument pdf, String pdfPath) {
  VeraValidationResults vera = (pdfPath != null)
      ? VeraRunner.validate(pdfPath)
      : VeraValidationResults.empty();
  // ...thread `vera` through every Context constructor below...
}
```

### `PDFUACheckpoint`
For each stub checkpoint we want veraPDF to own, replace `null, null` with `() -> new VeraPdfAdapterRule(SELF), Phase.DOCUMENT`. The self-reference needs a tiny tweak — easiest is a helper:
```java
private static Supplier<Rule> vera(PDFUACheckpoint cp) {
  return () -> new VeraPdfAdapterRule(cp);
}
```
But Java enum constants can't reference themselves at construction time, so the cleanest path is: leave factory `null` in the enum and let `Runner` decide — if the checkpoint has no native factory **and** `VeraRuleMapping` covers it, `Runner` constructs `new VeraPdfAdapterRule(cp)`. That keeps the enum free of veraPDF coupling.

### `PDFValidator.main`
```java
List<FindingDTO> findings = runner.runAll(pdf, path);
```

### `pom.xml`
```xml
<dependency>
  <groupId>org.verapdf</groupId>
  <artifactId>validation-model</artifactId>
  <version>1.26.2</version>
</dependency>
<dependency>
  <groupId>org.verapdf</groupId>
  <artifactId>gf-model</artifactId>
  <version>1.26.2</version>
</dependency>
```
(Both pull in `core` transitively. Pin the version after a quick check on the current veraPDF release.)

---

## Open questions before this is committed

1. **Who owns what?** Need an explicit list of which `PDFUACheckpoint`s switch to veraPDF and which stay native. Default-veraPDF for stubs, default-native for already-implemented, both for a handful where we want a sanity check?
2. **Conflicting findings**: if a checkpoint runs both native and veraPDF, do we merge, prefer one, or warn on disagreement?
3. **veraPDF version**: pin to a known good release; the validation-model XML changes between versions, which can shift rule IDs.
4. **Location data**: veraPDF's `TestAssertion.getLocation()` returns a pointer-style string (e.g. `root/document[0]/pages[2]/...`), not (page, bbox). Mapping this to our `BBoxDTO` is non-trivial and may not be worth it — leave bbox null for veraPDF-sourced findings, populate only `page`.
5. **PDF/UA-2** support: veraPDF added it in recent releases via `PDFAFlavour.PDFUA_2`. If the spec we target is UA-2, swap the flavour and re-do the mapping.
6. **Performance**: veraPDF parses the file independently from iText — both will hold the PDF in memory. Acceptable for CLI use; worth measuring before any service deployment.
7. **License**: veraPDF is dual-licensed (MPL 2.0 / GPL v3+). Confirm MPL terms are acceptable for the consuming product.