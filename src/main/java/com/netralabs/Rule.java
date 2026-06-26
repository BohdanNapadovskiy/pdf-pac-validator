package com.netralabs;

import com.netralabs.basic.content.Context;
import com.netralabs.report.FindingDTO;

import java.util.List;

public interface Rule {
  List<FindingDTO> run(Context ctx);
}
