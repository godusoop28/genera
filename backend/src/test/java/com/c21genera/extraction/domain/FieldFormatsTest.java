package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lecturas reales de producción (E2E 02/10) de la INE fotografiada de lado. */
class FieldFormatsTest {

  @Test
  void theFixtureValuesAreValid() {
    assertThat(FieldFormats.problem("electorKey", "RDCACL85031417H900")).isEmpty();
    assertThat(FieldFormats.problem("curp", "ROCC850314HMSDLR07")).isEmpty();
    assertThat(FieldFormats.problem("rfc", "ROCC850314TQ7")).isEmpty();
    assertThat(FieldFormats.problem("rfc", "IDS260101AA1")).isEmpty();
  }

  @Test
  void misreadingsSeenInProductionAreCaught() {
    assertThat(FieldFormats.problem("electorKey", "RDCAL8503147H900")).isPresent(); // 16 caracteres
    assertThat(FieldFormats.problem("electorKey", "RDCALB85031417H900")).isEmpty(); // formato válido: solo el revisor puede notarlo
    assertThat(FieldFormats.problem("curp", "ROCCR850314HMSDLR07")).isPresent(); // 19 caracteres
  }

  @Test
  void fieldsWithoutAKnownFormatAreNotJudged() {
    assertThat(FieldFormats.problem("fullName", "lo que sea")).isEmpty();
  }
}
