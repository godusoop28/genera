package com.c21genera.expedientes.application;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.shared.staff.StaffWorkload;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Control de Cambios #25: expedientes en curso de un asesor (hay que reasignarlos antes de darle de baja). */
@Component
@Transactional(readOnly = true)
class ExpedienteWorkload implements StaffWorkload {

  private static final EnumSet<ExpedienteStatus> FINISHED = EnumSet.of(ExpedienteStatus.CLOSED, ExpedienteStatus.PROPERTY_REJECTED);

  private final ExpedienteRepository expedientes;

  ExpedienteWorkload(ExpedienteRepository expedientes) {
    this.expedientes = expedientes;
  }

  @Override
  public List<Item> assignedTo(UUID userId) {
    return List.of(new Item("Expedientes en curso", expedientes.countByAssignedAdvisorIdAndArchivedAtIsNullAndStatusNotIn(userId, FINISHED)));
  }
}
