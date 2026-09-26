package com.c21genera.closing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClosingServiceRulesTest {

  @Test
  void revocationWindowCountsBusinessDaysOnly() {
    // Viernes 25/09/2026 + 5 días hábiles = viernes 02/10/2026.
    assertThat(ClosingService.plusBusinessDays(LocalDate.of(2026, 9, 25), 5)).isEqualTo(LocalDate.of(2026, 10, 2));
    // Sábado 26/09/2026 + 1 día hábil = lunes 28/09/2026.
    assertThat(ClosingService.plusBusinessDays(LocalDate.of(2026, 9, 26), 1)).isEqualTo(LocalDate.of(2026, 9, 28));
  }
}
