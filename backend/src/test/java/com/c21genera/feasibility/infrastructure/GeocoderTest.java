package com.c21genera.feasibility.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Las ubicaciones de los anuncios vienen con abreviaturas y datos que confunden al buscador. */
class GeocoderTest {

  @Test
  void cleansPortalStyleAddressesAndFallsBackToColonia() {
    assertThat(Geocoder.variants("Calle Río Balsas No. Ext. 12, Col. Vista Hermosa, Cuernavaca, Mor., C.P. 62290"))
        .first()
        .isEqualTo("Calle Río Balsas, Vista Hermosa, Cuernavaca, Morelos");
    assertThat(Geocoder.variants("Calle Río Balsas No. Ext. 12, Col. Vista Hermosa, Cuernavaca, Mor., C.P. 62290"))
        .contains("Vista Hermosa, Cuernavaca, Morelos", "Vista Hermosa, Morelos");
    assertThat(Geocoder.variants("Fracc. Lomas de Cortés, Cuernavaca")).first().isEqualTo("Lomas de Cortés, Cuernavaca, Morelos");
    assertThat(Geocoder.variants("Jiutepec")).first().isEqualTo("Jiutepec, Morelos");
    assertThat(Geocoder.variants(null)).isEmpty();
  }
}
