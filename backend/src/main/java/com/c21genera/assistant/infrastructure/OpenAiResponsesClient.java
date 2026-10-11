package com.c21genera.assistant.infrastructure;

import com.c21genera.shared.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Llamada simple a la Responses API de OpenAI (POST /responses) con la misma
 * llave que la extracción de documentos (AI_API_KEY). Igual que el resto del
 * backend, sin SDK. Nunca registra la llave ni el contenido de la conversación.
 */
@Component
public class OpenAiResponsesClient {

  /** Falla de la IA; retryable = vale la pena reintentar el turno (red, 429, 5xx). */
  public static class AiCallException extends RuntimeException {
    private final boolean retryable;

    public AiCallException(String message, boolean retryable, Throwable cause) {
      super(message, cause);
      this.retryable = retryable;
    }

    public boolean retryable() {
      return retryable;
    }
  }

  private final AiProperties ai;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  OpenAiResponsesClient(AiProperties ai, AssistantProperties assistant, ObjectMapper objectMapper) {
    this.ai = ai;
    this.objectMapper = objectMapper;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    factory.setReadTimeout(assistant.turnTimeout());
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

  public JsonNode create(Map<String, Object> body) {
    if (!configured()) {
      throw new AiCallException("AI_API_KEY no está configurada", false, null);
    }
    try {
      String raw = restClient.post().uri("/responses").contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(body)).retrieve().body(String.class);
      return objectMapper.readTree(raw);
    } catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      // El cuerpo del error de OpenAI no trae datos del cliente: sirve para diagnosticar (modelo inválido, parámetro no soportado).
      String detail = e.getResponseBodyAsString();
      throw new AiCallException("OpenAI respondió " + status + ": " + (detail.length() > 300 ? detail.substring(0, 300) : detail), status == 429 || status >= 500, e);
    } catch (ResourceAccessException e) {
      throw new AiCallException("Sin respuesta de OpenAI (tiempo de espera o red)", true, e);
    } catch (java.io.IOException e) {
      throw new AiCallException("Respuesta de OpenAI ilegible", true, e);
    }
  }
}
