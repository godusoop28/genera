package com.c21genera.online21;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * API pública del módulo online21 para la pantalla de Propiedades del CRM:
 * consulta el inventario completo de la oficina en 21Online (cualquier
 * estatus, en vivo y paginado) y da de alta o actualiza propiedades allá
 * (POST/PUT /api/propiedades). 21Online sigue siendo la fuente de verdad del
 * inventario; Genera no guarda una copia de lo que no se capturó en Genera.
 *
 * <p>A diferencia de {@link PropertyCatalog} (lo que puede ver un cliente por
 * WhatsApp), esta vista es interna: incluye estatus, número exterior y
 * comisión, pero nunca dueño ni clave catastral.
 */
public interface PropertyListings {

  boolean available();

  /** Una página del inventario de 21Online con los filtros indicados. */
  ListingPage list(ListingFilter filter);

  /** Ficha de una propiedad de 21Online; vacío si no existe. */
  Optional<Listing> get(long id);

  /** Da de alta la propiedad en 21Online; devuelve su id. Lanza {@link PublishException} si 21Online la rechaza. */
  long create(ListingDraft draft);

  /** Actualiza en 21Online una propiedad que ya existe allá. */
  void update(long id, ListingDraft draft);

  record ListingFilter(String query, String status, String operation, String subtype, String municipality, int page) {}

  record ListingPage(List<Listing> items, int page, Integer totalItems, boolean hasNext) {}

  record Listing(
      long id,
      String key,
      String internalKey,
      String status,
      String operation,
      String propertyType,
      String subtype,
      String subtypeLabel,
      String contractType,
      String title,
      BigDecimal salePrice,
      BigDecimal rentPrice,
      String currency,
      String street,
      String number,
      String neighborhood,
      String municipality,
      String state,
      String postalCode,
      Integer bedrooms,
      BigDecimal bathrooms,
      Integer halfBathrooms,
      Integer parking,
      BigDecimal landM2,
      BigDecimal builtM2,
      String description,
      Boolean publishedOnline,
      Long advisorOnline21Id,
      String photoUrl,
      int photoCount,
      Instant createdAt,
      Instant updatedAt) {}

  /**
   * Lo que Genera manda a 21Online. Los códigos son los de su API
   * (tipoOperacion "venta"/"renta", subtipoPropiedad "casa", status
   * "enPromocion", …). pais es obligatorio para 21Online: siempre "México".
   */
  record ListingDraft(
      String operation,
      String propertyType,
      String subtype,
      String contractType,
      String status,
      String headline,
      String description,
      BigDecimal salePrice,
      BigDecimal rentPrice,
      String currency,
      String street,
      String number,
      String neighborhood,
      String municipality,
      String state,
      String postalCode,
      boolean hideStreet,
      boolean hideNumber,
      boolean hidePrice,
      Integer bedrooms,
      BigDecimal bathrooms,
      Integer halfBathrooms,
      Integer parking,
      BigDecimal landM2,
      BigDecimal builtM2,
      Integer levels,
      Integer age,
      BigDecimal maintenanceFee,
      BigDecimal commission,
      boolean publishOnline,
      Long advisorOnline21Id,
      String integratorId) {}

  /**
   * 21Online rechazó la propiedad o no respondió. {@code message} es apto
   * para mostrarse al usuario (validaciones de 21Online o un texto genérico);
   * {@code uncertain} indica que pudo haberse creado aunque no hubo respuesta
   * (tiempo agotado), así que hay que revisar 21Online antes de reintentar.
   */
  class PublishException extends RuntimeException {

    private final boolean uncertain;

    public PublishException(String message, boolean uncertain) {
      super(message);
      this.uncertain = uncertain;
    }

    public boolean uncertain() {
      return uncertain;
    }
  }
}
