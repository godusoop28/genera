package com.c21genera.expedientes.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Llave para reconocer al mismo cliente aunque su nombre se haya capturado
 * distinto: sin acentos, mayúsculas, puntuación ni orden de las palabras
 * ("Pérez López, Juan" = "juan perez lopez"; "Inmobiliaria Sol, S.A. de C.V."
 * = "INMOBILIARIA SOL SA DE CV").
 */
public final class ClientNameKey {

  private ClientNameKey() {}

  public static String of(String fullName) {
    if (fullName == null) {
      return "";
    }
    String plain = Normalizer.normalize(fullName, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    // "S.A." -> "sa": los puntos de las abreviaturas no separan palabras.
    plain = plain.replace(".", "").replaceAll("[^a-z0-9]+", " ").strip();
    if (plain.isEmpty()) {
      return "";
    }
    return Arrays.stream(plain.split(" ")).sorted().collect(Collectors.joining(" "));
  }
}
