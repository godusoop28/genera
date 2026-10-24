package com.c21genera.feasibility.infrastructure;

import com.c21genera.shared.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Responses API de OpenAI en modo segundo plano ({@code background: true}): la factibilidad
 * con búsqueda web tarda varios minutos, así que se crea la petición, se guarda su id y se
 * consulta después (ver FeasibilityPoller). Sin SDK, igual que el resto del backend. Nunca
 * registra la llave ni el contenido.
 */
@Component
public class OpenAiBackgroundClient {

  public static class AiException extends RuntimeException {
    public AiException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** status: queued, in_progress, completed, failed, cancelled o incomplete. */
  public record ResponseState(String status, String text, List<Citation> citations, String error) {

    public boolean finished() {
      return !"queued".equals(status) && !"in_progress".equals(status);
    }

    public boolean completed() {
      return "completed".equals(status);
    }
  }

  public record Citation(String title, String url) {}

  private final AiProperties ai;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  OpenAiBackgroundClient(AiProperties ai, ObjectMapper objectMapper) {
    this.ai = ai;
    this.objectMapper = objectMapper;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    // Crear/consultar en segundo plano responde en segundos; subir un PDF de 12 MB puede tardar más.
    factory.setReadTimeout(Duration.ofSeconds(120));
    RestClient.Builder builder = RestClient.builder().baseUrl(ai.baseUrl() != null ? ai.baseUrl() : "https://api.openai.com/v1").requestFactory(factory);
    if (configured()) {
      builder.defaultHeader("Authorization", "Bearer " + ai.apiKey().trim());
    }
    this.restClient = builder.build();
  }

  public boolean configured() {
    return ai.apiKey() != null && !ai.apiKey().isBlank();
  }

  public String defaultModel() {
    return ai.model();
  }

  /** Crea la respuesta en segundo plano y devuelve su id. */
  public String createInBackground(Map<String, Object> body) {
    Map<String, Object> request = new LinkedHashMap<>(body);
    request.put("background", true);
    request.put("store", true);
    JsonNode node = call(() -> restClient.post().uri("/responses").contentType(MediaType.APPLICATION_JSON).body(json(request)).retrieve().body(String.class));
    String id = node.path("id").asText(null);
    if (id == null) {
      throw new AiException("OpenAI no devolvió el id de la petición", null);
    }
    return id;
  }

  public ResponseState get(String responseId) {
    return parse(call(() -> restClient.get().uri("/responses/{id}", responseId).retrieve().body(String.class)));
  }

  public void cancel(String responseId) {
    try {
      restClient.post().uri("/responses/{id}/cancel", responseId).retrieve().toBodilessEntity();
    } catch (RuntimeException ignored) {
      // Ya terminó o ya no existe: no hay nada que cancelar.
    }
  }

  /** Sube un archivo (PDF) para usarlo como input_file; devuelve su id. */
  public String uploadFile(String filename, byte[] content, String contentType) {
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("purpose", "user_data");
    parts.add(
        "file",
        new ByteArrayResource(content) {
          @Override
          public String getFilename() {
            return filename;
          }
        });
    JsonNode node = call(() -> restClient.post().uri("/files").contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(String.class));
    return node.path("id").asText();
  }

  public void deleteFile(String fileId) {
    try {
      restClient.delete().uri("/files/{id}", fileId).retrieve().toBodilessEntity();
    } catch (RuntimeException ignored) {
      // OpenAI los borra solo después; no vale la pena fallar por esto.
    }
  }

  /** Texto final (todas las partes output_text) y fuentes citadas por la búsqueda web. */
  static ResponseState parse(JsonNode node) {
    String status = node.path("status").asText("unknown");
    StringBuilder text = new StringBuilder();
    List<Citation> citations = new ArrayList<>();
    for (JsonNode item : node.path("output")) {
      if (!"message".equals(item.path("type").asText())) {
        continue;
      }
      for (JsonNode part : item.path("content")) {
        if ("output_text".equals(part.path("type").asText())) {
          text.append(part.path("text").asText());
          for (JsonNode a : part.path("annotations")) {
            if ("url_citation".equals(a.path("type").asText()) && a.hasNonNull("url")) {
              Citation c = new Citation(a.path("title").asText(""), a.path("url").asText());
              if (citations.stream().noneMatch(x -> x.url().equals(c.url()))) {
                citations.add(c);
              }
            }
          }
        }
      }
    }
    String error = null;
    if (node.hasNonNull("error") && node.path("error").hasNonNull("message")) {
      error = node.path("error").path("message").asText();
    } else if ("incomplete".equals(status)) {
      error = "La IA no terminó la respuesta (" + node.path("incomplete_details").path("reason").asText("motivo desconocido") + ")";
    }
    return new ResponseState(status, text.toString(), citations, error);
  }

  private interface Call {
    String run();
  }

  private JsonNode call(Call call) {
    if (!configured()) {
      throw new AiException("AI_API_KEY no está configurada", null);
    }
    try {
      return objectMapper.readTree(call.run());
    } catch (RestClientResponseException e) {
      String detail = e.getResponseBodyAsString();
      throw new AiException("OpenAI respondió " + e.getStatusCode().value() + ": " + (detail.length() > 300 ? detail.substring(0, 300) : detail), e);
    } catch (ResourceAccessException e) {
      throw new AiException("Sin respuesta de OpenAI (tiempo de espera o red)", e);
    } catch (IOException e) {
      throw new AiException("Respuesta de OpenAI ilegible", e);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new AiException("No se pudo armar la petición", e);
    }
  }
}
