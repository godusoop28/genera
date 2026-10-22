package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.OperationType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Control de Cambios #27: clave estructurada del inmueble = tipo + operación + iniciales del
 * asesor + consecutivo (p. ej. CVEA03: Casa, Venta, E. A., tercera de esa combinación).
 *
 * <p>prefix es la combinación (todas las letras) y number el consecutivo (los dígitos finales). Se
 * normaliza a mayúsculas, sin espacios ni guiones, con el consecutivo a dos dígitos como mínimo:
 * "cvea-3" y "CVEA03" son la misma clave.
 */
public record PropertyKey(String prefix, int number) {

  public static final int MAX_NUMBER = 9999;

  private static final Set<String> NAME_PARTICLES = Set.of("DE", "DEL", "LA", "LAS", "LOS", "Y", "VON", "VAN", "MC");

  public PropertyKey {
    if (prefix == null || !prefix.matches("[A-Z]{2,18}")) {
      throw new IllegalArgumentException("prefix");
    }
    if (number < 1 || number > MAX_NUMBER) {
      throw new IllegalArgumentException("number");
    }
  }

  /** Vacío si el texto no tiene la forma letras + consecutivo (p. ej. "CVEA03"). */
  public static Optional<PropertyKey> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String clean = raw.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_.]", "");
    var matcher = java.util.regex.Pattern.compile("([A-Z]{2,18})(\\d{1,4})").matcher(clean);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    int number = Integer.parseInt(matcher.group(2));
    if (number < 1) {
      return Optional.empty();
    }
    return Optional.of(new PropertyKey(matcher.group(1), number));
  }

  public String value() {
    return prefix + String.format("%02d", number);
  }

  /** Combinación tipo + operación + asesor, p. ej. "CVEA"; vacío si falta alguna de las partes. */
  public static Optional<String> prefixFor(String typeKeyCode, OperationType operation, String advisorName) {
    if (typeKeyCode == null || typeKeyCode.isBlank() || operation == null) {
      return Optional.empty();
    }
    String initials = initialsOf(advisorName);
    if (initials.length() < 2) {
      return Optional.empty();
    }
    return Optional.of(typeKeyCode.strip().toUpperCase(Locale.ROOT) + operationLetter(operation) + initials);
  }

  public static String operationLetter(OperationType operation) {
    return operation == OperationType.RENT ? "R" : "V";
  }

  /**
   * Iniciales del asesor: primer nombre + primer apellido. Con 2 palabras ("Ana López") o 3
   * ("Juan Pérez López", lo más común) el apellido es la segunda; con 4 o más ("Juan Carlos Pérez
   * López") es la penúltima. Se ignoran partículas ("de", "del", "la"...) y acentos.
   */
  public static String initialsOf(String fullName) {
    if (fullName == null) {
      return "";
    }
    String plain = Normalizer.normalize(fullName, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    List<String> words = new ArrayList<>();
    for (String w : plain.split("[^A-Z]+")) {
      if (!w.isEmpty() && !NAME_PARTICLES.contains(w)) {
        words.add(w);
      }
    }
    if (words.size() < 2) {
      return words.isEmpty() ? "" : words.get(0).substring(0, Math.min(2, words.get(0).length()));
    }
    String surname = words.size() >= 4 ? words.get(words.size() - 2) : words.get(1);
    return "" + words.get(0).charAt(0) + surname.charAt(0);
  }
}
