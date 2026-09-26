package com.c21genera.notifications.application;

import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.identity.UserDirectory;
import com.c21genera.notifications.NotificationsApi;
import com.c21genera.shared.domain.DocumentTypeLabels;
import com.c21genera.shared.domain.ReviewDecision;
import com.c21genera.shared.events.ContractEvents.ContractFullySigned;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsApproved;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsUploaded;
import com.c21genera.shared.events.DocumentEvents.DocumentContentAssessed;
import com.c21genera.shared.events.DocumentEvents.DocumentQualityFailed;
import com.c21genera.shared.events.DocumentEvents.DocumentReviewed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionUploaded;
import com.c21genera.shared.events.ExpedienteEvents.PropertyAccepted;
import com.c21genera.shared.events.ExpedienteEvents.PropertyRejected;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Avisos automáticos del proceso (retroalimentación 25/09, hallazgo 12): al
 * cliente cuando debe corregir algo o cuando avanza su trámite, y al asesor
 * del expediente cuando le toca actuar. Todos quedan en el historial de
 * notificaciones con su estado de entrega.
 *
 * <p>Los correos al cliente nunca incluyen la liga de acceso (solo se guarda
 * su hash); se le pide usar la liga que le compartió su asesor.
 */
@Component
class NotificationTriggers {

  private static final String SIGNATURE = "\n\nCENTURY 21 Genera\nWhatsApp 7778005300 · ventas@c21genera.com";

  private final NotificationsApi notifications;
  private final ExpedienteLifecycleApi expedientes;
  private final UserDirectory users;

  NotificationTriggers(NotificationsApi notifications, ExpedienteLifecycleApi expedientes, UserDirectory users) {
    this.notifications = notifications;
    this.expedientes = expedientes;
    this.users = users;
  }

  @ApplicationModuleListener
  void on(DocumentReviewed event) {
    if (event.decision() == ReviewDecision.ACCEPTED) {
      return;
    }
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    String label = DocumentTypeLabels.of(event.type());
    String action = event.decision() == ReviewDecision.RETURNED ? "Necesitamos que vuelvas a cargar" : "No pudimos aceptar";
    String reason = reasonLabel(event.reasonCode()) + (event.comment() != null ? ": " + event.comment() : "");
    toClient(
        e,
        event.decision() == ReviewDecision.RETURNED ? "Documento devuelto" : "Documento rechazado",
        "Tu expediente " + e.folio() + ": corrige tu " + label,
        "Hola,\n\n"
            + action
            + " tu documento \""
            + label
            + "\" del expediente "
            + e.folio()
            + ".\n\nMotivo: "
            + reason
            + "\n\nEntra a la liga que te compartió tu asesor para cargarlo de nuevo. Si la liga ya no funciona, pídele una nueva."
            + SIGNATURE);
  }

  @ApplicationModuleListener
  void on(DocumentQualityFailed event) {
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    String label = DocumentTypeLabels.of(event.type());
    toClient(
        e,
        "Foto no legible",
        "Tu expediente " + e.folio() + ": vuelve a tomar la foto de tu " + label,
        "Hola,\n\nLa foto que subiste de tu \""
            + label
            + "\" no se pudo leer: "
            + event.reason()
            + ".\n\nVuelve a tomarla con buena luz, sin reflejos y con el documento completo, y cárgala en la misma liga."
            + SIGNATURE);
  }

  @ApplicationModuleListener
  void on(DocumentContentAssessed event) {
    if (!Boolean.FALSE.equals(event.matchesExpectedType()) && !Boolean.FALSE.equals(event.legible())) {
      return;
    }
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    String label = DocumentTypeLabels.of(event.type());
    toAdvisor(
        e,
        "Alerta de documento",
        "Expediente " + e.folio() + ": revisa el archivo cargado como " + label,
        "La revisión automática detectó que el archivo cargado como \""
            + label
            + "\" "
            + (Boolean.FALSE.equals(event.matchesExpectedType())
                ? "no parece corresponder a ese documento" + (event.detectedDocumentKind() != null ? " (parece: " + event.detectedDocumentKind() + ")" : "")
                : "no es legible")
            + ".\n\n"
            + (event.observations() != null ? "Observaciones: " + event.observations() + "\n\n" : "")
            + "No se podrá aceptar sin una autorización de excepción; lo recomendable es devolverlo al cliente con el motivo.");
  }

