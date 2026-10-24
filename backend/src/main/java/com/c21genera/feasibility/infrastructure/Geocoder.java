package com.c21genera.feasibility.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ubica un domicilio o una colonia con Nominatim (OpenStreetMap). La política de uso pide una
 * petición por segundo como máximo y un User-Agent identificable: se respeta con una pausa.
 * Nunca falla: si no encuentra nada (o no hay red) devuelve vacío y el mapa omite ese marcador.
 */
@Component
public class Geocoder {

  private static final Logger log = LoggerFactory.getLogger(Geocoder.class);

  /** precise: el resultado es un número exterior o un edificio (no solo la calle o la colonia). */
  public record LatLng(double lat, double lng, boolean precise) {}

  private final FeasibilityProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
  private long lastCallAt;

  public Geocoder(FeasibilityProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public synchronized Optional<LatLng> locate(String query) {
    if (query == null || query.isBlank()) {
      return Optional.empty();
    }
    try {
      long wait = 1100 - (System.currentTimeMillis() - lastCallAt);
      if (wait > 0) {
        Thread.sleep(wait);
      }
      lastCallAt = System.currentTimeMillis();
      String url = properties.geocodeUrl() + "?format=jsonv2&limit=1&countrycodes=mx&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", properties.userAgent()).header("Accept-Language", "es").GET().build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return Optional.empty();
      }
      JsonNode results = objectMapper.readTree(response.body());
      if (!results.isArray() || results.isEmpty()) {
        return Optional.empty();
      }
      JsonNode first = results.get(0);
      String kind = first.path("addresstype").asText(first.path("type").asText(""));
      boolean precise = kind.equals("house") || kind.equals("building");
      return Optional.of(new LatLng(first.path("lat").asDouble(), first.path("lon").asDouble(), precise));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      log.warn("No se pudo geolocalizar una ubicación para la factibilidad: {}", e.getMessage());
      return Optional.empty();
    }
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
