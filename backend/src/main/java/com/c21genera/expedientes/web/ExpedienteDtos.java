package com.c21genera.expedientes.web;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.shared.domain.RequiredDocumentSpec;
import com.c21genera.expedientes.domain.AccreditationType;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ParticipantRole;
import com.c21genera.expedientes.domain.PersonType;
import com.c21genera.expedientes.domain.PropertyCaseType;
import com.c21genera.expedientes.domain.PropertyLegalStatus;
import com.c21genera.expedientes.domain.SignerCharacter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExpedienteDtos {

  private ExpedienteDtos() {}

  public record ParticipantRequest(@NotNull ParticipantRole role, @NotBlank String fullName) {}

  public record CreateExpedienteRequest(
      @NotBlank String ownerDisplayName,
      @NotNull PersonType personType,
      @NotNull SignerCharacter signerCharacter,
      @NotNull AccreditationType accreditationType,
      boolean condominiumRegime,
      @NotNull PropertyCaseType propertyCaseType,
      @NotNull PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      @NotEmpty List<@Valid ParticipantRequest> participants) {}

  public record ParticipantResponse(UUID id, String role, String fullName, int ordinal) {

    public static ParticipantResponse from(ExpedienteParticipant p) {
      return new ParticipantResponse(p.getId(), p.getRole().name(), p.getFullName(), p.getOrdinal());
    }
  }

  public record ExpedienteResponse(
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
      String decisionReason,
      UUID decidedByUserId,
      Instant decidedAt,
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
          e.getAccreditationType(),
          e.isCondominiumRegime(),
          e.getPropertyCaseType(),
          e.getDeclaredLegalStatus(),
          e.getPropertyAddress(),
          e.getDecisionReason(),
          e.getDecidedByUserId(),
          e.getDecidedAt(),
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

  public record RejectPropertyRequest(@NotBlank String reason) {}
}
