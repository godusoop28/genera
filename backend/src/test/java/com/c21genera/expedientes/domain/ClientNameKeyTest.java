package com.c21genera.expedientes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientNameKeyTest {

  @Test
  void theSameClientWrittenDifferentlyGetsTheSameKey() {
    assertThat(ClientNameKey.of("Juan Pérez López")).isEqualTo(ClientNameKey.of("  PEREZ LOPEZ, juan "));
    assertThat(ClientNameKey.of("Inmobiliaria Sol, S.A. de C.V.")).isEqualTo(ClientNameKey.of("INMOBILIARIA SOL SA DE CV"));
  }

  @Test
  void differentPeopleDoNotMatch() {
    assertThat(ClientNameKey.of("Juan Pérez López")).isNotEqualTo(ClientNameKey.of("Juan Pérez"));
    assertThat(ClientNameKey.of(null)).isEmpty();
  }
}
