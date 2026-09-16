package com.c21genera.extraction.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;
import java.util.Map;

/**
 * Campos que se intentan extraer por tipo de documento (ver AGENTS §38).
 * Lista ilustrativa para el Módulo 1: no pretende ser el catálogo legal
 * definitivo de CENTURY 21 Genera, solo el conjunto mínimo necesario para
 * validar cruces entre documentos (p. ej. nombre completo repetido en varios
 * documentos del mismo participante).
 */
public final class DocumentFieldSchemas {

  private DocumentFieldSchemas() {}

  private static final Map<DocumentTypeCode, List<String>> SCHEMAS =
      Map.of(
          DocumentTypeCode.INE, List.of("fullName", "curp", "electorKey", "address"),
          DocumentTypeCode.PASSPORT, List.of("fullName", "passportNumber", "birthDate"),
          DocumentTypeCode.CURP, List.of("fullName", "curp"),
          DocumentTypeCode.TAX_STATUS_CERTIFICATE, List.of("fullName", "rfc", "taxRegime", "address"),
          DocumentTypeCode.DEED, List.of("ownerFullName", "propertyAddress", "publicRegistryFolio"),
          DocumentTypeCode.PROOF_OF_ADDRESS, List.of("fullName", "address", "issueDate"),
          DocumentTypeCode.LIEN_CERTIFICATE, List.of("propertyAddress", "hasLiens"),
          DocumentTypeCode.PROPERTY_TAX, List.of("ownerFullName", "propertyAddress", "cadastralKey"),
          DocumentTypeCode.POWER_OF_ATTORNEY, List.of("grantorFullName", "attorneyFullName", "notaryNumber"),
          DocumentTypeCode.CONDOMINIUM_REGIME, List.of("propertyAddress", "regimeRegistrationFolio"));

  /** Documentos fuera de esta lista no tienen esquema de extracción todavía: se devuelve vacío. */
  public static List<String> fieldsFor(DocumentTypeCode type) {
    return SCHEMAS.getOrDefault(type, List.of());
  }
}
