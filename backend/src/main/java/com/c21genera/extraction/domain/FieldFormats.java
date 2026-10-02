package com.c21genera.extraction.domain;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formatos oficiales de los identificadores que la IA transcribe. Un valor que
 * no cumple su formato es casi seguro una mala lectura (E2E 02/10: la clave de
 * elector de una INE fotografiada de lado salía con 16 caracteres o con una
 * letra cambiada): nunca debe presentarse como dato confiable.
 */
public final class FieldFormats {

  private FieldFormats() {}

  private static final Pattern CURP = Pattern.compile("^[A-Z][AEIOUX][A-Z]{2}\\d{6}[HMX][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9]\\d$");
  private static final Pattern RFC = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$");
  private static final Pattern ELECTOR_KEY = Pattern.compile("^[A-Z]{6}\\d{8}[HMX]\\d{3}$");

  /** Cómo debe verse cada identificador, para pedirle a la IA que lo relea. */
  public static final Map<String, String> DESCRIPTION =
      Map.of(
          "curp", "18 caracteres: 4 letras, 6 dígitos de fecha AAMMDD, H o M, 5 letras, 1 letra o dígito y 1 dígito verificador",
          "rfc", "12 (persona moral) o 13 (persona física) caracteres: 3 o 4 letras, 6 dígitos de fecha AAMMDD y 3 de homoclave",
          "electorKey", "18 caracteres: 6 letras, 8 dígitos (fecha AAMMDD y 2 de estado), H o M y 3 dígitos");

  /** Problema de formato del valor, o vacío si es válido o el campo no tiene formato conocido. */
  public static Optional<String> problem(String fieldName, String value) {
    if (value == null || !DESCRIPTION.containsKey(fieldName)) {
      return Optional.empty();
    }
    String v = normalize(value);
    boolean valid =
        switch (fieldName) {
          // Sin dígito verificador a propósito: un documento con un dígito distinto (o los fixtures de
          // QA, que son sintéticos) no debe hacer que la IA "corrija" un valor que leyó bien.
          case "curp" -> CURP.matcher(v).matches();
          case "rfc" -> RFC.matcher(v).matches();
          case "electorKey" -> ELECTOR_KEY.matcher(v).matches();
          default -> true;
        };
    return valid ? Optional.empty() : Optional.of("debe tener " + DESCRIPTION.get(fieldName) + "; se leyó \"" + value + "\" (" + v.length() + " caracteres)");
  }

  static String normalize(String value) {
    return value.toUpperCase().replaceAll("[\\s-]", "");
  }
}
