package com.c21genera.expedientes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Vista de solo lectura para consumo de otros módulos (compliance, documents, contracts, ...). */
public record ExpedienteSummary(
    UUID id,
    String folio,
    String ownerDisplayName,
    ExpedienteStatus status,
    PersonType personType,
    SignerCharacter signerCharacter,
    AccreditationType accreditationType,
    boolean condominiumRegime,
    PropertyCaseType propertyCaseType,
    PropertyLegalStatus declaredLegalStatus,
    String propertyAddress,
    UUID createdByUserId,
    List<ParticipantView> participants,
    LegalDetails legalDetails) {

  /** role: OWNER, CO_OWNER, ATTORNEY o LEGAL_REPRESENTATIVE. */
  public record ParticipantView(
      UUID id,
      String role,
      String fullName,
      int ordinal,
      String nationality,
      IdDocumentType idDocumentType,
      String idDocumentNumber,
      String idDocumentIssuer,
      LocalDate birthDate,
      CivilStatus civilStatus,
      MaritalRegime maritalRegime,
      String rfc,
      String curp,
      String email,
      String phone,
      String address) {

    public boolean isOwner() {
      return "OWNER".equals(role) || "CO_OWNER".equals(role);
    }

    public boolean isRepresentative() {
      return "ATTORNEY".equals(role) || "LEGAL_REPRESENTATIVE".equals(role);
    }
  }
}
