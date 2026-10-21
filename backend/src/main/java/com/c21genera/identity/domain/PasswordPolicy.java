package com.c21genera.identity.domain;

import com.c21genera.shared.domain.UnprocessableException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Control de Cambios #25: política de contraseñas (criterio NIST SP 800-63B).
 * Largo mínimo y se rechazan las contraseñas obvias; no se exigen mayúsculas o
 * símbolos ni se obliga a cambiarla cada cierto tiempo, porque eso solo lleva
 * a contraseñas más predecibles.
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 10;
  public static final int MAX_LENGTH = 128;

  /** Las más usadas (listas públicas de contraseñas filtradas) y las del propio negocio. */
  private static final Set<String> COMMON =
      Set.of(
          "1234567890", "0123456789", "12345678910", "123456789a", "1234567890a", "qwertyuiop", "qwerty1234", "qwerty12345", "1q2w3e4r5t",
          "password", "password1", "password12", "password123", "passw0rd123", "contrasena", "contrasena1", "contrasena123", "micontrasena",
          "iloveyou12", "abcdefghij", "abc1234567", "abcd123456", "aaaaaaaaaa", "1111111111", "0000000000", "9876543210", "administrador",
          "admin12345", "admin123456", "administrator", "bienvenido", "bienvenido1", "bienvenido123", "temporal123", "cambiame123", "teamo12345",
          "century21", "century2121", "century21genera", "c21genera", "c21genera123", "genera2026", "genera12345", "inmobiliaria",
          "inmobiliaria1", "asesor12345", "mexico1234", "mexico12345", "guadalajara", "jalisco123", "monterrey1", "superman12", "futbol1234");

  private PasswordPolicy() {}

  /** Lanza 422 con un mensaje para la persona si la contraseña no se acepta. */
  public static void validate(String raw, String email, String name) {
    if (raw == null || raw.length() < MIN_LENGTH) {
      throw weak("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres. Una frase corta es fácil de recordar y difícil de adivinar.");
    }
    if (raw.length() > MAX_LENGTH) {
      throw weak("La contraseña no puede tener más de " + MAX_LENGTH + " caracteres.");
    }
    if (raw.isBlank()) {
      throw weak("La contraseña no puede ser solo espacios.");
    }
    String normalized = normalize(raw);
    if (COMMON.contains(normalized) || normalized.chars().distinct().count() <= 2) {
      throw weak("Esa contraseña es demasiado común o fácil de adivinar. Elige otra.");
    }
    if (email != null) {
      String localPart = normalize(email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
      if (localPart.length() >= 4 && normalized.contains(localPart)) {
        throw weak("La contraseña no debe contener tu correo.");
      }
    }
    if (name != null) {
      // Solo el nombre (o un nombre seguido de números, p. ej. "rodrigo2026") se adivina fácil.
      String compact = normalized.replace(" ", "");
      String withoutDigits = compact.replaceAll("\\d+$", "");
      String fullName = normalize(name).replace(" ", "");
      for (String word : normalize(name).split("\\s+")) {
        if (compact.equals(fullName) || (word.length() >= 4 && (withoutDigits.equals(word) || withoutDigits.equals(fullName)))) {
          throw weak("La contraseña no debe ser tu nombre.");
        }
      }
    }
  }

  private static String normalize(String value) {
    String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return noAccents.toLowerCase(Locale.ROOT).strip();
  }

  private static UnprocessableException weak(String message) {
    return new UnprocessableException("WEAK_PASSWORD", message);
  }
}
