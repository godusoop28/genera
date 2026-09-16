package com.c21genera.contracts.infrastructure;

import java.io.IOException;

/**
 * Puerto de conversión DOCX -> PDF (ver AGENTS §79). Se prioriza generar
 * correctamente el DOCX; el PDF es una representación adicional, nunca un
 * documento legal distinto al oficial.
 */
public interface PdfConverter {

  byte[] toPdf(byte[] docxBytes) throws IOException;
}
