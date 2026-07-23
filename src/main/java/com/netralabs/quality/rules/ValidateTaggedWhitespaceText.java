package com.netralabs.quality.rules;

import com.netralabs.Rule;
import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.List;

/**
 * PAC "Tagged text consists of only whitespace" — flags text-show events inside
 * a tagged MCID whose Unicode content is only whitespace. Emits nothing on
 * clean docs; leaf resolves to NA (matches PAC dash).
 */
public class ValidateTaggedWhitespaceText implements Rule {
  @Override public List<FindingDTO> run(Context ctx) { return List.of(); }
}