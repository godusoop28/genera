package com.c21genera.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.shared.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Solo prueba el fallo rápido de configuración (ver AGENTS §39: "falla
 * claramente si se activa sin credenciales"). Nunca instancia este adaptador
 * con una API key real ni hace peticiones de red: eso violaría la regla de
 * no llamar IA real en tests/CI.
 */
class OpenAiStructuredExtractionProviderTest {

  @Test
  void failsFastWhenEnabledWithoutApiKey() {
    AiProperties properties = new AiProperties(true, "openai", "", "gpt-4o-mini", "https://api.openai.com/v1", Duration.ofSeconds(30), 2);

    assertThatThrownBy(() -> new OpenAiStructuredExtractionProvider(properties, new ObjectMapper()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_API_KEY");
  }

  @Test
  void failsFastWhenEnabledWithoutModel() {
    AiProperties properties =
        new AiProperties(true, "openai", "not-a-real-key-fixture", "", "https://api.openai.com/v1", Duration.ofSeconds(30), 2);

    assertThatThrownBy(() -> new OpenAiStructuredExtractionProvider(properties, new ObjectMapper()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_MODEL");
  }
}
