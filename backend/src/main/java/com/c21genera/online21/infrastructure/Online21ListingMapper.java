package com.c21genera.online21.infrastructure;

import com.c21genera.online21.ListingAttributes;
import com.c21genera.online21.ListingAttributes.Attribute;
import com.c21genera.online21.PropertyListings.Listing;
import com.c21genera.online21.PropertyListings.ListingDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Traducción pura entre Genera y el esquema "Propiedades" de 21Online para
 * la pantalla interna. Al leer NO copia dueño (idClientesVendedor), clave
 * catastral ni datos de cierre. Al escribir manda los números como texto,
 * igual que los devuelve 21Online ("330.00"). Los atributos adicionales
 * (ver {@link ListingAttributes}) viajan con su misma clave de 21Online.
 */
final class Online21ListingMapper {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Campo de 21Online con las amenidades: {"campos":[{"nombre","label","tipo","required","valor"}]} como texto. */
  static final String FEATURES_FIELD = ListingAttributes.FEATURES_TEMPLATE;

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
    d.extras()
        .forEach(
            (key, value) -> {
              if (ListingAttributes.BY_KEY.containsKey(key)) {
                put(p, key, value);
              }
            });
    Object amenities = d.extras().get(ListingAttributes.AMENITIES_KEY);
    Object template = d.extras().get(FEATURES_FIELD);
    if (template instanceof String || (amenities instanceof Map<?, ?> m && !m.isEmpty())) {
      p.put(FEATURES_FIELD, featuresJson(template instanceof String s ? s : null, amenities instanceof Map<?, ?> m ? m : Map.of()));
    }
    return p;
  }

  /**
   * caracteristicasJSON para 21Online. Con template (el que trae 21Online: ~380 campos, muchos que Genera
   * no maneja, como acabados o alturas) solo cambia el valor de las casillas y del recorrido virtual; lo
   * demás viaja tal cual. Sin template (propiedad nueva) se arma con el catálogo de amenidades.
   */
  static String featuresJson(String template, Map<?, ?> amenities) {
    Map<String, Object> values = new TreeMap<>();
    amenities.forEach((k, v) -> values.put(String.valueOf(k), v));
    Object tour = values.get(ListingAttributes.VIRTUAL_TOUR);
    JsonNode root = null;
    if (template != null) {
      try {
        root = JSON.readTree(template);
      } catch (java.io.IOException e) {
        root = null;
      }
    }
    List<Object> fields = new ArrayList<>();
    java.util.Set<String> present = new java.util.HashSet<>();
    if (root != null && root.get("campos") != null && root.get("campos").isArray()) {
      for (JsonNode f : root.get("campos")) {
        Map<String, Object> copy = JSON.convertValue(f, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        String name = Online21PropertyMapper.text(f, "nombre");
        if (name != null) {
          present.add(name);
          if (ListingAttributes.VIRTUAL_TOUR.equals(name)) {
            copy.put("valor", tour == null ? "" : tour.toString());
          } else if ("checkbox".equals(Online21PropertyMapper.text(f, "tipo"))) {
            copy.put("valor", Boolean.TRUE.equals(values.get(name)));
          }
        }
        fields.add(copy);
      }
    }
    Map<String, String> labels = new LinkedHashMap<>();
    ListingAttributes.AMENITIES.forEach(a -> labels.put(a.key(), a.label()));
    for (String key : ListingAttributes.amenityOrder(values)) {
      if (!present.contains(key) && (root == null || Boolean.TRUE.equals(values.get(key)))) {
        fields.add(field(key, labels.getOrDefault(key, key), "checkbox", Boolean.TRUE.equals(values.get(key))));
      }
    }
    if (!present.contains(ListingAttributes.VIRTUAL_TOUR)) {
      fields.add(field(ListingAttributes.VIRTUAL_TOUR, "URL Recorrido virtual", "input", tour == null ? "" : tour.toString()));
    }
    try {
      return JSON.writeValueAsString(Map.of("campos", fields));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("No se pudieron armar las amenidades para 21Online", e);
    }
  }

  private static Map<String, Object> field(String name, String label, String type, Object value) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("nombre", name);
    f.put("label", label);
    f.put("tipo", type);
    f.put("required", false);
    f.put("valor", value);
    return f;
  }

  /**
   * Atributos adicionales de la ficha de 21Online en forma canónica. Los que 21Online no devuelve
   * (writeOnly) no vienen; un valor ilegible se ignora (no impide traer la propiedad).
   */
  static Map<String, Object> extras(JsonNode n) {
    Map<String, Object> raw = new LinkedHashMap<>();
    for (Attribute a : ListingAttributes.ATTRIBUTES) {
      JsonNode v = n.get(a.key());
      if (a.writeOnly() || v == null || v.isNull()) {
        continue;
      }
      raw.put(a.key(), v.isBoolean() ? v.asBoolean() : v.isNumber() ? v.numberValue().toString() : v.asText());
    }
    Map<String, Object> amenities = amenities(n.get(FEATURES_FIELD));
    if (!amenities.isEmpty()) {
      raw.put(ListingAttributes.AMENITIES_KEY, amenities);
    }
    Map<String, Object> out = new TreeMap<>();
    raw.forEach(
        (k, v) -> {
          try {
            out.putAll(ListingAttributes.normalize(Map.of(k, v)));
          } catch (IllegalArgumentException ignored) {
            // Un valor que 21Online guardó en otra forma no detiene la sincronización.
          }
        });
    // El caracteristicasJSON original se conserva como plantilla para no perder sus demás campos al editar.
    JsonNode features = n.get(FEATURES_FIELD);
    if (features != null && !features.isNull()) {
      out.put(FEATURES_FIELD, features.isTextual() ? features.asText() : features.toString());
    }
    return out;
  }

  /** nombre → valor de "caracteristicasJSON" (texto con JSON o ya como objeto). */
  static Map<String, Object> amenities(JsonNode features) {
    Map<String, Object> out = new TreeMap<>();
    if (features == null || features.isNull()) {
      return out;
    }
    JsonNode root = features;
    if (features.isTextual()) {
      try {
        root = JSON.readTree(features.asText());
      } catch (java.io.IOException e) {
        return out;
      }
    }
    JsonNode fields = root == null ? null : root.get("campos");
    if (fields == null || !fields.isArray()) {
      return out;
    }
    for (JsonNode f : fields) {
      String name = Online21PropertyMapper.text(f, "nombre");
      JsonNode value = f.get("valor");
      if (name == null || value == null || value.isNull()) {
        continue;
      }
      if (value.isBoolean()) {
        out.put(name, value.asBoolean());
      } else if (ListingAttributes.VIRTUAL_TOUR.equals(name)) {
        out.put(name, value.asText());
      }
    }
    return out;
  }

  /** URLs de las fotos en el orden de 21Online. */
  static List<String> photos(JsonNode n) {
    JsonNode photos = n.get("fotos");
    List<JsonNode> sorted = new ArrayList<>();
    if (photos != null && photos.isArray()) {
      photos.forEach(sorted::add);
    }
    sorted.sort(java.util.Comparator.comparingInt(p -> p.path("orden").asInt(Integer.MAX_VALUE)));
    List<String> out = new ArrayList<>();
    for (JsonNode photo : sorted) {
      String url = photo.isTextual() ? photo.asText() : Online21PropertyMapper.text(photo, "path");
      if (url != null && url.startsWith("http")) {
        out.add(url);
      }
    }
    return out;
  }

  /** Subtipos que en 21Online solo existen como comerciales (los ambiguos, como terreno, se toman como habitacionales). */
  private static final java.util.Set<String> COMMERCIAL_ONLY =
      java.util.Set.of("local", "oficinas", "bodega", "nave", "fabrica", "fraccionamiento", "escuela", "inmueble-productivo");

  /**
   * Ficha completa de 21Online como borrador editable en Genera (sin dueño, clave catastral ni datos
   * de cierre). La lectura de 21Online no trae tipoPropiedad: se deduce del subtipo. La comisión solo
   * se toma si es porcentaje (Genera la maneja así).
   */
  static ListingDraft toDraft(JsonNode n) {
    String subtype = Online21PropertyMapper.text(n, "subtipoPropiedad");
    String type = Online21PropertyMapper.text(n, "tipoPropiedad");
    if (type == null) {
      type = subtype != null && COMMERCIAL_ONLY.contains(subtype) ? "comercial" : "habitacional";
    }
    String commissionType = Online21PropertyMapper.text(n, "tipoComision");
    BigDecimal commission = commissionType == null || "porcentaje".equals(commissionType) ? Online21PropertyMapper.decimal(n, "comision") : null;
    String currency = Online21PropertyMapper.text(n, "moneda");
    return new ListingDraft(
        Online21PropertyMapper.text(n, "tipoOperacion"),
        type,
        subtype,
        Online21PropertyMapper.text(n, "tipoDeContrato"),
        Online21PropertyMapper.text(n, "status"),
        Online21PropertyMapper.text(n, "encabezado") != null ? Online21PropertyMapper.text(n, "encabezado") : title(n),
        Online21PropertyMapper.text(n, "descripcion"),
        Online21PropertyMapper.decimal(n, "precioVenta"),
        Online21PropertyMapper.decimal(n, "precioRenta"),
        currency != null ? currency : "MXN",
        Online21PropertyMapper.text(n, "calle"),
        Online21PropertyMapper.text(n, "numero"),
        Online21PropertyMapper.text(n, "colonia"),
        Online21PropertyMapper.text(n, "municipio"),
        Online21PropertyMapper.text(n, "estado"),
        Online21PropertyMapper.text(n, "cP"),
        flag(n, "ocultarCalleInternet"),
        flag(n, "ocultarNumeroInternet"),
        flag(n, "ocultarPrecioInternet"),
        Online21PropertyMapper.integer(n, "recamaras"),
        Online21PropertyMapper.decimal(n, "banios"),
        Online21PropertyMapper.integer(n, "mediosBanios"),
        Online21PropertyMapper.integer(n, "estacionamientos"),
        Online21PropertyMapper.decimal(n, "m2T"),
        Online21PropertyMapper.decimal(n, "m2C"),
        Online21PropertyMapper.integer(n, "nivelesConstruidos"),
        Online21PropertyMapper.integer(n, "edad"),
        Online21PropertyMapper.decimal(n, "cuotaMantenimiento"),
        commission,
        flag(n, "enInternet"),
        Online21PropertyMapper.advisorId(n),
        Online21PropertyMapper.text(n, "idIntegrador"),
        extras(n));
  }

  /**
   * Solo los campos que cambiaron entre lo último que se vio/mandó (baseline) y lo nuevo. Un campo que
   * se vació se manda como null. Así un PUT desde Genera nunca pisa lo que se editó en 21Online en otros
   * campos (ni lo que Genera no maneja: fotos, dueño, datos de cierre…).
   */
  static Map<String, Object> changes(ListingDraft baseline, ListingDraft draft) {
    Map<String, Object> before = toPayload(baseline);
    Map<String, Object> after = toPayload(draft);
    Map<String, Object> out = new LinkedHashMap<>();
    after.forEach(
        (k, v) -> {
          if (!java.util.Objects.equals(before.get(k), v)) {
            out.put(k, v);
          }
        });
    before.keySet().stream().filter(k -> !after.containsKey(k)).forEach(k -> out.put(k, null));
    // tipoComision acompaña a la comisión; idIntegrador no se cambia en una propiedad que ya existe.
    if (out.containsKey("comision") && out.get("comision") != null) {
      out.put("tipoComision", "porcentaje");
    } else {
      out.remove("tipoComision");
    }
    out.remove("idIntegrador");
    return out;
  }

  private static boolean flag(JsonNode n, String field) {
    return n.hasNonNull(field) && n.get(field).asBoolean();
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
