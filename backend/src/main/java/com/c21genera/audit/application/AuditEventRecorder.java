package com.c21genera.audit.application;

import com.c21genera.audit.domain.Activity;
import com.c21genera.audit.domain.ActivityCategory;
import com.c21genera.audit.domain.AuditEvent;
import com.c21genera.audit.infrastructure.ActivityRepository;
import com.c21genera.audit.infrastructure.AuditEventRepository;
import com.c21genera.shared.domain.DocumentTypeLabels;
import com.c21genera.shared.domain.ReviewDecision;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ContractEvents.ContractDelivered;
import com.c21genera.shared.events.ContractEvents.ContractFullySigned;
import com.c21genera.shared.events.ContractEvents.ContractGenerated;
import com.c21genera.shared.events.ContractEvents.ContractSignatureRecorded;
import com.c21genera.shared.events.ContractEvents.ContractSuperseded;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsApproved;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsUploaded;
import com.c21genera.shared.events.DocumentEvents.DocumentApplicabilityChanged;
import com.c21genera.shared.events.DocumentEvents.DocumentContentAssessed;
import com.c21genera.shared.events.DocumentEvents.DocumentQualityFailed;
import com.c21genera.shared.events.DocumentEvents.DocumentReviewed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionProcessed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionUploaded;
import com.c21genera.shared.events.DocumentEvents.ReceptionSigned;
import com.c21genera.shared.events.DocumentEvents.RequiredDocumentsReopened;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteCreated;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteDataCorrected;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteRequirementsChanged;
import com.c21genera.shared.events.ExpedienteEvents.PropertyAccepted;
import com.c21genera.shared.events.ExpedienteEvents.PropertyRejected;
import com.c21genera.shared.events.NotificationEvents.NotificationDelivered;
import com.c21genera.shared.events.NotificationEvents.NotificationDeliveryFailed;
import com.c21genera.shared.events.PrivacyEvents.PrivacyAccepted;
import com.c21genera.shared.events.PublicAccessEvents.PublicLinkGenerated;
import com.c21genera.shared.events.PublicAccessEvents.PublicLinkRevoked;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escucha los eventos de integración de los demás módulos y los traduce a
 * dos registros distintos (ver AGENTS §48-49): un {@link AuditEvent}
 * técnico y una entrada de {@link Activity} amigable para la UI.
 *
 * <p>Cada evento trae su {@link Actor}: la bitácora nunca supone que una
 * acción la hizo el cliente (antes, una carga hecha por un asesor en la
 * oficina aparecía como "El cliente cargó..."). Los mensajes describen solo
 * la acción; quién la hizo se guarda aparte (tipo, nombre y rol).
 */
@Component
@Transactional
class AuditEventRecorder {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Mexico_City"));

  private final AuditEventRepository auditEventRepository;
  private final ActivityRepository activityRepository;
  private final Clock clock;

  AuditEventRecorder(AuditEventRepository auditEventRepository, ActivityRepository activityRepository, Clock clock) {
    this.auditEventRepository = auditEventRepository;
    this.activityRepository = activityRepository;
    this.clock = clock;
  }

  // --- Expediente -------------------------------------------------------

  @ApplicationModuleListener
  void on(ExpedienteCreated event) {
    record("ExpedienteCreated", "Expediente", event.expedienteId(), event.actor(), "Expediente creado: " + event.folio());
    activity(event.expedienteId(), ActivityCategory.EXPEDIENT, "EXPEDIENT_CREATED", event.actor(), null, null,
        "Creó el expediente " + event.folio());
  }

  @ApplicationModuleListener
  void on(ExpedienteRequirementsChanged event) {
    record(
        "ExpedienteRequirementsChanged",
        "Expediente",
        event.expedienteId(),
        Actor.system(),
        "Requisitos documentales recalculados (" + event.requirements().size() + " requisitos)");
  }

  @ApplicationModuleListener
  void on(ExpedienteDataCorrected event) {
    String fields = String.join(", ", event.changedFields());
    String reason = event.reason() != null ? ". Motivo: " + event.reason() : "";
    record("ExpedienteDataCorrected", "Expediente", event.expedienteId(), event.actor(),
        "Corrección en " + event.section() + ": " + fields + reason);
    activity(event.expedienteId(), ActivityCategory.CORRECTION, "DATA_CORRECTED", event.actor(), null, null,
        "Corrigió " + event.section().toLowerCase() + " (" + fields + ")" + reason);
  }

