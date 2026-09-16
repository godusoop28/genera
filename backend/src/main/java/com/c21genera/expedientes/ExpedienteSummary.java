package com.c21genera.expedientes;

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
    List<ParticipantView> participants) {

  public record ParticipantView(UUID id, String role, String fullName, int ordinal) {}
}
