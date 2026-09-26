package com.c21genera.contracts.web;

import com.c21genera.contracts.application.ContractService.Readiness;
import com.c21genera.contracts.application.ContractSignatureService.IssuedSigningLink;
import com.c21genera.contracts.domain.ContractCalculator;
import com.c21genera.contracts.domain.ContractGeneration;
import com.c21genera.contracts.domain.ContractGenerationStatus;
import com.c21genera.contracts.domain.ContractSignature;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContractDtos {

  private ContractDtos() {}

  /** method: EMAIL, IN_PERSON, WHATSAPP u otro texto breve. */
  public record DeliverRequest(@NotBlank String method) {}

  public record SignatureResponse(
      UUID id,
      String party,
      UUID participantId,
      String signerName,
      String signerCapacity,
      boolean hasEmail,
      String status,
      String method,
      Instant requestedAt,
      Instant signedAt,
      Instant linkExpiresAt,
      String documentSha256,
      String ipAddress,
      String typedName,
      String evidenceSha256,
      UUID registeredByUserId,
      String voidedReason) {

    public static SignatureResponse from(ContractSignature s) {
      return new SignatureResponse(
          s.getId(),
          s.getParty().name(),
          s.getParticipantId(),
          s.getSignerName(),
          s.getSignerCapacity(),
          s.getSignerEmail() != null && !s.getSignerEmail().isBlank(),
          s.getStatus().name(),
          s.getMethod() == null ? null : s.getMethod().name(),
          s.getRequestedAt(),
          s.getSignedAt(),
          s.getStatus() == ContractSignature.Status.PENDING ? s.getTokenExpiresAt() : null,
          s.getDocumentSha256(),
          s.getIpAddress(),
          s.getTypedName(),
          s.getEvidenceSha256(),
          s.getRegisteredByUserId(),
          s.getVoidedReason());
    }
  }

  public record ContractGenerationResponse(
      UUID id,
      UUID expedienteId,
      int versionNumber,
      Instant generatedAt,
      UUID generatedBy,
      String documentSha256,
      ContractGenerationStatus status,
      String variantSummary,
      List<String> missingItems,
      boolean hasPdf,
      boolean hasSignedPackage,
      Instant signedAt,
      Instant deliveredAt,
      String deliveryMethod,
      Instant supersededAt,
      String supersededReason,
      List<SignatureResponse> signatures) {

    public static ContractGenerationResponse from(ContractGeneration c, List<ContractSignature> signatures) {
      return new ContractGenerationResponse(
          c.getId(),
          c.getExpedienteId(),
          c.getVersionNumber(),
          c.getGeneratedAt(),
          c.getGeneratedBy(),
          c.getDocumentSha256(),
          c.getStatus(),
          c.getVariantSummary(),
          c.missingItemList(),
          c.getPdfStorageKey() != null,
          c.getSignedPackageKey() != null,
          c.getSignedAt(),
          c.getDeliveredAt(),
          c.getDeliveryMethod(),
          c.getSupersededAt(),
          c.getSupersededReason(),
          signatures.stream().map(SignatureResponse::from).toList());
    }
  }

  public record SigningLinkResponse(UUID signatureId, String signerName, String signerCapacity, String url, Instant expiresAt, boolean emailed) {

    public static SigningLinkResponse from(IssuedSigningLink link) {
      return new SigningLinkResponse(link.signatureId(), link.signerName(), link.signerCapacity(), link.url(), link.expiresAt(), link.emailed());
    }
  }

  public record GenerateResponse(ContractGenerationResponse contract, List<SigningLinkResponse> signingLinks) {}

  public record ReadinessResponse(boolean ready, List<String> blockers, List<String> missingData, String variant) {

    public static ReadinessResponse from(Readiness r) {
      return new ReadinessResponse(r.ready(), r.blockers(), r.missingData(), r.variant());
    }
  }

  public record SignRequest(String typedName, String signatureImageBase64, String documentSha256, boolean accepted) {}

  public record CalculationsResponse(
      String price,
      String priceWritten,
      String commission,
      String vat,
      String totalCommissionWithVat,
      String penalty,
      int exclusivityDays,
      String exclusivityEndDate) {

    public static CalculationsResponse from(ContractCalculator.Result r) {
      return new CalculationsResponse(
          r.price().toPlainString(),
          r.priceWritten(),
          r.commission().toPlainString(),
          r.vat().toPlainString(),
          r.totalCommissionWithVat().toPlainString(),
          r.penalty().toPlainString(),
          r.exclusivityDays(),
          r.exclusivityEndDate().toString());
    }
  }
}