  /**
   * El cliente cargó la corrección de un documento devuelto o rechazado. Las
   * cargas normales no generan un correo cada una: el aviso agregado es
   * AllRequiredDocumentsUploaded.
   */
  @ApplicationModuleListener
  void on(DocumentVersionUploaded event) {
    boolean byClient = event.actor() == null || !event.actor().isStaff();
    boolean correction = "RETURNED".equals(event.previousStatus()) || "REJECTED".equals(event.previousStatus());
    if (!byClient || !correction) {
      return;
    }
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    String label = DocumentTypeLabels.of(event.type());
    toAdvisor(
        e,
        "Corrección cargada",
        "Expediente " + e.folio() + ": el cliente corrigió " + label,
        "El cliente del expediente " + e.folio() + " cargó una nueva versión de \"" + label + "\" que se le había devuelto. Siguiente paso: revisarla.");
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsUploaded event) {
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    toAdvisor(
        e,
        "Documentos completos por revisar",
        "Expediente " + e.folio() + ": el cliente cargó todos sus documentos",
        "El cliente del expediente " + e.folio() + " (" + e.ownerDisplayName() + ") ya cargó todos los documentos obligatorios. Siguiente paso: revisarlos.");
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsApproved event) {
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    toClient(
        e,
        "Documentación completa",
        "Tu expediente " + e.folio() + ": documentación completa",
        "Hola,\n\nRevisamos y aceptamos todos tus documentos del expediente "
            + e.folio()
            + ". El siguiente paso es la firma del contrato de intermediación; tu asesor te enviará la liga para firmarlo."
            + SIGNATURE);
    toAdvisor(
        e,
        "Documentación aprobada",
        "Expediente " + e.folio() + ": documentación aprobada",
        "Todos los documentos obligatorios del expediente " + e.folio() + " quedaron aceptados. Siguiente paso: firmar la recepción y generar el contrato.");
  }

  @ApplicationModuleListener
  void on(ContractFullySigned event) {
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    toClient(
        e,
        "Contrato firmado",
        "Tu expediente " + e.folio() + ": contrato firmado",
        "Hola,\n\nEl contrato de intermediación (versión "
            + event.versionNumber()
            + ") quedó firmado por todas las partes. Tu asesor te entregará un tanto firmado del contrato y sus anexos."
            + SIGNATURE);
    toAdvisor(
        e,
        "Contrato firmado",
        "Expediente " + e.folio() + ": contrato firmado por todas las partes",
        "El contrato versión " + event.versionNumber() + " del expediente " + e.folio()
            + " quedó firmado. Pendientes: entregar un tanto firmado al cliente y decidir sobre el inmueble.");
  }

  @ApplicationModuleListener
  void on(PropertyAccepted event) {
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    toAdvisor(e, "Inmueble aceptado", "Expediente " + e.folio() + ": inmueble aceptado", "El inmueble del expediente " + e.folio() + " fue aceptado.");
  }

  @ApplicationModuleListener
  void on(PropertyRejected event) {
    ExpedienteSummary e = expedientes.getSummary(event.expedienteId());
    toAdvisor(
        e,
        "Inmueble rechazado",
        "Expediente " + e.folio() + ": inmueble rechazado",
        "El inmueble del expediente " + e.folio() + " fue rechazado. Motivo: " + event.reason());
  }

  private void toClient(ExpedienteSummary e, String kind, String subject, String body) {
    ManualClientDataView data = expedientes.getManualData(e.id());
    String email = data.email();
    if (email == null || email.isBlank()) {
      email = e.participants().stream().filter(p -> p.email() != null && !p.email().isBlank()).map(p -> p.email()).findFirst().orElse(null);
    }
    notifications.notify(e.id(), kind + " (cliente)", email, subject, body);
  }

  private void toAdvisor(ExpedienteSummary e, String kind, String subject, String body) {
    UUID advisorId = e.createdByUserId();
    String email = users.contactOf(advisorId).filter(u -> u.active()).map(u -> u.email()).orElse(null);
    notifications.notify(e.id(), kind + " (asesor)", email, subject, body);
  }

  private static String reasonLabel(String code) {
    if (code == null) {
      return "sin motivo especificado";
    }
    return switch (code) {
      case "BLURRY_IMAGE" -> "la imagen está borrosa";
      case "INCOMPLETE_DOCUMENT" -> "el documento está incompleto";
      case "EXPIRED_DOCUMENT" -> "el documento está vencido";
      case "ILLEGIBLE_INFORMATION" -> "la información no es legible";
      case "WRONG_DOCUMENT" -> "no es el documento solicitado";
      case "MISSING_PAGE" -> "falta una página";
      default -> "otro";
    };
  }
}
