package com.c21genera.documents;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  /**
   * Avance documental de varios expedientes en una sola consulta (para el
   * listado). Solo cuentan los documentos obligatorios que aplican:
   * received = tienen archivo vigente (cargado, por revisar o aceptado);
   * accepted = aceptados por el revisor.
   */
  Map<UUID, DocumentProgressView> documentProgressOf(Collection<UUID> expedienteIds);

  record DocumentProgressView(int required, int received, int accepted) {}

  record PageView(int pageNumber, String storageKeyOriginal, String mimeType) {}

  /** name: con el que se muestra (el capturado si se agregó a mano). */
  record AcceptedDocumentView(UUID documentId, String type, UUID participantId, String pdfStorageKey, String name) {

    public AcceptedDocumentView(UUID documentId, String type, UUID participantId, String pdfStorageKey) {
      this(documentId, type, participantId, pdfStorageKey, null);
    }
  }

  /**
   * currentVersionId: la versión vigente del documento (null si no tiene archivo). name: con el
   * que se muestra. exception: falta, pero el asesor aprobó seguir sin él (no bloquea).
   */
  record RequirementStatusView(
      UUID documentId,
      com.c21genera.shared.domain.DocumentTypeCode type,
      UUID participantId,
      boolean required,
      String status,
      UUID currentVersionId,
      String name,
      boolean exception,
      /* Control de Cambios #14: el asesor lo marcó "Pendiente" (también cuenta en exception: no bloquea). */
      boolean markedPending) {

    public RequirementStatusView(
        UUID documentId,
        com.c21genera.shared.domain.DocumentTypeCode type,
        UUID participantId,
        boolean required,
        String status,
        UUID currentVersionId,
        String name,
        boolean exception) {
      this(documentId, type, participantId, required, status, currentVersionId, name, exception, false);
    }

    public RequirementStatusView(
        UUID documentId, com.c21genera.shared.domain.DocumentTypeCode type, UUID participantId, boolean required, String status) {
      this(documentId, type, participantId, required, status, null, null, false, false);
    }

    public RequirementStatusView(
        UUID documentId,
        com.c21genera.shared.domain.DocumentTypeCode type,
        UUID participantId,
        boolean required,
        String status,
        UUID currentVersionId) {
      this(documentId, type, participantId, required, status, currentVersionId, null, false, false);
    }

    /** Nombre con el que se muestra: el capturado o, si no hay, el del tipo. */
    public String displayName() {
      return name != null ? name : com.c21genera.shared.domain.DocumentTypeLabels.of(type);
    }
  }

  /** Archivo recibido por otro módulo para guardarlo como documento (se lee en flujo, sin copiarlo a memoria). */
  record IncomingFile(long size, String originalFilename, org.springframework.core.io.InputStreamSource source) {}

  /**
   * Guarda el aviso de privacidad firmado en papel como documento del expediente (mismo
   * almacenamiento, visor y versiones que los demás). Devuelve el id del documento.
   */
  /**
   * Control de Cambios #20: guarda el contrato firmado fuera del sistema (DocuSign o escaneo) como
   * documento del expediente (cada carga es una versión nueva). Devuelve el id del documento.
   */
  UUID attachSignedContract(UUID expedienteId, List<IncomingFile> files, com.c21genera.shared.events.Actor actor);

  UUID attachSignedPrivacyNotice(UUID expedienteId, List<IncomingFile> files, com.c21genera.shared.events.Actor actor);

  /** Versión vigente de un documento: los datos leídos de versiones anteriores ya no cuentan. */
  Optional<UUID> currentVersionIdOf(UUID documentId);

  /** Versiones de un documento, de la más reciente a la más antigua (Control de Cambios #23: elegir cuál enviar). */
  List<VersionView> versionViewsOf(UUID documentId);

  /** pdfStorageKey: PDF procesado de la versión (null si no generó PDF). */
  record VersionView(UUID id, UUID documentId, int versionNumber, java.time.Instant uploadedAt, String pdfStorageKey) {}
}
