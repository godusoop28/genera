package com.c21genera.assistant.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envío por WhatsApp Cloud API (graph.facebook.com/{version}/{phone_number_id}/messages)
 * y validación de la firma de los webhooks. Nunca registra el token.
 */
@Component
public class WhatsAppCloudClient {

  /** Resultado de un envío. uncertain = no se sabe si llegó (tiempo de espera): no se reenvía solo. */
  public record SendResult(boolean ok, String messageId, String error, boolean retryable, boolean uncertain) {}

  private final WhatsAppProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  WhatsAppCloudClient(WhatsAppProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    factory.setReadTimeout(Duration.ofSeconds(20));
    RestClient.Builder builder = RestClient.builder().baseUrl("https://graph.facebook.com/" + properties.apiVersion()).requestFactory(factory);
    if (properties.canSend()) {
      builder.defaultHeader("Authorization", "Bearer " + properties.accessToken().trim());
    }
    this.restClient = builder.build();
  }

  public boolean canSend() {
    return properties.canSend();
  }

  public SendResult sendText(String to, String text) {
    return send(Map.of("messaging_product", "whatsapp", "to", to, "type", "text", "text", Map.of("preview_url", true, "body", text)));
  }

  /** Plantilla aprobada con un parámetro de cuerpo (para avisar al asesor fuera de la ventana de 24 h). */
  public SendResult sendTemplate(String to, String template, String language, String bodyParam) {
    String param = bodyParam.replaceAll("[\\n\\t]+", " · ").replaceAll(" {4,}", "   ");
    return send(
        Map.of(
            "messaging_product", "whatsapp",
            "to", to,
            "type", "template",
            "template",
                Map.of(
                    "name", template,
                    "language", Map.of("code", language),
                    "components", List.of(Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", param)))))));
  }

  private SendResult send(Map<String, Object> payload) {
    if (!canSend()) {
      return new SendResult(false, null, "WhatsApp Cloud API no está configurada", false, false);
    }
    try {
      String raw = restClient.post().uri("/{phone}/messages", properties.phoneNumberId()).contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(payload)).retrieve().body(String.class);
      JsonNode node = objectMapper.readTree(raw);
      String id = node.path("messages").path(0).path("id").asText(null);
      return new SendResult(true, id, null, false, false);
    } catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      String message = errorMessage(e.getResponseBodyAsString());
      return new SendResult(false, null, "WhatsApp " + status + ": " + message, status == 429 || status >= 500, false);
    } catch (ResourceAccessException e) {
      return new SendResult(false, null, "Sin respuesta de WhatsApp (pudo haberse entregado)", false, true);
    } catch (java.io.IOException e) {
      return new SendResult(true, null, null, false, false);
    }
  }

  private String errorMessage(String body) {
    try {
      JsonNode error = objectMapper.readTree(body).path("error");
      String text = error.path("message").asText("");
      int code = error.path("code").asInt(0);
      // 131047: fuera de la ventana de 24 h; solo se puede escribir con plantilla.
      return code == 131047 ? "Fuera de la ventana de 24 h de WhatsApp: hace falta una plantilla aprobada" : (text.length() > 200 ? text.substring(0, 200) : text);
    } catch (java.io.IOException | RuntimeException e) {
      return "error desconocido";
    }
  }

  /** Valida X-Hub-Signature-256 ("sha256=<hex>") con el App Secret de Meta, en tiempo constante. */
  public boolean validSignature(byte[] body, String header) {
    String secret = properties.appSecret();
    if (secret == null || secret.isBlank() || header == null || !header.startsWith("sha256=")) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = mac.doFinal(body);
      byte[] given = HexFormat.of().parseHex(header.substring(7));
      return MessageDigest.isEqual(expected, given);
    } catch (Exception e) {
      return false;
    }
  }

  public boolean verifyTokenMatches(String token) {
    String expected = properties.verifyToken();
    return expected != null && !expected.isBlank() && token != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
  }
}
