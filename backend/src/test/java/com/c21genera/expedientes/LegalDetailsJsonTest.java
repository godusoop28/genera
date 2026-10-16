package com.c21genera.expedientes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Control de Cambios #19: la fecha de inscripción de la escritura se guarda y lee en el JSON de datos legales. */
class LegalDetailsJsonTest {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void deedRegistryDateSurvivesTheRoundTripAndOldJsonStillReads() throws Exception {
    LegalDetails.DeedData deed =
        new LegalDetails.DeedData("45,678", LocalDate.of(2010, 3, 15), "Roberto Díaz Soto", "12", "Cuernavaca, Morelos", "FR-1", LocalDate.of(2010, 5, 2));
    LegalDetails.DeedData back = mapper.readValue(mapper.writeValueAsString(deed), LegalDetails.DeedData.class);
    assertThat(back).isEqualTo(deed);

    LegalDetails.DeedData old = mapper.readValue("{\"number\":\"1\",\"notaryName\":\"X\"}", LegalDetails.DeedData.class);
    assertThat(old.number()).isEqualTo("1");
    assertThat(old.registryDate()).isNull();
  }
}
