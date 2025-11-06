package com.netralabs.metadata;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;

public class XMPMetaHelper {

  public static XMPMeta tryGetXmpMeta(PdfDocument pdf) {
    try {
      var m = PdfDocument.class.getMethod("getXmpMeta");
      Object r = m.invoke(pdf);
      if (r instanceof XMPMeta x) return x;
    } catch (Exception ignore) {}
    try {
      var m = PdfDocument.class.getMethod("getXmpMetadata");
      Object r = m.invoke(pdf);
      if (r instanceof byte[] b) return XMPMetaFactory.parseFromBuffer(b);
      if (r instanceof XMPMeta x) return x;
    } catch (Exception ignore) {}
    try {
      PdfDictionary cat = pdf.getCatalog().getPdfObject();
      PdfStream md = cat.getAsStream(PdfName.Metadata);
      if (md != null) {
        byte[] xml = md.getBytes(true);
        if (xml != null && xml.length > 0) return XMPMetaFactory.parseFromBuffer(xml);
      }
    } catch (Exception ignore) {}
    return null;
  }


}
