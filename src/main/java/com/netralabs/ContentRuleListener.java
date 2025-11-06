package com.netralabs;

import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import com.netralabs.report.FindingDTO;

import java.util.List;

public interface ContentRuleListener extends IEventListener {

  List<FindingDTO> drain();

}
