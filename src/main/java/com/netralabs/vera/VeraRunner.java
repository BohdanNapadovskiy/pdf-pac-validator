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
    ensureInitialised();
    Map<PDFUACheckpoint, List<FindingDTO>> bucket = new EnumMap<>(PDFUACheckpoint.class);
    try {
      ValidationProfile profile = Profiles.getVeraProfileDirectory()
          .getValidationProfileByFlavour(PDFAFlavour.PDFUA_1);
      try (InputStream in = Files.newInputStream(Paths.get(pdfPath));
           PDFAParser parser = Foundries.defaultInstance().createParser(in);
           PDFAValidator validator = Foundries.defaultInstance().createValidator(profile, -1, false, true, false)) {
        ValidationResult result = validator.validate(parser);
        for (TestAssertion ta : result.getTestAssertions()) {
          String ruleId = formatRuleId(ta.getRuleId());
          PDFUACheckpoint cp = VeraRuleMapping.toCheckpoint(ruleId);
          if (cp == null) continue;
          Severity sev;
          if (ta.getStatus() == TestAssertion.Status.PASSED) sev = Severity.PASSED;
          else if (VeraRuleMapping.isWarning(ruleId)) sev = Severity.WARNING;
          else sev = Severity.ERROR;
          FindingDTO f = new FindingDTO(sev, cp, null, null, ta.getMessage());
          bucket.computeIfAbsent(cp, k -> new ArrayList<>()).add(f);
        }
      }
    } catch (Exception e) {
      log.warn("veraPDF validation failed for {}: {}", pdfPath, e.getMessage());
      return VeraValidationResults.empty();
    }
    return new VeraValidationResults(bucket);
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
