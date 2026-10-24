package com.c21genera.feasibility.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que la IA entrega en la etapa 2 (prompt "Presentación de factibilidad"): el contenido de
 * las 12 láminas para el propietario. Montos en pesos (MXN) enteros; null = no se pudo confirmar.
 * {@link #SCHEMA} es el JSON Schema estricto que se le exige a la respuesta.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PresentationData(
    String executiveSummary,
    PropertyContext property,
    List<Point> strengths,
    List<Point> opportunities,
    MarketSupply marketSupply,
    List<ComparableListing> comparables,
    GroupNotes groupNotes,
    Pricing pricing,
    SalesStrategy sales,
    String mapConclusion,
    Conclusions conclusions,
    List<String> pendingItems) {

  public static final String ABOVE = "ARRIBA";
  public static final String SIMILAR = "SIMILAR";
  public static final String BELOW = "DEBAJO";

  public static final String EXACT = "EXACTA";
  public static final String APPROXIMATE = "APROXIMADA";
  public static final String NO_LOCATION = "SIN_UBICACION";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PropertyContext(
      String title,
      String location,
      String surfaces,
      String distribution,
      String condition,
      List<String> amenities,
      List<String> highlights,
      Double latitude,
      Double longitude,
      String locationPrecision) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Point(String title, String buyerPerception) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MarketSupply(
      Integer uniqueListings,
      Long minPrice,
      Long maxPrice,
      Long averagePrice,
      Long medianPrice,
      String concentration,
      String ourPosition,
      String scopeNote) {}

  /** group: ARRIBA, SIMILAR o DEBAJO. locationPrecision: EXACTA, APROXIMADA o SIN_UBICACION. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ComparableListing(
      String id,
      String group,
      String zone,
      String address,
      Long price,
      Double landM2,
      Double builtM2,
      String condition,
      String attributes,
      String url,
      boolean urlVerified,
      Double latitude,
      Double longitude,
      String locationPrecision,
      String source,
      String notes) {

    public ComparableListing withId(String newId) {
      return new ComparableListing(
          newId, group, zone, address, price, landM2, builtM2, condition, attributes, url, urlVerified, latitude, longitude, locationPrecision, source, notes);
    }

    public ComparableListing withLocation(Double lat, Double lng, String precision) {
      return new ComparableListing(id, group, zone, address, price, landM2, builtM2, condition, attributes, url, urlVerified, lat, lng, precision, source, notes);
    }

    public boolean located() {
      return latitude != null && longitude != null && !NO_LOCATION.equals(locationPrecision);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GroupNote(String advantages, String disadvantages) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GroupNotes(GroupNote above, GroupNote similar, GroupNote below) {}

  /** classification: ALINEADO, LIMITE_SUPERIOR o FUERA_DE_RANGO (lectura del Analista 3.0). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Pricing(
      Long ownerPrice, Long rangeMin, Long rangeMax, Long recommendedPrice, String classification, String strategy, List<String> justification) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SalesStrategy(
      String positioning,
      String targetBuyer,
      List<String> attributes,
      List<String> promotion,
      String mainMessage,
      String shortDescription,
      String mediumDescription,
      String fullDescription) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Conclusions(List<String> findings, List<String> nextSteps, String invitation) {}

  /**
   * Los comparables en el orden de las láminas (arriba, similares, debajo), renumerados C1..Cn
   * para que tablas, gráfica y mapa usen el mismo número.
   */
  public PresentationData normalized() {
    List<ComparableListing> ordered = new ArrayList<>();
    for (String group : List.of(ABOVE, SIMILAR, BELOW)) {
      safe(comparables).stream().filter(c -> group.equals(c.group())).limit(5).forEach(ordered::add);
    }
    List<ComparableListing> renumbered = new ArrayList<>();
    for (int i = 0; i < ordered.size(); i++) {
      renumbered.add(ordered.get(i).withId("C" + (i + 1)));
    }
    return withComparables(renumbered);
  }

  public PresentationData withComparables(List<ComparableListing> list) {
    return new PresentationData(
        executiveSummary, property, strengths, opportunities, marketSupply, list, groupNotes, pricing, sales, mapConclusion, conclusions, pendingItems);
  }

  public PresentationData withProperty(PropertyContext p) {
    return new PresentationData(
        executiveSummary, p, strengths, opportunities, marketSupply, comparables, groupNotes, pricing, sales, mapConclusion, conclusions, pendingItems);
  }

  public List<ComparableListing> group(String group) {
    return safe(comparables).stream().filter(c -> group.equals(c.group())).toList();
  }

  public static <T> List<T> safe(List<T> list) {
    return list == null ? List.of() : list;
  }

  // ---------------------------------------------------------------- JSON Schema (modo estricto)

  public static final Map<String, Object> SCHEMA = buildSchema();

  private static Map<String, Object> buildSchema() {
    Map<String, Object> point = object(Map.of("title", str(), "buyerPerception", str()));
    Map<String, Object> groupNote = object(Map.of("advantages", str(), "disadvantages", str()));
    Map<String, Object> location = enumOf(EXACT, APPROXIMATE, NO_LOCATION);

    Map<String, Object> property = new LinkedHashMap<>();
    property.put("title", str());
    property.put("location", str());
    property.put("surfaces", str());
    property.put("distribution", str());
    property.put("condition", str());
    property.put("amenities", array(str()));
    property.put("highlights", array(str()));
    property.put("latitude", nullable("number"));
    property.put("longitude", nullable("number"));
    property.put("locationPrecision", location);

    Map<String, Object> supply = new LinkedHashMap<>();
    supply.put("uniqueListings", nullable("integer"));
    supply.put("minPrice", nullable("integer"));
    supply.put("maxPrice", nullable("integer"));
    supply.put("averagePrice", nullable("integer"));
    supply.put("medianPrice", nullable("integer"));
    supply.put("concentration", str());
    supply.put("ourPosition", str());
    supply.put("scopeNote", str());

    Map<String, Object> comparable = new LinkedHashMap<>();
    comparable.put("id", str());
    comparable.put("group", enumOf(ABOVE, SIMILAR, BELOW));
    comparable.put("zone", str());
    comparable.put("address", nullable("string"));
    comparable.put("price", nullable("integer"));
    comparable.put("landM2", nullable("number"));
    comparable.put("builtM2", nullable("number"));
    comparable.put("condition", nullable("string"));
    comparable.put("attributes", str());
    comparable.put("url", nullable("string"));
    comparable.put("urlVerified", Map.of("type", "boolean"));
    comparable.put("latitude", nullable("number"));
    comparable.put("longitude", nullable("number"));
    comparable.put("locationPrecision", location);
    comparable.put("source", str());
    comparable.put("notes", str());

    Map<String, Object> pricing = new LinkedHashMap<>();
    pricing.put("ownerPrice", nullable("integer"));
    pricing.put("rangeMin", nullable("integer"));
    pricing.put("rangeMax", nullable("integer"));
    pricing.put("recommendedPrice", nullable("integer"));
    pricing.put("classification", enumOf("ALINEADO", "LIMITE_SUPERIOR", "FUERA_DE_RANGO", "SIN_DATOS"));
    pricing.put("strategy", str());
    pricing.put("justification", array(str()));

    Map<String, Object> sales = new LinkedHashMap<>();
    sales.put("positioning", str());
    sales.put("targetBuyer", str());
    sales.put("attributes", array(str()));
    sales.put("promotion", array(str()));
    sales.put("mainMessage", str());
    sales.put("shortDescription", str());
    sales.put("mediumDescription", str());
    sales.put("fullDescription", str());

    Map<String, Object> conclusions = new LinkedHashMap<>();
    conclusions.put("findings", array(str()));
    conclusions.put("nextSteps", array(str()));
    conclusions.put("invitation", str());

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("executiveSummary", str());
    root.put("property", object(property));
    root.put("strengths", array(point));
    root.put("opportunities", array(point));
    root.put("marketSupply", object(supply));
    root.put("comparables", array(object(comparable)));
    root.put("groupNotes", object(Map.of("above", groupNote, "similar", groupNote, "below", groupNote)));
    root.put("pricing", object(pricing));
    root.put("sales", object(sales));
    root.put("mapConclusion", str());
    root.put("conclusions", object(conclusions));
    root.put("pendingItems", array(str()));
    return object(root);
  }

  private static Map<String, Object> object(Map<String, Object> properties) {
    Map<String, Object> o = new LinkedHashMap<>();
    o.put("type", "object");
    o.put("properties", properties);
    o.put("required", List.copyOf(properties.keySet()));
    o.put("additionalProperties", false);
    return o;
  }

  private static Map<String, Object> str() {
    return Map.of("type", "string");
  }

  private static Map<String, Object> nullable(String type) {
    return Map.of("type", List.of(type, "null"));
  }

  private static Map<String, Object> array(Map<String, Object> items) {
    return Map.of("type", "array", "items", items);
  }

  private static Map<String, Object> enumOf(String... values) {
    return Map.of("type", "string", "enum", List.of(values));
  }
}
