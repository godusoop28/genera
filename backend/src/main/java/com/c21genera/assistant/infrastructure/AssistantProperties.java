package com.c21genera.assistant.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Asistente de WhatsApp. Usa la misma llave de OpenAI que la extracción de
 * documentos ({@code app.ai.api-key}); el modelo puede ser otro.
 *
 * <p>mode: OFF (default: guarda mensajes, no responde la IA), TEST_ONLY
 * (solo responde al simulador y a {@code testWaIds}) u ON.
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
    Mode mode,
    String model,
    String reasoningEffort,
    Duration debounce,
    Duration maxWait,
    int maxClarifications,
    Duration sessionIdle,
    Duration abandonHandoffAfter,
    Duration turnTimeout,
    List<String> testWaIds) {

  public enum Mode {
    OFF,
    TEST_ONLY,
    ON
  }

  public AssistantProperties {
    if (mode == null) {
      mode = Mode.OFF;
    }
    if (debounce == null) {
      debounce = Duration.ofSeconds(6);
    }
    if (maxWait == null) {
      maxWait = Duration.ofSeconds(25);
    }
    if (maxClarifications <= 0) {
      maxClarifications = 3;
    }
    if (sessionIdle == null) {
      sessionIdle = Duration.ofHours(6);
    }
    if (abandonHandoffAfter == null) {
      abandonHandoffAfter = Duration.ofMinutes(30);
    }
    if (turnTimeout == null) {
      turnTimeout = Duration.ofSeconds(40);
    }
    if (testWaIds == null) {
      testWaIds = List.of();
    }
  }
}
