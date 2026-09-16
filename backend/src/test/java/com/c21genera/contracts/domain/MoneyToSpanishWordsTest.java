package com.c21genera.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyToSpanishWordsTest {

  @Test
  void sixMillionPesosExact() {
    assertThat(MoneyToSpanishWords.convert(new BigDecimal("6000000.00"))).isEqualTo("SEIS MILLONES PESOS 00/100 M.N.");
  }

  @Test
  void withCentsAndThousands() {
    assertThat(MoneyToSpanishWords.convert(new BigDecimal("1234567.89"))).isEqualTo(
        "UN MILLÓN DOSCIENTOS TREINTA Y CUATRO MIL QUINIENTOS SESENTA Y SIETE PESOS 89/100 M.N.");
  }

  @Test
  void oneHundredExact() {
    assertThat(MoneyToSpanishWords.convert(new BigDecimal("100"))).isEqualTo("CIEN PESOS 00/100 M.N.");
  }

  @Test
  void zeroOrNegativeReturnsEmpty() {
    assertThat(MoneyToSpanishWords.convert(BigDecimal.ZERO)).isEmpty();
    assertThat(MoneyToSpanishWords.convert(new BigDecimal("-5"))).isEmpty();
    assertThat(MoneyToSpanishWords.convert(null)).isEmpty();
  }
}
