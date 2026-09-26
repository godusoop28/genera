package com.c21genera.expedientes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API pública del módulo expedientes para que otros módulos consulten y
 * hagan avanzar el estado de un expediente sin acceder a sus repositorios
 * internos (ver AGENTS §7).
 */
public interface ExpedienteLifecycleApi {

  ExpedienteSummary getSummary(UUID expedienteId);

  ManualClientDataView getManualData(UUID expedienteId);

  record ManualClientDataView(
      BigDecimal authorizedPrice,
      LocalDate contractSignatureDate,
      String email,
      String phone,
      String notificationAddress,
      String visitInstructions,
      Boolean marketingDataAuthorized,
      Boolean receiveAdsAuthorized,
      String additionalServicesRequested,
      Integer bedrooms,
      Integer bathrooms,
      Integer parkingSpots,
      String conservationStatus,
      String availableServices,
      String relevantFeatures,
      BigDecimal landAreaM2,
      BigDecimal builtAreaM2) {}

  /** WAITING_PRIVACY -> WAITING_DOCUMENTS. Idempotente. */
  void recordPrivacyAccepted(UUID expedienteId);

  /** WAITING_DOCUMENTS -> DOCUMENTS_RECEIVED. Idempotente. */
  void recordDocumentsSubmitted(UUID expedienteId);

  /** DOCUMENTS_RECEIVED -> UNDER_REVIEW. Idempotente. */
  void recordUnderReview(UUID expedienteId);

  /** -> CORRECTIONS_REQUESTED. Idempotente. */
  void recordCorrectionsRequested(UUID expedienteId);

  /** UNDER_REVIEW -> DOCUMENTS_APPROVED. Idempotente. */
  void recordDocumentsApproved(UUID expedienteId);

  /** DOCUMENTS_APPROVED -> RECEPTION_SIGNED. Idempotente. */
  void recordReceptionSigned(UUID expedienteId);

  /** RECEPTION_SIGNED -> CONTRACT_PREPARATION. Idempotente. */
  void recordContractPreparation(UUID expedienteId);

  /** CONTRACT_PREPARATION -> READY_FOR_SIGNATURE. Idempotente. */
  void recordReadyForSignature(UUID expedienteId);

  /** READY_FOR_SIGNATURE -> CONTRACT_SIGNED. Idempotente. */
  void recordContractSigned(UUID expedienteId);

  /** READY_FOR_SIGNATURE -> CONTRACT_PREPARATION, cuando el contrato pendiente de firma quedó sin efecto. */
  void recordContractInvalidated(UUID expedienteId);
}
