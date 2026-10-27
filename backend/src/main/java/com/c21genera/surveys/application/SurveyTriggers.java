package com.c21genera.surveys.application;

import com.c21genera.shared.events.ClosingEvents.ClosingCompleted;
import com.c21genera.shared.events.ContractEvents.ContractDelivered;
import com.c21genera.shared.events.ContractEvents.ContractFullySigned;
import com.c21genera.shared.events.ContractEvents.SignedContractUploaded;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsApproved;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsUploaded;
import com.c21genera.shared.events.DocumentEvents.DocumentFollowUp;
import com.c21genera.shared.events.DocumentEvents.DocumentFollowUpChanged;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteCreated;
import com.c21genera.shared.events.NotaryEvents.NotaryPackageSent;
import com.c21genera.shared.events.PrivacyEvents.PrivacyAccepted;
import com.c21genera.surveys.domain.Touchpoint;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Control de Cambios #28: puntos de contacto vía sistema. Cada evento del
 * proceso dispara (si el punto está activo) la encuesta del expediente; el
 * envío ocurre después, según el retraso configurado. Un error aquí nunca
 * afecta al proceso que publicó el evento.
 */
@Component
class SurveyTriggers {

  private static final Logger log = LoggerFactory.getLogger(SurveyTriggers.class);

  private final SurveyService surveys;

  SurveyTriggers(SurveyService surveys) {
    this.surveys = surveys;
  }

  @ApplicationModuleListener
  void on(ExpedienteCreated event) {
    fire(Touchpoint.REGISTRATION, event.expedienteId());
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsUploaded event) {
    fire(Touchpoint.DOCUMENTS_UPLOADED, event.expedienteId());
  }

  @ApplicationModuleListener
  void on(PrivacyAccepted event) {
    fire(Touchpoint.PRIVACY_SIGNED, event.expedienteId());
  }

  /** Todos los documentos quedaron revisados y aceptados: el cliente recibe la notificación. */
  @ApplicationModuleListener
  void on(AllRequiredDocumentsApproved event) {
    fire(Touchpoint.AI_VALIDATION, event.expedienteId());
  }

  @ApplicationModuleListener
  void on(DocumentFollowUpChanged event) {
    if (event.change() == DocumentFollowUp.UPDATE_REQUESTED) {
      fire(Touchpoint.DOCUMENT_UPDATE, event.expedienteId());
    }
  }

  @ApplicationModuleListener
  void on(ContractDelivered event) {
    fire(Touchpoint.CONTRACT_SENT, event.expedienteId());
  }

  @ApplicationModuleListener
  void on(ContractFullySigned event) {
    fire(Touchpoint.CONTRACT_SIGNED, event.expedienteId());
  }

  /** Contrato firmado fuera del sistema (DocuSign o en papel); los históricos no cuentan. */
  @ApplicationModuleListener
  void on(SignedContractUploaded event) {
    if (!event.historical()) {
      fire(Touchpoint.CONTRACT_SIGNED, event.expedienteId());
    }
  }

  @ApplicationModuleListener
  void on(NotaryPackageSent event) {
    fire(Touchpoint.NOTARY_SENT, event.expedienteId());
  }

  /** Cierre: la encuesta del cierre y, con su propio retraso (30 días), la de seguimiento post-venta. */
  @ApplicationModuleListener
  void on(ClosingCompleted event) {
    fire(Touchpoint.CLOSING, event.expedienteId());
    fire(Touchpoint.POST_SALE, event.expedienteId());
  }

  private void fire(Touchpoint touchpoint, UUID expedienteId) {
    try {
      surveys.trigger(touchpoint, expedienteId);
    } catch (RuntimeException e) {
      log.warn("No se pudo programar la encuesta {} del expediente {} ({})", touchpoint, expedienteId, e.getClass().getSimpleName());
    }
  }
}
