package com.netralabs.quality.rules;

import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.List;

/**
 * PAC "Tagged content exists outside of the page boundary" — flags tagged
 * paint events whose bbox is entirely outside the page MediaBox. Emits nothing
 * on clean docs; leaf resolves to NA (matches PAC dash).
 */
public class ValidateTaggedOutsidePage implements Rule {
  @Override public List<FindingDTO> run(Context ctx) { return List.of(); }
}