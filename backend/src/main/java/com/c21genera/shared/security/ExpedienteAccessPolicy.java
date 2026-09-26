package com.c21genera.shared.security;

import java.util.Collection;
import java.util.UUID;

/**
 * Aislamiento entre expedientes para usuarios internos. Vive en shared (no
 * en expedientes) para que cualquier módulo con endpoints internos
 * (documents, contracts, publicaccess, ...) pueda verificar el acceso sin
 * crear una dependencia cíclica; la implementación la aporta expedientes.
 *
 * <p>Nunca se debe confiar en que un usuario con EXPEDIENT_VIEW_OWN "no
 * conoce" el id de un expediente ajeno: todo endpoint interno que reciba un
 * id (de expediente, documento, versión, contrato, observación...) debe
 * resolverlo a su expediente y pasar por aquí.
 */
public interface ExpedienteAccessPolicy {

  /**
   * Lanza {@link com.c21genera.shared.domain.NotFoundException} (404, no 403,
   * para no revelar que el expediente existe) si el usuario no puede verlo.
   */
  void requireAccess(UUID expedienteId, UUID userId, Collection<String> permissions);

  boolean canAccess(UUID expedienteId, UUID userId, Collection<String> permissions);
}
