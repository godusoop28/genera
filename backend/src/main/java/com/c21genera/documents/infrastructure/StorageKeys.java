package com.c21genera.documents.infrastructure;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.UUID;

/**
 * Genera storage keys internas (ver AGENTS §30-31): nunca se usa el nombre
 * de archivo del cliente ni se confía en su extensión.
 */
public final class StorageKeys {

  private StorageKeys() {}

  public static String categoryOf(DocumentTypeCode type) {
    return switch (type) {
      case INE, PASSPORT, CURP, POWER_OF_ATTORNEY, MARRIAGE_CERTIFICATE -> "identity";
      case TAX_STATUS_CERTIFICATE -> "tax";
      case DEED,
          PROOF_OF_ADDRESS,
          LIEN_CERTIFICATE,
          PROPERTY_TAX,
          CONDOMINIUM_REGIME,
          WATER_RECEIPT,
          ELECTRICITY_RECEIPT,
          CADASTRAL_PLAN,
          RPP_REGISTRATION_SLIP,
          APPRAISAL,
          LAND_USE,
          SUCCESSION,
          ADJUDICATION,
          WILL,
          MORTGAGE,
          LEASE_AGREEMENT -> "property";
      case OTHER -> "misc";
    };
  }

  public static String originalPageKey(
      UUID expedienteId, DocumentTypeCode type, UUID documentId, int versionNumber, int pageNumber, String extension) {
    return "expedientes/%s/%s/%s/v%d/page-%d-%s.%s"
        .formatted(expedienteId, categoryOf(type), documentId, versionNumber, pageNumber, UUID.randomUUID(), extension);
  }

  public static String pdfKey(UUID expedienteId, DocumentTypeCode type, UUID documentId, int versionNumber) {
    return "expedientes/%s/%s/%s/v%d/document.pdf".formatted(expedienteId, categoryOf(type), documentId, versionNumber);
  }

  public static String extensionFor(String mimeType) {
    return switch (mimeType) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "application/pdf" -> "pdf";
      default -> "bin";
    };
  }
}
