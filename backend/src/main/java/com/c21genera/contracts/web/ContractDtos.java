package com.c21genera.contracts.web;

import com.c21genera.contracts.domain.ContractCalculator;
import com.c21genera.contracts.domain.ContractGeneration;
import com.c21genera.contracts.domain.ContractGenerationStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class ContractDtos {

  private ContractDtos() {}

  public record DeliverRequest(@NotBlank String method) {}

  public record ContractGenerationResponse(
      UUID id,
      UUID expedienteId,
      int versionNumber,
      String docxStorageKey,
      String pdfStorageKey,
      Instant generatedAt,
      UUID generatedBy,
      String sha256,
      ContractGenerationStatus status,
      Instant signedAt,
      Instant deliveredAt,
      String deliveryMethod) {

    public static ContractGenerationResponse from(ContractGeneration c) {
      return new ContractGenerationResponse(
          c.getId(),
          c.getExpedienteId(),
          c.getVersionNumber(),
          c.getDocxStorageKey(),
          c.getPdfStorageKey(),
          c.getGeneratedAt(),
          c.getGeneratedBy(),
          c.getSha256(),
          c.getStatus(),
          c.getSignedAt(),
          c.getDeliveredAt(),
          c.getDeliveryMethod());
    }
  }

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
