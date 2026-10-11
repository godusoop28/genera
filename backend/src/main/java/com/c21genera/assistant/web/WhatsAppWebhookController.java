package com.c21genera.assistant.web;

import com.c21genera.assistant.application.InboundService;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de WhatsApp Cloud API. Público (Meta no manda JWT): la seguridad es
 * el verify token al registrarlo y la firma HMAC (X-Hub-Signature-256) con el
 * App Secret en cada llamada. Solo persiste el mensaje y responde 200 rápido;
 * la IA corre después (AssistantWorker).
 */
@RestController
public class WhatsAppWebhookController {

  private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
  static final String PATH = "/api/v1/public/whatsapp/webhook";

  private final InboundService inbound;
  private final WhatsAppCloudClient whatsApp;
  private final ObjectMapper objectMapper;

  public WhatsAppWebhookController(InboundService inbound, WhatsAppCloudClient whatsApp, ObjectMapper objectMapper) {
    this.inbound = inbound;
    this.whatsApp = whatsApp;
    this.objectMapper = objectMapper;
  }

  /** Verificación que hace Meta al registrar el webhook. */
  @GetMapping(PATH)
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String token,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    if ("subscribe".equals(mode) && whatsApp.verifyTokenMatches(token) && challenge != null) {
      return ResponseEntity.ok(challenge);
    }
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }

  @PostMapping(PATH)
  public ResponseEntity<Void> receive(@RequestBody byte[] body, @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
    if (!whatsApp.validSignature(body, signature)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(body);
    } catch (java.io.IOException e) {
      return ResponseEntity.badRequest().build();
    }
    for (JsonNode entry : root.path("entry")) {
      for (JsonNode change : entry.path("changes")) {
        JsonNode value = change.path("value");
        Map<String, String> names = new HashMap<>();
        for (JsonNode contact : value.path("contacts")) {
          names.put(contact.path("wa_id").asText(), contact.path("profile").path("name").asText(null));
        }
        for (JsonNode message : value.path("messages")) {
          String from = message.path("from").asText(null);
          String text = textOf(message);
          if (from == null || text == null) {
            continue;
          }
          try {
            inbound.receive(from.replaceAll("\\D", ""), names.get(from), text, message.path("id").asText(null), false);
          } catch (RuntimeException e) {
            // Meta reintenta si no respondemos 200; el id del mensaje evita duplicarlo al reintentar.
            log.warn("No se pudo guardar un mensaje de WhatsApp ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
          }
        }
      }
    }
    return ResponseEntity.ok().build();
  }

  /** Texto del mensaje; los que no son texto se registran como descripción para que el equipo los revise. */
  static String textOf(JsonNode m) {
    String type = m.path("type").asText();
    return switch (type) {
      case "text" -> blankToNull(m.path("text").path("body").asText(null));
      case "button" -> blankToNull(m.path("button").path("text").asText(null));
      case "interactive" -> {
        JsonNode i = m.path("interactive");
        String title = i.path("button_reply").path("title").asText(null);
        yield blankToNull(title != null ? title : i.path("list_reply").path("title").asText(null));
      }
      case "image", "video", "document" -> {
        String caption = m.path(type).path("caption").asText("");
        yield "[El cliente envió " + (type.equals("image") ? "una imagen" : type.equals("video") ? "un video" : "un documento") + "]" + (caption.isBlank() ? "" : " " + caption);
      }
      case "audio" -> "[El cliente envió una nota de voz]";
      case "location" -> "[El cliente compartió una ubicación: " + m.path("location").path("name").asText(m.path("location").path("address").asText("sin nombre")) + "]";
      case "sticker", "reaction", "unsupported" -> null;
      default -> "[Mensaje de tipo " + type + "]";
    };
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
