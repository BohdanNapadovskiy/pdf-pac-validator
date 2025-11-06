package com.netralabs;

import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class MultiCanvasListener implements IEventListener {

  private final List<IEventListener> delegates;
  public MultiCanvasListener(List<IEventListener> delegates) { this.delegates = delegates; }

  @Override
  public void eventOccurred(IEventData data, EventType type) {
    for (IEventListener d : delegates) d.eventOccurred(data, type);
  }

  @Override
  public Set<EventType> getSupportedEvents() { return EnumSet.allOf(EventType.class); }

}
