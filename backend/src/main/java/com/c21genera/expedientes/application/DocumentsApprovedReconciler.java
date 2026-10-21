package com.c21genera.expedientes.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import java.util.EnumSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Repara expedientes que quedaron atorados antes de corregir {@link ExpedienteService#recordDocumentsApproved}:
 * todos sus documentos obligatorios ya estaban aceptados (o marcados "No aplica"), pero seguían en
 * "Esperando documentos" porque el cliente nunca oprimió "Enviar" en su liga. El aviso de "todo
 * aprobado" solo se publica una vez, así que sin esta revisión no avanzarían nunca. Corre una vez
 * al arrancar y es idempotente.
 */
@Component
class DocumentsApprovedReconciler {

  private static final Logger log = LoggerFactory.getLogger(DocumentsApprovedReconciler.class);
  private static final EnumSet<ExpedienteStatus> BEFORE_APPROVAL =
      EnumSet.of(ExpedienteStatus.WAITING_DOCUMENTS, ExpedienteStatus.DOCUMENTS_RECEIVED, ExpedienteStatus.UNDER_REVIEW);

  private final ExpedienteRepository expedientes;
  private final DocumentsApi documents;
  private final ExpedienteService service;

  DocumentsApprovedReconciler(ExpedienteRepository expedientes, DocumentsApi documents, ExpedienteService service) {
    this.expedientes = expedientes;
    this.documents = documents;
    this.service = service;
  }

  @EventListener(ApplicationReadyEvent.class)
  void reconcile() {
    int repaired = 0;
    try {
      for (UUID id : expedientes.findByStatusIn(BEFORE_APPROVAL).stream().map(Expediente::getId).toList()) {
        if (documents.allRequiredAccepted(id)) {
          service.recordDocumentsApproved(id);
          repaired++;
        }
      }
    } catch (RuntimeException e) {
      // Nunca impide el arranque: en el peor caso el expediente sigue como estaba.
      log.warn("No se pudo revisar si hay expedientes con la documentación ya aprobada ({})", e.getClass().getSimpleName());
    }
    if (repaired > 0) {
      log.info("{} expediente(s) con todos sus documentos obligatorios aceptados pasaron a documentación aprobada", repaired);
    }
  }
}
