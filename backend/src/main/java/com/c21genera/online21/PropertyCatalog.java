package com.c21genera.online21;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * API pública del módulo online21: consulta de solo lectura del inventario
 * de 21Online (plus.21onlinemx.com). La usan el asistente de WhatsApp y los
 * leads para identificar propiedades y saber de qué asesor son.
 *
 * <p>No hay pantalla de propiedades en Genera (eso es el Módulo 2): aquí solo
 * se consulta. Si la integración no está configurada, {@link #available()}
 * es false y las búsquedas responden {@link Status#UNAVAILABLE} (nunca "no
 * existe"), para que el bot no afirme que una propiedad no está.
 */
public interface PropertyCatalog {

  boolean available();

  /** Busca por texto libre (colonia, municipio, tipo, clave) entre las propiedades en promoción. */
  SearchResult search(String query, Operation operation, int limit);

  /** Busca una propiedad por id, clave pública ("634294--649") o clave interna ("GN 14"). */
  Lookup find(String reference);

  /**
   * Ficha desde el índice en memoria, SIN llamar a 21Online (para armar avisos
   * dentro de una transacción sin bloquearla por la red). Vacío si no está.
   */
  Optional<CatalogProperty> cached(long id);

  /** Extrae referencias de propiedad mencionadas en un texto (ids de 6+ dígitos, claves con "--"). */
  List<String> referencesIn(String text);

  enum Operation {
    SALE,
    RENT,
    ANY
  }

  enum Status {
    OK,
    NOT_FOUND,
    UNAVAILABLE
  }

  record SearchResult(Status status, int inventorySize, List<CatalogProperty> candidates) {}

  record Lookup(Status status, Optional<CatalogProperty> property) {

    public static Lookup unavailable() {
      return new Lookup(Status.UNAVAILABLE, Optional.empty());
    }

    public static Lookup notFound() {
      return new Lookup(Status.NOT_FOUND, Optional.empty());
    }

    public static Lookup of(CatalogProperty property) {
      return new Lookup(Status.OK, Optional.of(property));
    }
  }

  /**
   * Vista de una propiedad apta para mostrarse a un cliente: nunca incluye
   * dueño, número exterior ni clave catastral; la calle solo si 21Online no
   * la marca como oculta, y el precio solo si no está oculto.
   */
  record CatalogProperty(
      long id,
      String key,
      String internalKey,
      String title,
      String operation,
      String subtype,
      BigDecimal price,
      String currency,
      String street,
      String neighborhood,
      String municipality,
      String state,
      Integer bedrooms,
      BigDecimal bathrooms,
      Integer parking,
      BigDecimal landM2,
      BigDecimal builtM2,
      String description,
      Long advisorOnline21Id,
      String publicUrl,
      Instant updatedAt) {

    /** Texto corto para el asesor y para guardar en el lead. */
    public String shortTitle() {
      return (internalKey != null && !internalKey.isBlank() ? internalKey + " · " : "") + title;
    }
  }
}
