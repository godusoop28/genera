package com.c21genera.expedientes.web;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.IdDocumentType;
import com.c21genera.expedientes.LegalDetails;
import com.c21genera.expedientes.MaritalRegime;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.expedientes.application.ExpedienteService.ParticipantInput;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteChange;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ParticipantRole;
import com.c21genera.shared.domain.RequiredDocumentSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ExpedienteDtos {

  private ExpedienteDtos() {}

  public record ParticipantRequest(
      @NotNull ParticipantRole role,
      @NotBlank String fullName,
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

    public ParticipantInput toInput() {
      return new ParticipantInput(
          role,
          fullName,
          new ExpedienteParticipant.Details(
              nationality,
              idDocumentType,
              idDocumentNumber,
              idDocumentIssuer,
              birthDate,
              civilStatus,
              maritalRegime,
              rfc,
              curp,
              email,
              phone,
              address));
    }
  }

  /**
   * El nombre que se muestra del expediente y el carácter con el que se
   * firma se derivan de los participantes y del tipo de persona: ya no se
   * capturan por separado (evita contradicciones como "persona moral con
   * estado civil" o "copropietario" con un solo titular).
   */
  public record CreateExpedienteRequest(
      @NotNull PersonType personType,
      boolean signedByAttorney,
      @NotNull AccreditationType accreditationType,
      boolean condominiumRegime,
      @NotNull PropertyCaseType propertyCaseType,
      // Opcionales: se leen de la escritura / certificado de libertad de gravamen / predial.
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      // Para que el cliente reconozca su liga (no es el domicilio legal; ese sale de los documentos).
      @Size(max = 120) String propertyReference,
      @NotEmpty List<@Valid ParticipantRequest> participants,
      LegalDetails legalDetails,
      // Solo si el cliente ya tiene otro expediente en curso y quien crea tiene EXPEDIENT_DUPLICATE_AUTHORIZE.
      @Size(max = 500) String duplicateAuthorizationReason) {}

  public record DuplicateCheckRequest(@NotNull List<@Size(max = 200) String> holderNames) {}

  /** Expediente en curso del mismo cliente; advisorName: quién lo atiende (para coordinarse). */
  public record DuplicateMatchResponse(
      UUID id, String folio, String ownerDisplayName, String matchedName, ExpedienteStatus status, String property, String advisorName, Instant createdAt) {}

  public record CorrectConfigurationRequest(
      @NotNull PersonType personType,
      boolean signedByAttorney,
      @NotNull AccreditationType accreditationType,
      boolean condominiumRegime,
      @NotNull PropertyCaseType propertyCaseType,
      @NotNull PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      String reason) {}

  public record ParticipantChangeRequest(@NotNull @Valid ParticipantRequest participant, String reason) {}

  public record LegalDetailsRequest(@NotNull LegalDetails legalDetails, String reason) {}

  public record ParticipantResponse(
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

    public static ParticipantResponse from(ExpedienteParticipant p) {
      ExpedienteParticipant.Details d = p.details();
      return new ParticipantResponse(
          p.getId(),
          p.getRole().name(),
          p.getFullName(),
          p.getOrdinal(),
          d.nationality(),
          d.idDocumentType(),
          d.idDocumentNumber(),
          d.idDocumentIssuer(),
          d.birthDate(),
          d.civilStatus(),
          d.maritalRegime(),
          d.rfc(),
          d.curp(),
          d.email(),
          d.phone(),
          d.address());
    }
  }

  public record ExpedienteResponse(
      UUID id,
      String folio,
      String ownerDisplayName,
      ExpedienteStatus status,
      PersonType personType,
      SignerCharacter signerCharacter,
      boolean signedByAttorney,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      String propertyReference,
      String decisionReason,
      UUID decidedByUserId,
      Instant decidedAt,
      UUID createdByUserId,
      boolean correctable,
      Instant createdAt,
      Instant updatedAt) {

    public static ExpedienteResponse from(Expediente e) {
      return new ExpedienteResponse(
          e.getId(),
          e.getFolio(),
          e.getOwnerDisplayName(),
          e.getStatus(),
          e.getPersonType(),
          e.getSignerCharacter(),
          e.getSignerCharacter() == SignerCharacter.APODERADO,
          e.getAccreditationType(),
          e.isCondominiumRegime(),
          e.getPropertyCaseType(),
          e.getDeclaredLegalStatus(),
          e.getPropertyAddress(),
          e.getPropertyReference(),
          e.getDecisionReason(),
          e.getDecidedByUserId(),
          e.getDecidedAt(),
          e.getCreatedByUserId(),
          e.isCorrectable(),
          e.getCreatedAt(),
          e.getUpdatedAt());
    }
  }

  public record RequirementResponse(String requirementCode, String type, boolean required, boolean conditional, UUID participantId) {

    public static RequirementResponse from(RequiredDocumentSpec spec) {
      return new RequirementResponse(
          spec.requirementCode(), spec.type().name(), spec.required(), spec.conditional(), spec.participantId());
    }
  }

  public record ChangeResponse(
      UUID id,
      Instant changedAt,
      String actorType,
      String actorName,
      String actorRole,
      String section,
      String field,
      String oldValue,
      String newValue,
      String reason) {

    public static ChangeResponse from(ExpedienteChange c) {
      return new ChangeResponse(
          c.getId(),
          c.getChangedAt(),
          c.getActorType(),
          c.getActorName(),
          c.getActorRole(),
          c.getSection(),
          c.getField(),
          c.getOldValue(),
          c.getNewValue(),
          c.getReason());
    }
  }

  public record RejectPropertyRequest(@NotBlank String reason) {}
}
