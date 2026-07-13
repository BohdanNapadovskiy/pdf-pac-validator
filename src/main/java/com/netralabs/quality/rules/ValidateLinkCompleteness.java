package com.netralabs.quality.rules;

import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.List;

/**
 * PAC "Completeness of Link elements" — flags Link struct elements that are
 * structurally incomplete (no annot ref, no visible content). The exact PAC
 * heuristic is not yet reverse-engineered on the corpus (Complex shows NA);
 * conservative stub returns nothing so the leaf resolves to NA.
 */
public class ValidateLinkCompleteness implements Rule {
  @Override public List<FindingDTO> run(Context ctx) { return List.of(); }
}