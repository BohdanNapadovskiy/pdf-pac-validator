package com.netralabs.vera;

import com.netralabs.domain.PDFUACheckpoint;
import com.netralabs.domain.Severity;
import com.netralabs.report.FindingDTO;
import lombok.extern.slf4j.Slf4j;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;
import org.verapdf.pdfa.validation.profiles.Profiles;
import org.verapdf.pdfa.validation.profiles.RuleId;
import org.verapdf.pdfa.validation.profiles.ValidationProfile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class VeraRunner {

  private static volatile boolean initialised = false;

  private VeraRunner() {}

  public static VeraValidationResults validate(String pdfPath) {
    return validate(pdfPath, true);
  }

  /**
   * @param applyUa2CoreRules when {@code false}, findings whose rule ID is from
   *     {@code ISO 14289-2:2024} are dropped. Use {@code false} when the document
   *     does not declare {@code pdfuaid:part=2} — PAC.exe applies UA-2-specific rules
   *     conditionally on that declaration. ISO 32005:2023 rules are always kept
   *     because PAC applies them universally (e.g. Figure BBox geometric containment).
   */
  public static VeraValidationResults validate(String pdfPath, boolean applyUa2CoreRules) {
    ensureInitialised();
    Map<PDFUACheckpoint, List<FindingDTO>> bucket = new EnumMap<>(PDFUACheckpoint.class);
    runProfile(pdfPath, PDFAFlavour.PDFUA_1, bucket, true);
    runProfile(pdfPath, PDFAFlavour.PDFUA_2, bucket, applyUa2CoreRules);
    return new VeraValidationResults(bucket);
  }

  private static void runProfile(String pdfPath, PDFAFlavour flavour,
      Map<PDFUACheckpoint, List<FindingDTO>> bucket, boolean applyUa2CoreRules) {
    ValidationProfile profile;
    try {
      profile = Profiles.getVeraProfileDirectory().getValidationProfileByFlavour(flavour);
    } catch (Exception e) {
      log.warn("veraPDF {} profile load failed: {}", flavour, e.getMessage());
      return;
    }
    // Pass 1: failures only. logPassedChecks=false + maxFailures=-1 avoids the ~10K
    // total-assertion cap so no failure is dropped.
    runValidation(pdfPath, flavour, profile, false, bucket, /*keepPasses*/ false, applyUa2CoreRules);
    // Pass 2: passes only. logPassedChecks=true — assertions may be truncated at
    // vera's ~10K cap, but every captured PASSED is a net gain (previously zero were
    // captured). Failures from this pass are ignored to avoid double-counting with
    // pass 1. Trade-off: on large tagged docs the cap can drop tail-end PASSED counts.
    runValidation(pdfPath, flavour, profile, true, bucket, /*keepPasses*/ true, applyUa2CoreRules);
  }

  private static void runValidation(String pdfPath, PDFAFlavour flavour,
      ValidationProfile profile, boolean logPassed,
      Map<PDFUACheckpoint, List<FindingDTO>> bucket, boolean keepPasses, boolean applyUa2CoreRules) {
    try (InputStream in = Files.newInputStream(Paths.get(pdfPath));
         PDFAParser parser = Foundries.defaultInstance().createParser(in);
         PDFAValidator validator = Foundries.defaultInstance().createValidator(profile, -1, logPassed, true, false)) {
      ValidationResult result = validator.validate(parser);
      for (TestAssertion ta : result.getTestAssertions()) {
        boolean isPass = ta.getStatus() == TestAssertion.Status.PASSED;
        // Filter: pass 1 wants only failures; pass 2 wants only passes.
        if (keepPasses != isPass) continue;
        String ruleId = formatRuleId(ta.getRuleId());
        if (!applyUa2CoreRules && ruleId.startsWith("ISO 14289-2:2024-")) continue;
        PDFUACheckpoint cp = VeraRuleMapping.toCheckpoint(ruleId);
        if (cp == null) continue;
        Severity sev;
        if (isPass) sev = Severity.PASSED;
        else if (VeraRuleMapping.isWarning(ruleId)) sev = Severity.WARNING;
        else sev = Severity.ERROR;
        FindingDTO f = new FindingDTO(sev, cp, null, null, ta.getMessage());
        bucket.computeIfAbsent(cp, k -> new ArrayList<>()).add(f);
      }
    } catch (Exception e) {
      log.warn("veraPDF {} pass (logPassed={}) failed for {}: {}",
          flavour, logPassed, pdfPath, e.getMessage());
    }
  }

  private static void ensureInitialised() {
    if (!initialised) {
      synchronized (VeraRunner.class) {
        if (!initialised) {
          VeraGreenfieldFoundryProvider.initialise();
          initialised = true;
        }
      }
    }
  }

  private static String formatRuleId(RuleId id) {
    if (id == null) return "";
    return id.getSpecification().getId() + "-" + id.getClause() + "-" + id.getTestNumber();
  }
}
