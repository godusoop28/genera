package com.c21genera.documents.infrastructure;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Candado por expediente para los cambios que alteran "¿ya están todos los
 * documentos cargados/aceptados?". Sin él, varias cargas simultáneas (el
 * cliente sube varios archivos a la vez desde su liga) calculaban esa
 * pregunta cada una sin ver las otras y ninguna publicaba "todos cargados":
 * el cliente no podía enviar su documentación (E2E 02/10). El candado es de
 * PostgreSQL (pg_advisory_xact_lock) y se libera solo al terminar la transacción.
 */
@Component
public class ExpedienteDocumentsLock {

  private final EntityManager entityManager;

  public ExpedienteDocumentsLock(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public void lock(UUID expedienteId) {
    entityManager
        .createNativeQuery("select cast(pg_advisory_xact_lock(hashtext(:key)) as text)")
        .setParameter("key", "expediente-documents:" + expedienteId)
        .getSingleResult();
  }
}
