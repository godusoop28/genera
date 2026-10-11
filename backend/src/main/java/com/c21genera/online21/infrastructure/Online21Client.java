package com.c21genera.online21.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP mínimo de la API de 21Online (API Platform). Acepta tanto la
 * forma JSON-LD ({@code hydra:member}/{@code member}) como un arreglo plano.
 * Nunca registra la llave ni el cuerpo de las respuestas (contienen datos de
 * dueños).
 */
@Component
class Online21Client {

  private final Online21Properties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  Online21Client(Online21Properties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    factory.setReadTimeout(properties.timeout());
    RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory);
    if (properties.configured()) {
      builder.defaultHeader("Authorization", properties.authPrefix() + properties.apiKey().trim());
    }
    this.restClient = builder.build();
  }

  boolean configured() {
    return properties.configured();
  }

  /** Una página de propiedades en promoción y publicadas en internet. */
  List<JsonNode> propertiesPage(int page) {
    String body =
        restClient
            .get()
            .uri(b -> b.path("/api/propiedades").queryParam("status", "enPromocion").queryParam("enInternet", "true").queryParam("page", page).build())
            .accept(MediaType.valueOf("application/ld+json"), MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String.class);
    return members(body);
  }

  /** Ficha de una propiedad; null si 21Online responde 404. */
  JsonNode property(long id) {
    try {
      String body = restClient.get().uri("/api/propiedades/{id}", id).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
      return body == null ? null : objectMapper.readTree(body);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        return null;
      }
      throw e;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Respuesta de 21Online ilegible", e);
    }
  }

  /** Crea un prospecto; devuelve el id que asigna 21Online. */
  Long createProspect(Map<String, Object> payload) {
    try {
      String body =
          restClient.post().uri("/api/prospectos").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(payload)).retrieve().body(String.class);
      JsonNode node = body == null ? null : objectMapper.readTree(body);
      return node != null && node.hasNonNull("id") ? node.get("id").asLong() : null;
    } catch (java.io.IOException e) {
      return null;
    }
  }

  private List<JsonNode> members(String body) {
    if (body == null || body.isBlank()) {
      return List.of();
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode list = root.isArray() ? root : root.has("hydra:member") ? root.get("hydra:member") : root.get("member");
      List<JsonNode> out = new ArrayList<>();
      if (list != null && list.isArray()) {
        list.forEach(out::add);
      }
      return out;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Respuesta de 21Online ilegible", e);
    }
  }
}
