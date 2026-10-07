package com.c21genera.expedientes.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.web.ExpedienteOverviewController.StatusGroup;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ExpedienteOverviewStatusGroupTest {

  /** Pendientes + en revisión + completos debe sumar el total: cada estatus va en un solo grupo. */
  @Test
  void everyStatusBelongsToExactlyOneGroup() {
    for (ExpedienteStatus status : ExpedienteStatus.values()) {
      long groups = Arrays.stream(StatusGroup.values()).filter(g -> g.statuses().contains(status)).count();
      assertThat(groups).as(status.name()).isEqualTo(1);
    }
  }
}
