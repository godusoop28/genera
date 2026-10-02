package com.c21genera.expedientes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.shared.events.Actor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** E2E 02/10: editar a "INMUEBLES DEMO DEL SUR, S.A. DE C.V." respondía 500 (la sección no cabía en 48). */
class ExpedienteChangeTest {

  @Test
  void aLongParticipantNameFitsInTheChangeSection() {
    String section = "Participante: INMUEBLES DEMO DEL SUR, S.A. DE C.V.";
    ExpedienteChange change = new ExpedienteChange(UUID.randomUUID(), Instant.now(), Actor.system(), section, "RFC", null, "IDS260101AA1", "prueba");

    assertThat(change.getSection()).isEqualTo(section);
    assertThat(new ExpedienteChange(UUID.randomUUID(), Instant.now(), Actor.system(), "x".repeat(500), "f", null, null, null).getSection()).hasSize(200);
  }
}
