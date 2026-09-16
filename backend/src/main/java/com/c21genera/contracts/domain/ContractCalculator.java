package com.c21genera.contracts.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Reglas contractuales centralizadas (ver AGENTS §47/§76/§78). Siempre
 * {@link BigDecimal}, nunca {@code double}. Ningún número mágico repetido
 * por otros servicios.
 */
public final class ContractCalculator {

  public static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");
  public static final BigDecimal VAT_RATE = new BigDecimal("0.16");
  public static final BigDecimal PENALTY_RATE = BigDecimal.ONE; // 100% de la comisión pactada
  public static final int EXCLUSIVITY_DAYS = 180;
  public static final int REVOCATION_BUSINESS_DAYS = 5;

  private ContractCalculator() {}

  public static BigDecimal commission(BigDecimal price) {
    return price.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal vat(BigDecimal commission) {
    return commission.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal totalCommissionWithVat(BigDecimal commission, BigDecimal vat) {
    return commission.add(vat).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal penalty(BigDecimal commission) {
    return commission.multiply(PENALTY_RATE).setScale(2, RoundingMode.HALF_UP);
  }

  public static LocalDate exclusivityEndDate(LocalDate signatureDate) {
    return signatureDate.plusDays(EXCLUSIVITY_DAYS);
  }

  public record Result(
      BigDecimal price,
      String priceWritten,
      BigDecimal commission,
      BigDecimal vat,
      BigDecimal totalCommissionWithVat,
      BigDecimal penalty,
      int exclusivityDays,
      LocalDate exclusivityEndDate) {}

  public static Result calculate(BigDecimal price, LocalDate signatureDate) {
    BigDecimal safePrice = price != null ? price : BigDecimal.ZERO;
    BigDecimal commission = commission(safePrice);
    BigDecimal vat = vat(commission);
    LocalDate effectiveSignatureDate = signatureDate != null ? signatureDate : LocalDate.now();
    return new Result(
        safePrice,
        MoneyToSpanishWords.convert(safePrice),
        commission,
        vat,
        totalCommissionWithVat(commission, vat),
        penalty(commission),
        EXCLUSIVITY_DAYS,
        exclusivityEndDate(effectiveSignatureDate));
  }
}
