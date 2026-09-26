package com.c21genera.expedientes.application;

import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * EXPEDIENT_VIEW_ALL ve cualquier expediente; EXPEDIENT_VIEW_OWN solo los
 * que el propio usuario creó. Cualquier otro caso responde 404 (igual que un
 * expediente inexistente) para no revelar que existe.
 */
@Component
class ExpedienteAccessPolicyImpl implements ExpedienteAccessPolicy {

  private final ExpedienteRepository repository;

  ExpedienteAccessPolicyImpl(ExpedienteRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public void requireAccess(UUID expedienteId, UUID userId, Collection<String> permissions) {
    if (!canAccess(expedienteId, userId, permissions)) {
      throw new NotFoundException("Expediente", expedienteId);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canAccess(UUID expedienteId, UUID userId, Collection<String> permissions) {
    if (expedienteId == null || userId == null || permissions == null) {
      return false;
    }
    return repository
        .findById(expedienteId)
        .map(
            e ->
                permissions.contains("EXPEDIENT_VIEW_ALL")
                    || (permissions.contains("EXPEDIENT_VIEW_OWN") && userId.equals(e.getCreatedByUserId())))
        .orElse(false);
  }
}
