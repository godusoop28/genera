package com.c21genera.extraction.domain;

import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decide el valor de un dato difícil a partir de varias lecturas
 * independientes del mismo documento (original, versión mejorada, relectura
 * dirigida). Una lectura solo cuenta si es "limpia": sin "?" y, si el campo
 * tiene formato oficial (CURP, RFC, clave de elector), cumpliéndolo.
 *
 * <ul>
 *   <li>Dos o más lecturas limpias idénticas: consenso, confianza 0.9.
 *   <li>Lecturas limpias que no coinciden: se usa la de más confianza, pero con
 *       confianza máxima 0.5 y un aviso con todas las lecturas, para revisión.
 *   <li>Ninguna lectura limpia: no hay decisión (se conserva la original, que
 *       el guardia de formatos deja en confianza baja).
 * </ul>
 *
 * Nunca combina caracteres de lecturas distintas ni toma valores de otros
 * documentos: solo elige entre lo que se leyó en este.
 */
public final class ReadingConsensus {

  private ReadingConsensus() {}

  public static final double CONSENSUS_CONFIDENCE = 0.9;
  public static final double DISAGREEMENT_MAX_CONFIDENCE = 0.5;
  private static final double MIN_AGREEING_CONFIDENCE = 0.5;

  public record Decision(FieldResult field, boolean consensus, List<String> distinctReadings) {}

  public static Optional<Decision> decide(String fieldName, List<FieldResult> readings) {
    Map<String, List<FieldResult>> clean = new LinkedHashMap<>();
    List<String> distinct = new ArrayList<>();
    for (FieldResult r : readings) {
      if (r == null || r.value() == null || r.value().isBlank()) {
        continue;
      }
      String key = key(fieldName, r.value());
      if (!distinct.contains(key)) {
        distinct.add(key);
      }
      if (isClean(fieldName, r.value())) {
        clean.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
      }
    }
    if (clean.isEmpty()) {
      return Optional.empty();
    }
    // Dos lecturas en las que la propia IA dijo no estar segura (confianza < 0.5) no son un consenso.
    List<FieldResult> agreed =
        clean.values().stream()
            .filter(group -> group.size() >= 2 && group.stream().anyMatch(r -> r.confidence() >= MIN_AGREEING_CONFIDENCE))
            .max((a, b) -> Integer.compare(a.size(), b.size()))
            .orElse(null);
    if (agreed != null) {
      FieldResult first = agreed.getFirst();
      double confidence = Math.max(CONSENSUS_CONFIDENCE, agreed.stream().mapToDouble(FieldResult::confidence).max().orElse(0));
      return Optional.of(
          new Decision(new FieldResult(fieldName, key(fieldName, first.value()), Math.min(confidence, 0.95), first.page()), true, distinct));
    }
    FieldResult best =
        clean.values().stream().flatMap(List::stream).max((a, b) -> Double.compare(a.confidence(), b.confidence())).orElseThrow();
    return Optional.of(
        new Decision(
            new FieldResult(fieldName, best.value(), Math.min(best.confidence(), DISAGREEMENT_MAX_CONFIDENCE), best.page()), false, distinct));
  }

  /** Lectura sin caracteres dudosos y, si aplica, con el formato oficial. */
  public static boolean isClean(String fieldName, String value) {
    return value != null && !value.contains("?") && FieldFormats.problem(fieldName, value).isEmpty();
  }

  private static String key(String fieldName, String value) {
    return FieldFormats.hasFormat(fieldName) ? FieldFormats.normalizeIdentifier(value) : value.strip();
  }
}
