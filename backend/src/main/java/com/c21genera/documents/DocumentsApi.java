package com.c21genera.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** API pública del módulo documents, usada por documentprocessing/extraction/contracts/notifications. */
public interface DocumentsApi {

  List<PageView> pagesOf(UUID documentVersionId);

  void markProcessing(UUID documentVersionId);

  /** qualityLevel: ACCEPTED o ACCEPTED_WITH_WARNINGS; las advertencias se muestran al revisor y nunca bloquean. */
  void markProcessed(UUID documentVersionId, String pdfStorageKey, String normalizedStorageKey, String qualityLevel, List<String> qualityWarnings);

  void markQualityFailed(UUID documentVersionId, String reason);

  void markFailed(UUID documentVersionId, String reason);

  /** Todo documento requerido tiene al menos una versión cargada (no implica que ya esté aceptado). */
  boolean allRequiredUploaded(UUID expedienteId);

  boolean allRequiredAccepted(UUID expedienteId);

  /**
   * PDF de la versión ACTUAL de un documento, solo si el documento ya está
   * ACCEPTED (ver AGENTS §46-47: notifications nunca adjunta versiones
   * rechazadas ni no autorizadas sin acción explícita). Vacío si no aplica.
   */
  Optional<String> currentAcceptedPdfStorageKey(UUID documentId);

  List<UUID> documentIdsOf(UUID expedienteId);

  /** Para verificar acceso: a qué expediente pertenece un documento. */
  Optional<UUID> expedienteIdOfDocument(UUID documentId);

  /** Estado de cada requisito documental (para el checklist previo al contrato). status: nombre de DocumentStatus. */
  List<RequirementStatusView> requirementStatusOf(UUID expedienteId);

  /**
   * Documentos ya ACCEPTED de un expediente, con su tipo y el PDF de la
   * versión vigente. pdfStorageKey es null cuando el staff aceptó una
   * versión que nunca llegó a generar PDF (p. ej. anuló un QUALITY_FAILED).
   */
  List<AcceptedDocumentView> acceptedDocumentsOf(UUID expedienteId);

  record PageView(int pageNumber, String storageKeyOriginal, String mimeType) {}

  record AcceptedDocumentView(UUID documentId, String type, UUID participantId, String pdfStorageKey) {}

  /** currentVersionId: la versión vigente del documento (null si no tiene archivo). */
  record RequirementStatusView(
      UUID documentId, com.c21genera.shared.domain.DocumentTypeCode type, UUID participantId, boolean required, String status, UUID currentVersionId) {

    public RequirementStatusView(
        UUID documentId, com.c21genera.shared.domain.DocumentTypeCode type, UUID participantId, boolean required, String status) {
      this(documentId, type, participantId, required, status, null);
    }
  }

  /** Versión vigente de un documento: los datos leídos de versiones anteriores ya no cuentan. */
  Optional<UUID> currentVersionIdOf(UUID documentId);
}
