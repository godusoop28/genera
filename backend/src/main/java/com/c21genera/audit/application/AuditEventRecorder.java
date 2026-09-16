package com.c21genera.audit.application;

import com.c21genera.audit.domain.Activity;
import com.c21genera.audit.domain.ActivityCategory;
import com.c21genera.audit.domain.AuditEvent;
import com.c21genera.audit.infrastructure.ActivityRepository;
import com.c21genera.audit.infrastructure.AuditEventRepository;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsApproved;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsUploaded;
import com.c21genera.shared.events.DocumentEvents.DocumentReviewed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionProcessed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionUploaded;
import com.c21genera.shared.events.DocumentEvents.ReceptionSigned;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteCreated;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteRequirementsChanged;
import com.c21genera.shared.events.ExpedienteEvents.PropertyAccepted;
import com.c21genera.shared.events.ExpedienteEvents.PropertyRejected;
import com.c21genera.shared.events.PrivacyEvents.PrivacyAccepted;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escucha los eventos de integración de los demás módulos y los traduce a
 * dos registros distintos (ver AGENTS §48-49): un {@link AuditEvent}
 * técnico y una entrada de {@link Activity} amigable para la UI. Ninguno de
 * los eventos escuchados aquí transporta PII (solo IDs, tipos y enums), así
 * que no hay riesgo de filtrarla a los logs de auditoría.
 */
@Component
@Transactional
class AuditEventRecorder {

  private final AuditEventRepository auditEventRepository;
  private final ActivityRepository activityRepository;
  private final Clock clock;

  AuditEventRecorder(AuditEventRepository auditEventRepository, ActivityRepository activityRepository, Clock clock) {
    this.auditEventRepository = auditEventRepository;
    this.activityRepository = activityRepository;
    this.clock = clock;
  }

  @ApplicationModuleListener
  void on(ExpedienteCreated event) {
    record("ExpedienteCreated", "Expediente", event.expedienteId(), event.createdByUserId(), "Expediente creado: " + event.folio());
    activity(event.expedienteId(), ActivityCategory.EXPEDIENT, "Expediente creado (" + event.folio() + ")");
  }

  @ApplicationModuleListener
  void on(ExpedienteRequirementsChanged event) {
    record(
        "ExpedienteRequirementsChanged",
        "Expediente",
        event.expedienteId(),
        null,
        "Requisitos documentales recalculados (" + event.requirements().size() + " requisitos)");
  }

  @ApplicationModuleListener
  void on(PropertyAccepted event) {
    record("PropertyAccepted", "Expediente", event.expedienteId(), event.decidedByUserId(), "Inmueble aceptado");
    activity(event.expedienteId(), ActivityCategory.PROPERTY_DECISION, "El inmueble fue aceptado");
  }

  @ApplicationModuleListener
  void on(PropertyRejected event) {
    record("PropertyRejected", "Expediente", event.expedienteId(), event.decidedByUserId(), "Inmueble rechazado");
    activity(event.expedienteId(), ActivityCategory.PROPERTY_DECISION, "El inmueble fue rechazado");
  }

  @ApplicationModuleListener
  void on(PrivacyAccepted event) {
    record("PrivacyAccepted", "Expediente", event.expedienteId(), null, "Aviso de privacidad aceptado por el cliente");
    activity(event.expedienteId(), ActivityCategory.PRIVACY, "El cliente aceptó el aviso de privacidad");
  }

  @ApplicationModuleListener
  void on(DocumentVersionUploaded event) {
    record(
        "DocumentVersionUploaded",
        "Document",
        event.documentId(),
        null,
        "Nueva versión cargada de tipo " + event.type());
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "El cliente cargó un documento de tipo " + event.type());
  }

  @ApplicationModuleListener
  void on(DocumentVersionProcessed event) {
    record("DocumentVersionProcessed", "Document", event.documentId(), null, "Documento procesado (normalización/PDF listo)");
  }

  @ApplicationModuleListener
  void on(DocumentReviewed event) {
    record("DocumentReviewed", "Document", event.documentId(), null, "Documento revisado: " + event.decision());
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "Un documento fue revisado: " + event.decision());
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsUploaded event) {
    record("AllRequiredDocumentsUploaded", "Expediente", event.expedienteId(), null, "Todos los documentos obligatorios fueron cargados");
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "El cliente completó la carga de todos los documentos obligatorios");
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsApproved event) {
    record("AllRequiredDocumentsApproved", "Expediente", event.expedienteId(), null, "Todos los documentos obligatorios fueron aceptados");
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "Todos los documentos obligatorios fueron aceptados");
  }

  @ApplicationModuleListener
  void on(ReceptionSigned event) {
    record("ReceptionSigned", "Expediente", event.expedienteId(), event.signedByUserId(), "Recepción de documentos firmada por staff");
    activity(event.expedienteId(), ActivityCategory.DOCUMENT, "La recepción de documentos fue firmada");
  }

  private void record(String eventType, String aggregateType, UUID aggregateId, UUID actorUserId, String summary) {
    Instant now = clock.instant();
    auditEventRepository.save(new AuditEvent(now, eventType, aggregateType, aggregateId, actorUserId, summary));
  }

  private void activity(UUID expedienteId, ActivityCategory category, String message) {
    activityRepository.save(new Activity(expedienteId, clock.instant(), category, message));
  }
}
