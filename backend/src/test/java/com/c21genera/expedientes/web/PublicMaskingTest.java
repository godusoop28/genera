package com.c21genera.expedientes.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicMaskingTest {

  @Test
  void namesAreMaskedSoAWrongRecipientCannotIdentifyTheOwner() {
    assertThat(PublicExpedienteController.maskName("Juan Pérez López")).isEqualTo("Juan P. L.");
    assertThat(PublicExpedienteController.maskName("Juan Pérez López y María Gómez")).isEqualTo("Juan P. L., María G.");
  }

  @Test
  void addressShowsOnlyTheStartOfTheStreetAndTheCity() {
    assertThat(PublicExpedienteController.maskAddress("Calle Río Balsas No. Ext. 12, Col. Vista Hermosa, Cuernavaca, Morelos, C.P. 62290"))
        .isEqualTo("Calle Río Ba…, Cuernavaca, Morelos");
  }
}
