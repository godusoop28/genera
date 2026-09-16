package com.c21genera.documentprocessing.infrastructure;

import java.util.UUID;

/** Genera claves de almacenamiento no adivinables para artefactos derivados (ver AGENTS §29-30). */
public final class ProcessedStorageKeys {

  private ProcessedStorageKeys() {}

  public static String normalizedPageKey(UUID documentVersionId, int pageNumber) {
    return "processed/%s/normalized/%d-%s.jpg".formatted(documentVersionId, pageNumber, UUID.randomUUID());
  }

  public static String manifestKey(UUID documentVersionId) {
    return "processed/%s/manifest-%s.json".formatted(documentVersionId, UUID.randomUUID());
  }

  public static String pdfKey(UUID documentVersionId) {
    return "processed/%s/document-%s.pdf".formatted(documentVersionId, UUID.randomUUID());
  }
}