  @ApplicationModuleListener
  void on(PropertyAccepted event) {
    record("PropertyAccepted", "Expediente", event.expedienteId(), event.actor(), "Inmueble aceptado");
    activity(event.expedienteId(), ActivityCategory.PROPERTY_DECISION, "PROPERTY_ACCEPTED", event.actor(), null, null, "Aceptó el inmueble");
  }

  @ApplicationModuleListener
  void on(PropertyRejected event) {
    record("PropertyRejected", "Expediente", event.expedienteId(), event.actor(), "Inmueble rechazado");
    activity(event.expedienteId(), ActivityCategory.PROPERTY_DECISION, "PROPERTY_REJECTED", event.actor(), null, null,
        "Rechazó el inmueble. Motivo: " + event.reason());
  }

  // --- Liga del cliente y privacidad ------------------------------------

  @ApplicationModuleListener
  void on(PublicLinkGenerated event) {
    record("PublicLinkGenerated", "Expediente", event.expedienteId(), event.actor(), "Liga de cliente generada");
    activity(event.expedienteId(), ActivityCategory.LINK, "PUBLIC_LINK_GENERATED", event.actor(), null, null,
        "Generó una liga nueva para el cliente" + (event.expiresAt() != null ? " (vence el " + DATE.format(event.expiresAt()) + ")" : "")
            + "; cualquier liga anterior dejó de funcionar");
  }

  @ApplicationModuleListener
  void on(PublicLinkRevoked event) {
    record("PublicLinkRevoked", "Expediente", event.expedienteId(), event.actor(), "Liga de cliente revocada: " + event.reason());
    activity(event.expedienteId(), ActivityCategory.LINK, "PUBLIC_LINK_REVOKED", event.actor(), null, null,
        "Revocó la liga del cliente (" + event.reason() + ")");
  }

  @ApplicationModuleListener
  void on(PrivacyAccepted event) {
    record("PrivacyAccepted", "Expediente", event.expedienteId(), Actor.client(), "Aviso de privacidad aceptado por el cliente");
    activity(event.expedienteId(), ActivityCategory.PRIVACY, "PRIVACY_ACCEPTED", Actor.client(), null, null,
        event.mainPurposesAccepted() ? "Aceptó y firmó el aviso de privacidad" : "Registró su respuesta al aviso de privacidad sin aceptar la finalidad principal");
  }

  // --- Documentos -------------------------------------------------------

