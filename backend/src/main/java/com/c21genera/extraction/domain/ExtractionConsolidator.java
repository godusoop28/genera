package com.c21genera.extraction.domain;

import com.c21genera.extraction.domain.StructuredExtractionProvider.ContentAssessment;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Junta los resultados de varios lotes de páginas de un mismo documento en
 * uno solo: por cada campo se queda con el valor de mayor confianza (ante
 * empate, el primero que apareció), conserva todos los datos extra y todas
 * las advertencias, y toma la identificación del documento del primer lote
 * que la dio. Determinístico, sin IA.
 */
public final class ExtractionConsolidator {

  /** Confianza a partir de la cual un campo ya no se sigue buscando en más páginas. */
  public static final double GOOD_CONFIDENCE = 0.75;

  private final Map<String, FieldResult> best = new LinkedHashMap<>();
  private final Set<String> warnings = new LinkedHashSet<>();
  private Boolean matchesExpectedType;
  private Boolean legible;
  private String detectedKind;
  private String observations;

  public void add(ExtractionResult batch) {
    for (FieldResult field : batch.fields()) {
      if (field.value() == null || field.value().isBlank()) {
        continue;
      }
      FieldResult current = best.get(field.fieldName());
      if (current == null || field.confidence() > current.confidence()) {
        best.put(field.fieldName(), field);
      }
    }
    warnings.addAll(batch.warnings());
    ContentAssessment a = batch.assessment();
    if (a != null) {
      if (detectedKind == null && a.detectedDocumentKind() != null) {
        detectedKind = a.detectedDocumentKind();
      }
      if (observations == null && a.observations() != null) {
        observations = a.observations();
      }
      if (matchesExpectedType == null) {
        matchesExpectedType = a.matchesExpectedType();
      } else if (Boolean.TRUE.equals(a.matchesExpectedType())) {
        // Basta con que un lote reconozca el documento (la portada de una escritura puede ser ambigua).
        matchesExpectedType = true;
      }
      // Legible si cualquier parte se pudo leer: un documento largo con una hoja manchada sigue siendo útil.
      if (Boolean.TRUE.equals(a.legible())) {
        legible = true;
      } else if (legible == null) {
        legible = a.legible();
      }
    }
  }

  /** Una relectura dirigida corrigió el campo: reemplaza el valor anterior aunque tuviera más confianza. */
  public void replace(FieldResult field) {
    best.put(field.fieldName(), field);
  }

  /** Lo que dijo la IA sobre la legibilidad (null si no lo dijo). */
  public Boolean legible() {
    return legible;
  }

  /** Una segunda lectura (versión mejorada) sí pudo leer el documento. */
  public void markLegible() {
    legible = true;
  }

  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /** Campos del esquema que todavía no se encontraron con buena confianza. */
  public List<String> pending(List<String> schemaFields) {
    return schemaFields.stream()
        .filter(f -> !best.containsKey(f) || best.get(f).confidence() < GOOD_CONFIDENCE)
        .toList();
  }

  /** Cuántos campos del esquema se encontraron (con cualquier confianza). */
  public int foundOf(List<String> schemaFields) {
    return (int) schemaFields.stream().filter(best::containsKey).count();
  }

  public ExtractionResult result(Integer pagesAnalyzed, Integer pagesTotal) {
    return new ExtractionResult(
        new ArrayList<>(best.values()),
        new ContentAssessment(matchesExpectedType, legible, detectedKind, observations),
        new ArrayList<>(warnings),
        pagesAnalyzed,
        pagesTotal);
  }
}
