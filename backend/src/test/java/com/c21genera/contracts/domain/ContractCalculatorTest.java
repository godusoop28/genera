package com.c21genera.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ContractCalculatorTest {

  @Test
  void commissionIsFivePercent() {
    assertThat(ContractCalculator.commission(new BigDecimal("6000000"))).isEqualByComparingTo("300000.00");
  }

  @Test
  void vatIsSixteenPercentOfCommission() {
    assertThat(ContractCalculator.vat(new BigDecimal("300000"))).isEqualByComparingTo("48000.00");
  }

  @Test
  void penaltyEqualsCommission() {
    BigDecimal commission = new BigDecimal("300000.00");
    assertThat(ContractCalculator.penalty(commission)).isEqualByComparingTo(commission);
  }

  @Test
  void exclusivityIs180CalendarDaysFromSignature() {
    LocalDate signature = LocalDate.of(2026, 9, 14);
    assertThat(ContractCalculator.exclusivityEndDate(signature)).isEqualTo(LocalDate.of(2027, 3, 13));
  }

  @Test
  void fullCalculationForSixMillionPesos() {
    ContractCalculator.Result result = ContractCalculator.calculate(new BigDecimal("6000000"), LocalDate.of(2026, 9, 14));

    assertThat(result.commission()).isEqualByComparingTo("300000.00");
    assertThat(result.vat()).isEqualByComparingTo("48000.00");
    assertThat(result.totalCommissionWithVat()).isEqualByComparingTo("348000.00");
    assertThat(result.penalty()).isEqualByComparingTo("300000.00");
    assertThat(result.exclusivityDays()).isEqualTo(180);
    assertThat(result.exclusivityEndDate()).isEqualTo(LocalDate.of(2027, 3, 13));
    assertThat(result.priceWritten()).isEqualTo("SEIS MILLONES PESOS 00/100 M.N.");
  }

  @Test
  void revocationWindowIsFiveBusinessDaysByContractDefinition() {
    assertThat(ContractCalculator.REVOCATION_BUSINESS_DAYS).isEqualTo(5);
  }
}
