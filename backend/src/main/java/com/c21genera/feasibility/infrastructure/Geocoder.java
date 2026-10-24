package com.c21genera.feasibility.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ubica un domicilio o una colonia. Primero Nominatim (OpenStreetMap, con preferencia por la zona
 * de Morelos) y, si no encuentra nada o no responde (p. ej. limita las peticiones desde el
 * servidor), Photon (komoot), que usa los mismos datos. Los anuncios suelen escribir la ubicación
 * como "Col. Vista Hermosa, Cuernavaca, Mor., C.P. 62290": se prueban variantes limpias de mayor a
 * menor detalle. Nominatim pide máximo una petición por segundo y un User-Agent identificable.
 * Nunca falla: sin resultado devuelve vacío y el mapa omite ese marcador.
 */
@Component
public class Geocoder {

  private static final Logger log = LoggerFactory.getLogger(Geocoder.class);

  /** Centro de Cuernavaca: la oficina; la búsqueda prefiere resultados cercanos. */
  public static final double HOME_LAT = 18.9242;
  public static final double HOME_LNG = -99.2216;

  /** precise: el resultado es un número exterior o un edificio (no solo la calle o la colonia). */
  public record LatLng(double lat, double lng, boolean precise) {}

  private final FeasibilityProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
  private final Map<String, Optional<LatLng>> cache = new ConcurrentHashMap<>();
  private long lastNominatimCall;

  public Geocoder(FeasibilityProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /** Prueba la ubicación tal cual y sus variantes limpias; la primera que se encuentre gana. */
  public Optional<LatLng> locate(String query) {
    for (String variant : variants(query)) {
      Optional<LatLng> found = cache.computeIfAbsent(variant.toLowerCase(Locale.ROOT), k -> lookup(variant));
      if (found.isPresent()) {
        return found;
      }
    }
    if (query != null && !query.isBlank()) {
      log.info("Factibilidad: no se pudo ubicar en el mapa \"{}\"", query);
    }
    return Optional.empty();
  }

  /**
   * "Calle Río Balsas No. Ext. 12, Col. Vista Hermosa, Cuernavaca, Mor., C.P. 62290" →
   * [tal cual limpio, "Vista Hermosa, Cuernavaca, Morelos", "Vista Hermosa, Morelos", ...].
   */
  static List<String> variants(String query) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (query == null || query.isBlank()) {
      return List.of();
    }
    String cleaned =
        query
            .replaceAll("(?i)\\bC\\.?\\s?P\\.?\\s*\\d{5}\\b", " ")
            .replaceAll("(?i)\\b(no\\.?|núm\\.?|num\\.?|número)\\s*(ext\\.?|int\\.?|exterior|interior)?\\s*\\d+[a-z]?\\b", " ")
            .replaceAll("(?i)\\b(int\\.?|interior|depto\\.?|departamento|lote|manzana|mz\\.?|lt\\.?)\\s*[\\w-]+", " ")
            .replaceAll("(?i)\\b(col\\.|colonia|fracc\\.|fraccionamiento|fracc|unidad hab\\.?)\\s*", "")
            .replaceAll("(?i)\\bmor\\.?(?=\\s*(,|$))", "Morelos")
            .replaceAll("(?i),?\\s*(méxico|mexico)\\s*$", "")
            .replaceAll("\\s+", " ")
            .replaceAll("\\s*,\\s*", ", ")
            .replaceAll("(,\\s*)+", ", ")
            .replaceAll("^[,\\s]+|[,\\s]+$", "")
            .strip();
    boolean hasState = cleaned.toLowerCase(Locale.ROOT).contains("morelos");
    out.add(hasState ? cleaned : cleaned + ", Morelos");
    String[] parts = cleaned.split(",\\s*");
    List<String> pieces = new ArrayList<>();
    for (String p : parts) {
      if (!p.isBlank() && !p.equalsIgnoreCase("Morelos") && !p.matches("\\d+")) {
        pieces.add(p.strip());
      }
    }
    // Sin la calle: colonia + municipio, y luego solo la colonia o el municipio.
    if (pieces.size() >= 3) {
      out.add(String.join(", ", pieces.subList(pieces.size() - 2, pieces.size())) + ", Morelos");
    }
    if (pieces.size() >= 2) {
      out.add(pieces.get(pieces.size() - 2) + ", " + pieces.get(pieces.size() - 1) + ", Morelos");
      out.add(pieces.get(pieces.size() - 2) + ", Morelos");
    }
    if (!pieces.isEmpty()) {
      out.add(pieces.get(pieces.size() - 1) + ", Morelos");
    }
    out.add(stripAccents(cleaned));
    out.removeIf(String::isBlank);
    return List.copyOf(out);
  }

  private Optional<LatLng> lookup(String query) {
    Optional<LatLng> found = nominatim(query);
    return found.isPresent() ? found : photon(query);
  }

  private synchronized Optional<LatLng> nominatim(String query) {
    try {
      long wait = 1100 - (System.currentTimeMillis() - lastNominatimCall);
      if (wait > 0) {
        Thread.sleep(wait);
      }
      lastNominatimCall = System.currentTimeMillis();
      // viewbox sin "bounded": prefiere Morelos y alrededores, pero no excluye el resto del país.
      String url =
          properties.geocodeUrl()
              + "?format=jsonv2&limit=1&countrycodes=mx&viewbox=-99.6,19.2,-98.6,18.3&q="
              + URLEncoder.encode(query, StandardCharsets.UTF_8);
      HttpResponse<String> response = get(url);
      if (response.statusCode() != 200) {
        log.warn("Nominatim respondió {} al ubicar para la factibilidad", response.statusCode());
        return Optional.empty();
      }
      JsonNode results = objectMapper.readTree(response.body());
      if (!results.isArray() || results.isEmpty()) {
        return Optional.empty();
      }
      JsonNode first = results.get(0);
      String kind = first.path("addresstype").asText(first.path("type").asText(""));
      return Optional.of(new LatLng(first.path("lat").asDouble(), first.path("lon").asDouble(), kind.equals("house") || kind.equals("building")));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Nominatim no respondió al ubicar para la factibilidad: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<LatLng> photon(String query) {
    try {
      String url =
          properties.photonUrl() + "?limit=1&lat=" + HOME_LAT + "&lon=" + HOME_LNG + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
      HttpResponse<String> response = get(url);
      if (response.statusCode() != 200) {
        log.warn("Photon respondió {} al ubicar para la factibilidad", response.statusCode());
        return Optional.empty();
      }
      JsonNode feature = objectMapper.readTree(response.body()).path("features").path(0);
      if (feature.isMissingNode() || !"MX".equalsIgnoreCase(feature.path("properties").path("countrycode").asText("MX"))) {
        return Optional.empty();
      }
      JsonNode coordinates = feature.path("geometry").path("coordinates");
      String kind = feature.path("properties").path("type").asText("");
      return Optional.of(new LatLng(coordinates.path(1).asDouble(), coordinates.path(0).asDouble(), kind.equals("house")));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Photon no respondió al ubicar para la factibilidad: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private HttpResponse<String> get(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", properties.userAgent()).header("Accept-Language", "es").GET().build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String stripAccents(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
  }

  /** Distancia en línea recta (fórmula del haversine), en kilómetros. */
  public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
    double r = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * r * Math.asin(Math.sqrt(a));
  }
}
