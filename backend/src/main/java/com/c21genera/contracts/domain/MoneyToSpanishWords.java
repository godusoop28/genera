package com.c21genera.contracts.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Convierte un monto a su representación en letra (ver AGENTS §77). */
public final class MoneyToSpanishWords {

  private static final String[] UNITS = {
    "", "UN", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE",
    "CATORCE", "QUINCE"
  };
  private static final String[] TENS = {
    "", "", "VEINTE", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"
  };
  private static final String[] HUNDREDS = {
    "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS",
    "OCHOCIENTOS", "NOVECIENTOS"
  };

  private MoneyToSpanishWords() {}

  public static String convert(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      return "";
    }
    BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
    long pesos = rounded.longValue();
    int cents = rounded.subtract(BigDecimal.valueOf(pesos)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    return "%s PESOS %02d/100 M.N.".formatted(integerToWords(pesos), cents);
  }

  private static String chunkToWords(int n) {
    if (n == 0) return "";
    if (n == 100) return "CIEN";
    StringBuilder words = new StringBuilder();
    int h = n / 100;
    int rest = n % 100;
    if (h > 0) words.append(HUNDREDS[h]).append(' ');
    if (rest <= 15) {
      words.append(UNITS[rest]);
    } else if (rest < 20) {
      words.append("DIECI").append(UNITS[rest - 10]);
    } else if (rest < 30) {
      words.append(rest == 20 ? "VEINTE" : "VEINTI" + UNITS[rest - 20]);
    } else {
      int t = rest / 10;
      int u = rest % 10;
      words.append(TENS[t]);
      if (u > 0) words.append(" Y ").append(UNITS[u]);
    }
    return words.toString().trim();
  }

  private static String integerToWords(long n) {
    if (n == 0) return "CERO";
    if (n == 1) return "UN";

    long millions = n / 1_000_000;
    long thousands = (n % 1_000_000) / 1000;
    long units = n % 1000;

    StringBuilder words = new StringBuilder();
    if (millions > 0) {
      words.append(millions == 1 ? "UN MILLÓN" : integerToWords(millions) + " MILLONES").append(' ');
    }
    if (thousands > 0) {
      words.append(thousands == 1 ? "MIL" : chunkToWords((int) thousands) + " MIL").append(' ');
    }
    if (units > 0) {
      words.append(chunkToWords((int) units));
    }
    return words.toString().trim();
  }
}
