package com.c21genera.extraction.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detecta conflictos entre valores del MISMO nombre de campo declarados en
 * documentos distintos de un mismo expediente (ver AGENTS §41). Puramente
 * determinístico y sin IA: solo compara texto normalizado. Nunca decide cuál
 * valor es el correcto, solo lo reporta.
 */
public final class CrossDocumentValidator {

  private CrossDocumentValidator() {}

  public record FieldValue(String fieldName, String value, String documentLabel) {}

  public record DetectedConflict(String fieldName, String description) {}

  public static List<DetectedConflict> validate(List<FieldValue> values) {
    Map<String, List<FieldValue>> byField =
        values.stream()
            .filter(v -> v.value() != null && !v.value().isBlank())
            .collect(Collectors.groupingBy(FieldValue::fieldName));

    List<DetectedConflict> conflicts = new ArrayList<>();
    for (Map.Entry<String, List<FieldValue>> entry : byField.entrySet()) {
      List<FieldValue> group = entry.getValue();
      Set<String> normalizedValues = group.stream().map(v -> normalize(v.value())).collect(Collectors.toSet());
      if (normalizedValues.size() > 1) {
        String description =
            group.stream().map(v -> v.documentLabel() + "=\"" + v.value() + "\"").collect(Collectors.joining("; "));
        conflicts.add(new DetectedConflict(entry.getKey(), description));
      }
    }
    return conflicts;
  }

  private static String normalize(String value) {
    return value.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