  @ApplicationModuleListener
  void on(DocumentVersionUploaded event) {
    String label = DocumentTypeLabels.of(event.type());
    record("DocumentVersionUploaded", "Document", event.documentId(), event.actor(), "Nueva versión cargada: " + label);
    String message =
        event.actor() != null && event.actor().isStaff()
            ? "Cargó " + label + " en nombre del cliente (" + event.pages().size() + " archivo(s))"
            : "Cargó " + label + " desde su liga (" + event.pages().size() + " archivo(s))";
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "DOCUMENT_UPLOADED", event.actor(), event.documentId(), label, message);
  }

  @ApplicationModuleListener
  void on(DocumentVersionProcessed event) {
    record("DocumentVersionProcessed", "Document", event.documentId(), Actor.system(), "Documento procesado (normalización/PDF listo)");
  }

  @ApplicationModuleListener
  void on(DocumentQualityFailed event) {
    String label = DocumentTypeLabels.of(event.type());
    record("DocumentQualityFailed", "Document", event.documentId(), Actor.system(), "Calidad insuficiente: " + event.reason());
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "DOCUMENT_QUALITY_FAILED", Actor.system(), event.documentId(), label,
        "La verificación automática detectó un problema en " + label + ": " + event.reason());
  }

  @ApplicationModuleListener
  void on(DocumentContentAssessed event) {
    String label = DocumentTypeLabels.of(event.type());
    boolean mismatch = Boolean.FALSE.equals(event.matchesExpectedType());
    boolean illegible = Boolean.FALSE.equals(event.legible());
    record("DocumentContentAssessed", "Document", event.documentId(), Actor.system(),
        "Revisión automática de contenido: coincide=" + event.matchesExpectedType() + ", legible=" + event.legible());
    if (mismatch || illegible) {
      String detail =
          mismatch
              ? "el archivo cargado como " + label + " no parece corresponder a ese documento"
                  + (event.detectedDocumentKind() != null ? " (parece: " + event.detectedDocumentKind() + ")" : "")
              : "el archivo de " + label + " no es legible";
      activity(event.expedienteId(), ActivityCategory.DOCUMENT, "DOCUMENT_CONTENT_ALERT", Actor.system(), event.documentId(), label,
          "Alerta de la revisión automática: " + detail);
    }
  }

  @ApplicationModuleListener
  void on(DocumentReviewed event) {
    String label = DocumentTypeLabels.of(event.type());
    String reason = reasonLabel(event.reasonCode()) + (event.comment() != null ? ": " + event.comment() : "");
    String message =
        switch (event.decision()) {
          case ACCEPTED ->
              event.overrideJustification() != null
                  ? "Aceptó " + label + " POR EXCEPCIÓN pese a las alertas. Justificación: " + event.overrideJustification()
                  : "Aceptó " + label;
          case RETURNED -> "Devolvió " + label + " al cliente para corrección. Motivo: " + reason;
          case REJECTED -> "Rechazó " + label + ". Motivo: " + reason;
        };
    record("DocumentReviewed", "Document", event.documentId(), event.actor(), message);
    activity(event.expedienteId(), ActivityCategory.DOCUMENT,
        event.decision() == ReviewDecision.ACCEPTED && event.overrideJustification() != null ? "DOCUMENT_ACCEPTED_BY_EXCEPTION" : "DOCUMENT_" + event.decision().name(),
        event.actor(), event.documentId(), label, message);
  }

  @ApplicationModuleListener
  void on(DocumentApplicabilityChanged event) {
    String label = DocumentTypeLabels.of(event.type());
    String message =
        event.notApplicable() ? "Marcó " + label + " como \"No aplica\". Justificación: " + event.justification() : "Volvió a solicitar " + label;
    record("DocumentApplicabilityChanged", "Document", event.documentId(), event.actor(), message);
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, event.notApplicable() ? "DOCUMENT_NOT_APPLICABLE" : "DOCUMENT_REQUESTED_AGAIN",
        event.actor(), event.documentId(), label, message);
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsUploaded event) {
    record("AllRequiredDocumentsUploaded", "Expediente", event.expedienteId(), Actor.system(), "Todos los documentos obligatorios fueron cargados");
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsApproved event) {
    record("AllRequiredDocumentsApproved", "Expediente", event.expedienteId(), Actor.system(), "Todos los documentos obligatorios fueron aceptados");
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "ALL_DOCUMENTS_APPROVED", Actor.system(), null, null,
        "Todos los documentos obligatorios quedaron aceptados (o marcados como \"No aplica\")");
  }

  @ApplicationModuleListener
  void on(RequiredDocumentsReopened event) {
    record("RequiredDocumentsReopened", "Expediente", event.expedienteId(), Actor.system(), "Hay documentos obligatorios pendientes de carga");
  }

  @ApplicationModuleListener
  void on(ReceptionSigned event) {
    record("ReceptionSigned", "Expediente", event.expedienteId(), event.actor(), "Recepción de documentos firmada por staff");
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "RECEPTION_SIGNED", event.actor(), null, null, "Firmó la recepción de documentos");
  }

  // --- Contrato ---------------------------------------------------------

  @ApplicationModuleListener
  void on(ContractGenerated event) {
    String message =
        event.draft()
            ? "Generó el borrador INCOMPLETO del contrato (versión " + event.versionNumber() + "), bloqueado para firma"
            : "Generó el contrato versión " + event.versionNumber() + " y lo envió a firma";
    record("ContractGenerated", "Contract", event.contractId(), event.actor(), message);
    activity(event.expedienteId(), ActivityCategory.CONTRACT, event.draft() ? "CONTRACT_DRAFT_GENERATED" : "CONTRACT_GENERATED", event.actor(),
        null, null, message);
  }

  @ApplicationModuleListener
  void on(ContractSuperseded event) {
    String message = "El contrato versión " + event.versionNumber() + " quedó sin efecto: " + event.reason();
    record("ContractSuperseded", "Contract", event.contractId(), event.actor(), message);
    activity(event.expedienteId(), ActivityCategory.CONTRACT, "CONTRACT_SUPERSEDED", event.actor(), null, null, message);
  }

  @ApplicationModuleListener
  void on(ContractSignatureRecorded event) {
    String how = "AUTOGRAPH_SCAN".equals(event.method()) ? "firma autógrafa (contrato escaneado)" : "firma electrónica simple";
    String message =
        "Firmó el contrato versión " + event.versionNumber() + " — " + event.signerName() + " (" + event.signerCapacity() + "), mediante " + how;
    record("ContractSignatureRecorded", "Contract", event.contractId(), event.actor(), message);
    activity(event.expedienteId(), ActivityCategory.CONTRACT, "CONTRACT_SIGNED_BY_PARTY", event.actor(), null, null, message);
  }

  @ApplicationModuleListener
  void on(ContractFullySigned event) {
    String message = "El contrato versión " + event.versionNumber() + " quedó firmado por todas las partes (huella SHA-256 " + event.documentSha256().substring(0, 12) + "…)";
    record("ContractFullySigned", "Contract", event.contractId(), Actor.system(), message);
    activity(event.expedienteId(), ActivityCategory.CONTRACT, "CONTRACT_FULLY_SIGNED", Actor.system(), null, null, message);
  }

  @ApplicationModuleListener
  void on(ContractDelivered event) {
    String message = "Registró la entrega al cliente del contrato firmado versión " + event.versionNumber() + " (" + event.method() + ")";
    record("ContractDelivered", "Contract", event.contractId(), event.actor(), message);
    activity(event.expedienteId(), ActivityCategory.CONTRACT, "CONTRACT_DELIVERED", event.actor(), null, null, message);
  }

  // --- Notificaciones ---------------------------------------------------

  @ApplicationModuleListener
  void on(NotificationDelivered event) {
    record("NotificationDelivered", "Expediente", event.expedienteId(), Actor.system(), "Correo enviado: " + event.kind());
    activity(event.expedienteId(), ActivityCategory.NOTIFICATION, "NOTIFICATION_SENT", Actor.system(), null, null,
        "Envió el aviso \"" + event.kind() + "\" a " + event.maskedRecipient());
  }

  @ApplicationModuleListener
  void on(NotificationDeliveryFailed event) {
    record("NotificationDeliveryFailed", "Expediente", event.expedienteId(), Actor.system(), "Fallo al enviar correo: " + event.kind());
    activity(event.expedienteId(), ActivityCategory.NOTIFICATION, "NOTIFICATION_FAILED", Actor.system(), null, null,
        "No se pudo entregar el aviso \"" + event.kind() + "\" a " + event.maskedRecipient() + ": " + event.error());
  }

  // ---------------------------------------------------------------------

  private static String reasonLabel(String code) {
    if (code == null) {
      return "sin motivo";
    }
    return switch (code) {
      case "BLURRY_IMAGE" -> "Imagen borrosa";
      case "INCOMPLETE_DOCUMENT" -> "Documento incompleto";
      case "EXPIRED_DOCUMENT" -> "Documento vencido";
      case "ILLEGIBLE_INFORMATION" -> "Información ilegible";
      case "WRONG_DOCUMENT" -> "Documento equivocado";
      case "MISSING_PAGE" -> "Falta una página";
      default -> "Otro";
    };
  }

  private void record(String eventType, String aggregateType, UUID aggregateId, Actor actor, String summary) {
    auditEventRepository.save(new AuditEvent(clock.instant(), eventType, aggregateType, aggregateId, actor, summary));
  }

  private void activity(
      UUID expedienteId, ActivityCategory category, String action, Actor actor, UUID documentId, String documentLabel, String message) {
    activityRepository.save(new Activity(expedienteId, clock.instant(), category, action, actor, documentId, documentLabel, message));
  }
}
