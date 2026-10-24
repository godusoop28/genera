package com.c21genera.feasibility.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Factibilidad con IA. Usa la misma llave de OpenAI que la extracción de documentos
 * ({@code app.ai.api-key}); el modelo puede ser otro (vacío = el de la extracción).
 *
 * <ul>
 *   <li>reasoningEffort: low/medium/high; vacío = no se envía. chatReasoningEffort: el del chat de
 *       consultas (low por defecto para que responda rápido).
 *   <li>stageTimeout: tiempo máximo de cada etapa en la IA antes de darla por fallida.
 *   <li>maxFileBytes / maxTotalFileBytes: PDFs de los documentos que se adjuntan (los más
 *       grandes se omiten y la IA trabaja con los datos ya leídos de ellos).
 *   <li>tileUrl / tileFallbackUrl: teselas del mapa de la lámina 8 (OpenStreetMap y, si no
 *       responde, CARTO).
 *   <li>geocodeUrl / photonUrl: ubicar domicilios y colonias (Nominatim y, de respaldo, Photon).
 * </ul>
 */
@ConfigurationProperties(prefix = "app.feasibility")
public record FeasibilityProperties(
    String model,
    String reasoningEffort,
    int maxOutputTokensReport,
    int maxOutputTokensPresentation,
    Duration stageTimeout,
    long maxFileBytes,
    long maxTotalFileBytes,
    String tileUrl,
    String geocodeUrl,
    String userAgent,
    String tileFallbackUrl,
    String photonUrl,
    String chatReasoningEffort) {

  public FeasibilityProperties {
    if (maxOutputTokensReport <= 0) {
      maxOutputTokensReport = 100_000;
    }
    if (maxOutputTokensPresentation <= 0) {
      maxOutputTokensPresentation = 50_000;
    }
    if (stageTimeout == null) {
      stageTimeout = Duration.ofMinutes(45);
    }
    if (maxFileBytes <= 0) {
      maxFileBytes = 12L * 1024 * 1024;
    }
    if (maxTotalFileBytes <= 0) {
      maxTotalFileBytes = 30L * 1024 * 1024;
    }
    if (tileUrl == null || tileUrl.isBlank()) {
      tileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    }
    if (geocodeUrl == null || geocodeUrl.isBlank()) {
      geocodeUrl = "https://nominatim.openstreetmap.org/search";
    }
    if (tileFallbackUrl == null || tileFallbackUrl.isBlank()) {
      tileFallbackUrl = "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png";
    }
    if (photonUrl == null || photonUrl.isBlank()) {
      photonUrl = "https://photon.komoot.io/api/";
    }
    if (chatReasoningEffort == null) {
      chatReasoningEffort = "low";
    }
    if (userAgent == null || userAgent.isBlank()) {
      userAgent = "C21Genera-Factibilidad/1.0 (https://c21genera.com)";
    }
  }

  // Un solo constructor a propósito (ver AiProperties): con dos, el enlace de configuración falla al arrancar.
}
