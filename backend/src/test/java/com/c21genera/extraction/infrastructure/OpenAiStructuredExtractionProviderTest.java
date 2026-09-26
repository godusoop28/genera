package com.c21genera.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.extraction.domain.StructuredExtractionProvider.AiUnavailableException;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.shared.config.AiProperties;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Fallo rápido de configuración (ver AGENTS §39) y comportamiento ante
 * errores del servicio. Nunca usa una API key real ni llama a la IA real:
 * las peticiones van a un servidor HTTP falso en localhost.
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

  private static final String VALID_RESPONSE =
      """
      {"choices":[{"message":{"content":"{\\"documentCheck\\":{\\"matchesExpectedType\\":false,\\"legible\\":true,\\"detectedDocumentKind\\":\\"lista de compras\\",\\"observations\\":\\"No es una credencial\\"},\\"fields\\":[{\\"fieldName\\":\\"fullName\\",\\"value\\":\\"JUAN PEREZ\\",\\"confidence\\":0.9}]}"}}]}
      """;

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  /** Servidor falso: responde según el número de llamada (1, 2, ...) con {estado, cuerpo}. */
  private OpenAiStructuredExtractionProvider providerAgainst(IntFunction<Object[]> responder, AtomicInteger calls, int maxRetries)
      throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          // Como un servidor estricto: exige Content-Length (sin cuerpo "chunked").
          Object[] response =
              exchange.getRequestHeaders().getFirst("Content-Length") == null
                  ? new Object[] {411, "{}"}
                  : responder.apply(calls.incrementAndGet());
          byte[] body = ((String) response[1]).getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders((int) response[0], body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    AiProperties properties = new AiProperties(true, "openai", "fixture-key", "fixture-model", baseUrl, Duration.ofSeconds(5), maxRetries);
    return new OpenAiStructuredExtractionProvider(properties, new ObjectMapper());
  }

  private static byte[] onePagePdf() throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void parsesContentCheckAndFields() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var provider = providerAgainst(n -> new Object[] {200, VALID_RESPONSE}, calls, 0);

    ExtractionResult result = provider.extract(DocumentTypeCode.INE, onePagePdf(), List.of("fullName"));

    assertThat(result.assessment().matchesExpectedType()).isFalse();
    assertThat(result.assessment().detectedDocumentKind()).isEqualTo("lista de compras");
    assertThat(result.fields()).singleElement().satisfies(f -> assertThat(f.value()).isEqualTo("JUAN PEREZ"));
  }

  @Test
  void retriesTransientServerErrors() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var provider = providerAgainst(n -> n == 1 ? new Object[] {503, "{}"} : new Object[] {200, VALID_RESPONSE}, calls, 1);

    ExtractionResult result = provider.extract(DocumentTypeCode.INE, onePagePdf(), List.of("fullName"));

    assertThat(calls.get()).isEqualTo(2);
    assertThat(result.fields()).hasSize(1);
  }

  @Test
  void throwsWhenServiceNeverAnswersInsteadOfReturningAnEmptyApproval() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var provider = providerAgainst(n -> new Object[] {500, "{}"}, calls, 1);

    assertThatThrownBy(() -> provider.extract(DocumentTypeCode.INE, onePagePdf(), List.of("fullName")))
        .isInstanceOf(AiUnavailableException.class);
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void doesNotRetryWhenTheRequestItselfIsRejected() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var provider = providerAgainst(n -> new Object[] {401, "{\"error\":\"invalid key\"}"}, calls, 2);

    assertThatThrownBy(() -> provider.extract(DocumentTypeCode.INE, onePagePdf(), List.of("fullName")))
        .isInstanceOf(AiUnavailableException.class);
    assertThat(calls.get()).isEqualTo(1);
  }
}
