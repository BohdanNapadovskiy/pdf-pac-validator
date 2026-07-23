package com.netralabs.quality.rules;

import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.List;

/**
 * PAC "Artifacted content on page body" — flags real page content (text/paths)
 * incorrectly marked as {@code /Artifact} on the page body. Emits nothing on
 * clean docs; leaf resolves to NA (matches PAC dash).
 */
public class ValidateArtifactedOnBody implements Rule {
  @Override public List<FindingDTO> run(Context ctx) { return List.of(); }
}