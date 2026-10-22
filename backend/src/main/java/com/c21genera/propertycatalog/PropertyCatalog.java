package com.c21genera.propertycatalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública del catálogo de inmuebles (Control de Cambios #26): categorías y
 * subcategorías que el administrador mantiene sin cambios de código. Lo usan
 * el alta de expediente y el alta de propiedades del CRM.
 *
 * <p>caseType es el tratamiento que reciben los documentos y el contrato
 * (HOUSING, DEPARTMENT, RESIDENTIAL_LAND o COMMERCIAL, ver
 * expedientes.PropertyCaseType); se guarda como texto para que este módulo no
 * dependa de expedientes.
 */
public interface PropertyCatalog {

  /** Subcategoría (activa o no: las inactivas siguen sirviendo para mostrar registros históricos). */
  Optional<Subtype> subtype(UUID subtypeId);

  Optional<Category> category(UUID categoryId);

  /** Todo el catálogo en orden de menú, incluidas las inactivas. */
  List<Category> all();

  record Category(UUID id, String name, int sortOrder, boolean active, List<Subtype> subtypes) {}

  record Subtype(
      UUID id,
      UUID categoryId,
      String categoryName,
      String name,
      int sortOrder,
      boolean active,
      boolean requiresDetail,
      String caseType,
      String online21Type,
      String online21Subtype,
      // Control de Cambios #27: letras del tipo en la clave estructurada (null = sin clave automática).
      String keyCode) {

    /** "Habitacional → Casa en condominio" (o "Especiales → Otro: <detalle>"). */
    public String label(String detail) {
      String base = categoryName + " → " + name;
      return requiresDetail && detail != null && !detail.isBlank() ? categoryName + " → " + name.replaceAll("\s*—.*$", "") + ": " + detail.strip() : base;
    }
  }
}
