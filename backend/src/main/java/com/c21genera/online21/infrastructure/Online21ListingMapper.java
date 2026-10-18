package com.c21genera.online21.infrastructure;

import com.c21genera.online21.PropertyListings.Listing;
import com.c21genera.online21.PropertyListings.ListingDraft;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traducción pura entre Genera y el esquema "Propiedades" de 21Online para
 * la pantalla interna. Al leer NO copia dueño (idClientesVendedor), clave
 * catastral ni datos de cierre. Al escribir manda los números como texto,
 * igual que los devuelve 21Online ("330.00").
 */
final class Online21ListingMapper {

  private Online21ListingMapper() {}

  static Listing toListing(JsonNode n) {
    long id = n.path("id").asLong();
    JsonNode photos = n.get("fotos");
    int photoCount = photos != null && photos.isArray() ? photos.size() : 0;
    return new Listing(
        id,
        id + "--" + office(n),
        Online21PropertyMapper.text(n, "clave"),
        Online21PropertyMapper.text(n, "status"),
        Online21PropertyMapper.text(n, "tipoOperacion"),
        Online21PropertyMapper.text(n, "tipoPropiedad"),
        Online21PropertyMapper.text(n, "subtipoPropiedad"),
        Online21PropertyMapper.subtypeLabel(Online21PropertyMapper.text(n, "subtipoPropiedad")),
        Online21PropertyMapper.text(n, "tipoDeContrato"),
        title(n),
        Online21PropertyMapper.decimal(n, "precioVenta"),
        Online21PropertyMapper.decimal(n, "precioRenta"),
        Online21PropertyMapper.text(n, "moneda") != null ? Online21PropertyMapper.text(n, "moneda") : "MXN",
        Online21PropertyMapper.text(n, "calle"),
        Online21PropertyMapper.text(n, "numero"),
        Online21PropertyMapper.text(n, "colonia"),
        Online21PropertyMapper.text(n, "municipio"),
        Online21PropertyMapper.text(n, "estado"),
        Online21PropertyMapper.text(n, "cP"),
        Online21PropertyMapper.integer(n, "recamaras"),
        Online21PropertyMapper.decimal(n, "banios"),
        Online21PropertyMapper.integer(n, "mediosBanios"),
        Online21PropertyMapper.integer(n, "estacionamientos"),
        Online21PropertyMapper.decimal(n, "m2T"),
        Online21PropertyMapper.decimal(n, "m2C"),
        truncate(Online21PropertyMapper.text(n, "descripcion"), 4000),
        n.hasNonNull("enInternet") ? n.get("enInternet").asBoolean() : null,
        Online21PropertyMapper.advisorId(n),
        firstPhoto(photos),
        photoCount,
        instant(n, "fechaAlta"),
        instant(n, "fechaModificacion"));
  }

  static Map<String, Object> toPayload(ListingDraft d) {
    Map<String, Object> p = new LinkedHashMap<>();
    put(p, "tipoOperacion", d.operation());
    put(p, "tipoPropiedad", d.propertyType());
    put(p, "subtipoPropiedad", d.subtype());
    put(p, "tipoDeContrato", d.contractType());
    put(p, "status", d.status() != null ? d.status() : "enPromocion");
    put(p, "encabezado", d.headline());
    put(p, "descripcion", d.description());
    boolean rent = "renta".equals(d.operation());
    put(p, rent ? "precioRenta" : "precioVenta", number(rent ? d.rentPrice() : d.salePrice()));
    put(p, "moneda", d.currency() != null ? d.currency() : "MXN");
    p.put("ocultarPrecioInternet", d.hidePrice());
    put(p, "calle", d.street());
    put(p, "numero", d.number());
    put(p, "colonia", d.neighborhood());
    put(p, "municipio", d.municipality());
    put(p, "estado", d.state());
    p.put("pais", "México");
    put(p, "cP", d.postalCode());
    p.put("ocultarCalleInternet", d.hideStreet());
    p.put("ocultarNumeroInternet", d.hideNumber());
    put(p, "recamaras", number(d.bedrooms()));
    put(p, "banios", number(d.bathrooms()));
    put(p, "mediosBanios", number(d.halfBathrooms()));
    put(p, "estacionamientos", number(d.parking()));
    put(p, "m2T", number(d.landM2()));
    put(p, "m2C", number(d.builtM2()));
    put(p, "nivelesConstruidos", number(d.levels()));
    put(p, "edad", number(d.age()));
    put(p, "cuotaMantenimiento", number(d.maintenanceFee()));
    if (d.commission() != null) {
      put(p, "comision", number(d.commission()));
      p.put("tipoComision", "porcentaje");
    }
    p.put("enInternet", d.publishOnline());
    if (d.advisorOnline21Id() != null) {
      p.put("usuarios", "/api/usuarios/" + d.advisorOnline21Id());
    }
    put(p, "idIntegrador", d.integratorId());
    return p;
  }

  /** Mensaje legible de una respuesta 422 de API Platform ({"violations":[{"propertyPath","message"}]}). */
  static String violations(JsonNode body) {
    if (body == null) {
      return null;
    }
    JsonNode list = body.get("violations");
    if (list != null && list.isArray() && !list.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (JsonNode v : list) {
        if (!sb.isEmpty()) {
          sb.append("; ");
        }
        String path = v.path("propertyPath").asText("");
        sb.append(path.isEmpty() ? "" : path + ": ").append(v.path("message").asText(""));
      }
      return truncate(sb.toString(), 480);
    }
    String detail = Online21PropertyMapper.text(body, "hydra:description");
    if (detail == null) {
      detail = Online21PropertyMapper.text(body, "detail");
    }
    return detail != null ? truncate(detail, 480) : null;
  }

  private static String title(JsonNode n) {
    String headline = Online21PropertyMapper.text(n, "encabezado");
    if (headline != null) {
      return truncate(headline, 200);
    }
    String operation = Online21PropertyMapper.text(n, "tipoOperacion");
    String neighborhood = Online21PropertyMapper.text(n, "colonia");
    return Online21PropertyMapper.subtypeLabel(Online21PropertyMapper.text(n, "subtipoPropiedad"))
        + " en "
        + (operation != null ? operation : "venta")
        + (neighborhood != null ? " en " + neighborhood : "");
  }

  private static String firstPhoto(JsonNode photos) {
    if (photos == null || !photos.isArray()) {
      return null;
    }
    for (JsonNode photo : photos) {
      String url = photo.isTextual() ? photo.asText() : Online21PropertyMapper.text(photo, "path");
      if (url != null && url.startsWith("http")) {
        return url;
      }
    }
    return null;
  }

  private static String office(JsonNode n) {
    if (n.hasNonNull("idAfiliados")) {
      return n.get("idAfiliados").asText();
    }
    String iri = Online21PropertyMapper.text(n, "afiliados");
    return iri != null ? iri.replaceAll(".*/", "") : "";
  }

  private static String number(Number value) {
    if (value == null) {
      return null;
    }
    BigDecimal d = value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    return d.stripTrailingZeros().toPlainString();
  }

  private static void put(Map<String, Object> p, String key, Object value) {
    if (value instanceof String s && s.isBlank()) {
      return;
    }
    if (value != null) {
      p.put(key, value);
    }
  }

  private static Instant instant(JsonNode n, String field) {
    String s = Online21PropertyMapper.text(n, field);
    if (s == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String truncate(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }
}
