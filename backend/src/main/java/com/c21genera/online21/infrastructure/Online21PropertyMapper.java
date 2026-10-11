package com.c21genera.online21.infrastructure;

import com.c21genera.online21.PropertyCatalog.CatalogProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte el JSON de 21Online en {@link CatalogProperty}. Puro y tolerante:
 * 21Online manda muchos números como texto ("4", "330.00") y campos nulos.
 * Deliberadamente NO copia dueño, número exterior, clave catastral ni datos
 * de cierre: lo que sale de aquí puede llegar al cliente por WhatsApp.
 */
final class Online21PropertyMapper {

  private static final Map<String, String> SUBTYPE_LABELS =
      Map.ofEntries(
          Map.entry("casa", "Casa"),
          Map.entry("casa-duplex", "Casa dúplex"),
          Map.entry("cabana", "Cabaña"),
          Map.entry("casa-en-condominio", "Casa en condominio"),
          Map.entry("departamento", "Departamento"),
          Map.entry("penthouse", "Penthouse"),
          Map.entry("terreno", "Terreno"),
          Map.entry("local", "Local"),
          Map.entry("oficina", "Oficina"),
          Map.entry("bodega", "Bodega"),
          Map.entry("nave", "Nave industrial"),
          Map.entry("quinta", "Quinta"),
          Map.entry("rancho", "Rancho"),
          Map.entry("edificio", "Edificio"));

  private Online21PropertyMapper() {}

  static CatalogProperty map(JsonNode n, String publicUrlTemplate) {
    long id = n.path("id").asLong();
    String operation = text(n, "tipoOperacion");
    String subtype = subtypeLabel(text(n, "subtipoPropiedad"));
    boolean rent = "renta".equalsIgnoreCase(operation);
    BigDecimal price = bool(n, "ocultarPrecioInternet") ? null : decimal(n, rent ? "precioRenta" : "precioVenta");
    String neighborhood = text(n, "colonia");
    String headline = text(n, "encabezado");
    String title =
        headline != null
            ? headline
            : subtype + " en " + (operation != null ? operation : "venta") + (neighborhood != null ? " en " + neighborhood : "");
    String publicUrl =
        publicUrlTemplate == null || publicUrlTemplate.isBlank() ? null : publicUrlTemplate.replace("{id}", Long.toString(id));
    return new CatalogProperty(
        id,
        id + "--" + officeSuffix(n),
        text(n, "clave"),
        truncate(title, 160),
        operation,
        subtype,
        price,
        text(n, "moneda") != null ? text(n, "moneda") : "MXN",
        bool(n, "ocultarCalleInternet") ? null : text(n, "calle"),
        neighborhood,
        text(n, "municipio"),
        text(n, "estado"),
        integer(n, "recamaras"),
        decimal(n, "banios"),
        integer(n, "estacionamientos"),
        decimal(n, "m2T"),
        decimal(n, "m2C"),
        truncate(text(n, "descripcion"), 1200),
        advisorId(n),
        publicUrl,
        instant(n, "fechaModificacion"));
  }

  static String subtypeLabel(String code) {
    if (code == null) {
      return "Propiedad";
    }
    return SUBTYPE_LABELS.getOrDefault(code.toLowerCase(Locale.ROOT), Character.toUpperCase(code.charAt(0)) + code.substring(1).replace('-', ' '));
  }

  /** El asesor de la propiedad: idAsesorExclusiva o, si no viene, el IRI de "usuarios" (/api/usuarios/41388). */
  static Long advisorId(JsonNode n) {
    if (n.hasNonNull("idAsesorExclusiva") && n.get("idAsesorExclusiva").asLong() > 0) {
      return n.get("idAsesorExclusiva").asLong();
    }
    String iri = text(n, "usuarios");
    if (iri != null) {
      String digits = iri.replaceAll(".*/", "");
      if (digits.matches("\\d+")) {
        return Long.parseLong(digits);
      }
    }
    return null;
  }

  private static String officeSuffix(JsonNode n) {
    String afiliado = n.hasNonNull("idAfiliados") ? n.get("idAfiliados").asText() : null;
    if (afiliado == null) {
      String iri = text(n, "afiliados");
      afiliado = iri != null ? iri.replaceAll(".*/", "") : "";
    }
    return afiliado;
  }

  static String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    String s = v.isTextual() ? v.asText() : v.toString();
    s = s.trim();
    return s.isEmpty() ? null : s;
  }

  static BigDecimal decimal(JsonNode n, String field) {
    String s = text(n, field);
    if (s == null) {
      return null;
    }
    try {
      BigDecimal value = new BigDecimal(s.replace(",", "").replace("$", "").trim());
      return value.signum() <= 0 ? null : value.stripTrailingZeros();
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static Integer integer(JsonNode n, String field) {
    BigDecimal d = decimal(n, field);
    return d == null ? null : d.intValue();
  }

  private static boolean bool(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v != null && (v.asBoolean(false) || "1".equals(v.asText()));
  }

  private static Instant instant(JsonNode n, String field) {
    String s = text(n, field);
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
