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

  /** Página del inventario completo (cualquier estatus) para la pantalla interna; devuelve el cuerpo tal cual. */
  JsonNode listingsPage(Map<String, String> filters, int page) {
    String body =
        restClient
            .get()
            .uri(
                b -> {
                  b.path("/api/propiedades");
                  filters.forEach(b::queryParam);
                  return b.queryParam("order[fechaModificacion]", "desc").queryParam("page", page).build();
                })
            .accept(MediaType.valueOf("application/ld+json"), MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String.class);
    return read(body);
  }

  /**
   * GET genérico de una colección de 21Online (JSON-LD) con filtros; devuelve el cuerpo tal cual. Los
   * filtros pueden repetirse (p. ej. "idPropiedades.id"). Los errores HTTP se propagan.
   */
  JsonNode collection(String path, Map<String, String> filters, int page) {
    String body =
        restClient
            .get()
            .uri(
                b -> {
                  b.path(path);
                  filters.forEach(b::queryParam);
                  return b.queryParam("page", page).build();
                })
            .accept(MediaType.valueOf("application/ld+json"), MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String.class);
    return read(body);
  }

  /** GET de un recurso por su ruta (p. ej. "/api/usuarios/41388"); null si 21Online responde 404. */
  JsonNode item(String path) {
    try {
      String body = restClient.get().uri(path).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
      return read(body);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        return null;
      }
      throw e;
    }
  }

  /** Lista de propiedades de una respuesta (JSON-LD o arreglo plano). */
  List<JsonNode> membersOf(JsonNode root) {
    List<JsonNode> out = new ArrayList<>();
    if (root == null) {
      return out;
    }
    JsonNode list = root.isArray() ? root : root.has("hydra:member") ? root.get("hydra:member") : root.get("member");
    if (list != null && list.isArray()) {
      list.forEach(out::add);
    }
    return out;
  }

  /** POST /api/propiedades; devuelve el id que asigna 21Online. Los errores HTTP se propagan. */
  long createProperty(Map<String, Object> payload) {
    String body =
        restClient.post().uri("/api/propiedades").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(write(payload)).retrieve().body(String.class);
    JsonNode node = read(body);
    if (node == null || !node.hasNonNull("id")) {
      throw new IllegalStateException("21Online no devolvió el id de la propiedad");
    }
    return node.get("id").asLong();
  }

  /** PUT /api/propiedades/{id}. Los errores HTTP se propagan. */
  void updateProperty(long id, Map<String, Object> payload) {
    restClient.put().uri("/api/propiedades/{id}", id).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(write(payload)).retrieve().toBodilessEntity();
  }

  private String write(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("No se pudo armar la solicitud a 21Online", e);
    }
  }

  private JsonNode read(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(body);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Respuesta de 21Online ilegible", e);
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
