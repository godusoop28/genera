package com.c21genera.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.DocumentFieldSchemas;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import com.c21genera.shared.config.AiProperties;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Extracción "primero extraer": INE (frente, frente y reverso), extracción
 * parcial, documento cargado en la categoría equivocada y documentos largos
 * donde el dato está después de la página 12. Todo contra un servidor falso.
 */
class OpenAiExtractionStrategyTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<String> requests = new CopyOnWriteArrayList<>();
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  /** responder: cuerpo de la petición -> {estado, contenido JSON del modelo (o cuerpo crudo si el estado no es 200)}. */
  private OpenAiStructuredExtractionProvider provider(Function<String, Object[]> responder, int maxRetries) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          requests.add(body);
          Object[] answer = responder.apply(body);
          int status = (int) answer[0];
          String payload =
              status == 200
                  ? mapper.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", answer[1])))))
                  : (String) answer[1];
          byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    return new OpenAiStructuredExtractionProvider(
        new AiProperties(true, "openai", "fixture-key", "fixture-model", baseUrl, Duration.ofSeconds(20), maxRetries, 96, 8), mapper);
  }

  private static String json(String documentKind, Boolean matches, String fields, String extras, String warnings) {
    return """
        {"documentCheck": {"detectedDocumentKind": "%s", "matchesExpectedType": %s, "legible": true, "observations": ""},
         "fields": [%s], "otherFields": [%s], "warnings": [%s]}"""
        .formatted(documentKind, matches, fields, extras, warnings);
  }

  private static String field(String name, String value, double confidence, int page) {
    return "{\"fieldName\": \"%s\", \"value\": \"%s\", \"confidence\": %s, \"page\": %d}".formatted(name, value, confidence, page);
  }

  private static FieldResult find(ExtractionResult result, String name) {
    return result.fields().stream().filter(f -> f.fieldName().equals(name)).findFirst().orElse(null);
  }

  private static int count(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
      n++;
    }
    return n;
  }

  // --- INE ---------------------------------------------------------------

  @Test
  void ineFrontExtractsItsFieldsWithReadingHintsForAnyDesign() throws Exception {
    var provider =
        provider(
            body ->
                new Object[] {
                  200,
                  json(
                      "credencial INE (frente)",
                      true,
                      String.join(
                          ",",
                          field("fullName", "JUAN PEREZ LOPEZ", 0.95, 1),
                          field("curp", "PELJ800101HMSRPN01", 0.9, 1),
                          field("electorKey", "PRLPJN80010117H400", 0.85, 1),
                          field("birthDate", "1980-01-01", 0.9, 1),
                          field("expirationYear", "2029", 0.9, 1)),
                      "{\"label\": \"Sección electoral\", \"value\": \"0412\", \"page\": 1}",
                      "\"Solo se ve el frente de la credencial\"")
                },
            0);

    ExtractionResult result = provider.extract(DocumentTypeCode.INE, pdf(1, null), DocumentFieldSchemas.fieldsFor(DocumentTypeCode.INE));

    assertThat(find(result, "curp").value()).isEqualTo("PELJ800101HMSRPN01");
    assertThat(find(result, "fullName").page()).isEqualTo(1);
    assertThat(find(result, "extra.Sección electoral").value()).isEqualTo("0412");
    assertThat(result.warnings()).contains("Solo se ve el frente de la credencial");
    // La IA recibe cómo leer INE/IFE de cualquier modelo (por etiqueta, MRZ del reverso) y la imagen en alta resolución.
    assertThat(requests.getFirst()).contains("IFE antigua").contains("IDMEX").contains("\"detail\":\"high\"");
  }

  @Test
  void ineFrontAndBackAreSentTogetherAndBothSidesAreUsed() throws Exception {
    var provider =
        provider(
            body ->
                new Object[] {
                  200,
                  json(
                      "credencial INE (frente y reverso)",
                      true,
                      String.join(",", field("fullName", "MARIA GOMEZ RUIZ", 0.9, 1), field("birthDate", "1985-03-02", 0.8, 2)),
                      "",
                      "")
                },
            0);

    ExtractionResult result = provider.extract(DocumentTypeCode.INE, pdf(2, null), DocumentFieldSchemas.fieldsFor(DocumentTypeCode.INE));

    assertThat(requests).hasSize(1);
    // Dos páginas verticales de una credencial: cada una va también girada ±90° (foto con el celular vertical).
    assertThat(count(requests.getFirst(), "\"type\":\"image_url\"")).isEqualTo(6);
    assertThat(requests.getFirst()).contains("Página 1:").contains("Página 2:").contains("Página 1 girada 90° a la derecha");
    assertThat(find(result, "birthDate").page()).isEqualTo(2);
  }

  @Test
  void aPartialExtractionKeepsEveryFieldThatWasReadEvenWithLowConfidence() throws Exception {
    var provider =
        provider(
            body ->
                new Object[] {
                  200,
                  json(
                      "credencial INE",
                      true,
                      String.join(
                          ",",
                          field("fullName", "JUAN PEREZ LOPEZ", 0.9, 1),
                          field("curp", "PELJ800101HMSRPN01", 0.8, 1),
                          field("birthDate", "1980-01-01", 0.3, 1)),
                      "",
                      "\"La clave de elector no se distingue\"")
                },
            0);

    ExtractionResult result = provider.extract(DocumentTypeCode.INE, pdf(1, null), DocumentFieldSchemas.fieldsFor(DocumentTypeCode.INE));

    assertThat(result.fields()).extracting(FieldResult::fieldName).containsExactlyInAnyOrder("fullName", "curp", "birthDate");
    assertThat(find(result, "birthDate").confidence()).isEqualTo(0.3);
    assertThat(find(result, "electorKey")).isNull();
  }

  @Test
  void aLongDocumentPageIsNotSentRotated() throws Exception {
    var provider = provider(body -> new Object[] {200, json("escritura", true, field("deedNumber", "1", 0.9, 1), "", "")}, 0);

    provider.extract(DocumentTypeCode.DEED, pdf(1, null), List.of("deedNumber"));

    assertThat(requests.getFirst()).doesNotContain("girada 90°");
  }

  @Test
  void rotatingSwapsWidthAndHeight() {
    var image = new java.awt.image.BufferedImage(90, 140, java.awt.image.BufferedImage.TYPE_INT_RGB);

    assertThat(OpenAiStructuredExtractionProvider.rotate(image, true).getWidth()).isEqualTo(140);
    assertThat(OpenAiStructuredExtractionProvider.rotate(image, false).getHeight()).isEqualTo(90);
  }

  // --- Categoría equivocada ----------------------------------------------

  @Test
  void aDocumentUploadedInTheWrongCategoryKeepsItsExtraction() throws Exception {
    var provider =
        provider(
            body ->
                new Object[] {
                  200,
                  json(
                      "Predial",
                      false,
                      String.join(",", field("ownerFullName", "JUAN PEREZ LOPEZ", 0.9, 1), field("cadastralKey", "1100-01-012-004", 0.9, 1)),
                      "",
                      "")
                },
            0);

    ExtractionResult result =
        provider.extract(
            DocumentTypeCode.MARRIAGE_CERTIFICATE, pdf(1, null), DocumentFieldSchemas.fieldsFor(DocumentTypeCode.MARRIAGE_CERTIFICATE));

    assertThat(result.assessment().matchesExpectedType()).isFalse();
    assertThat(result.assessment().detectedDocumentKind()).isEqualTo("Predial");
    assertThat(result.fields()).extracting(FieldResult::fieldName).contains("ownerFullName", "cadastralKey");
  }

  // --- Documentos largos -------------------------------------------------

  @Test
  void aScannedDeedLongerThan12PagesFindsInformationOnLaterPages() throws Exception {
    // Escritura escaneada de 40 páginas: el número de escritura está en la 1, el folio real hasta la página 25.
    var provider =
        provider(
            body -> {
              String fields = "";
              if (body.contains("Página 1:")) {
                fields = field("deedNumber", "45,678", 0.9, 1);
              }
              if (body.contains("Página 25:")) {
                fields = field("publicRegistryFolio", "FR-123456", 0.9, 25);
              }
              return new Object[] {200, json("escritura pública", true, fields, "", "")};
            },
            0);

    ExtractionResult result =
        provider.extract(DocumentTypeCode.DEED, pdf(40, null), List.of("deedNumber", "publicRegistryFolio"));

    assertThat(find(result, "deedNumber").value()).isEqualTo("45,678");
    assertThat(find(result, "publicRegistryFolio").page()).isEqualTo(25);
    // Se revisó más allá de las primeras 12 páginas, por lotes (no 40 imágenes en una sola petición).
    assertThat(requests.size()).isGreaterThan(1);
    assertThat(requests).allMatch(r -> count(r, "\"type\":\"image_url\"") <= 8);
    assertThat(result.pagesTotal()).isEqualTo(40);
  }

  @Test
  void aLongDocumentStopsAsSoonAsEverythingWasFound() throws Exception {
    var provider =
        provider(
            body ->
                new Object[] {
                  200,
                  json("escritura pública", true, String.join(",", field("deedNumber", "1", 0.9, 1), field("notaryName", "Lic. Díaz", 0.9, 2)), "", "")
                },
            0);

    ExtractionResult result = provider.extract(DocumentTypeCode.DEED, pdf(40, null), List.of("deedNumber", "notaryName"));

    assertThat(requests).hasSize(1);
    assertThat(result.pagesAnalyzed()).isEqualTo(8);
  }

  @Test
  void aDeedWithNativeTextSendsTheRelevantPagesTextInOneRequest() throws Exception {
    var provider =
        provider(
            body -> {
              Matcher m = Pattern.compile("=== Página (\\d+) ===").matcher(body);
              boolean hasPage22 = false;
              while (m.find()) {
                hasPage22 |= m.group(1).equals("22");
              }
              String fields = hasPage22 ? field("publicRegistryFolio", "FR-998877", 0.95, 22) : "";
              return new Object[] {200, json("escritura pública", true, fields, "", "")};
            },
            0);

    ExtractionResult result =
        provider.extract(DocumentTypeCode.DEED, pdf(30, page -> page == 22 ? "INSCRITO EN EL REGISTRO PUBLICO BAJO EL FOLIO REAL FR-998877" : null),
            List.of("publicRegistryFolio"));

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst()).contains("TEXTO DEL DOCUMENTO");
    assertThat(find(result, "publicRegistryFolio").value()).isEqualTo("FR-998877");
  }

  @Test
  void ifALaterBatchFailsWhatWasAlreadyReadIsKept() throws Exception {
    var provider =
        provider(
            body ->
                body.contains("Página 1:")
                    ? new Object[] {200, json("escritura pública", true, field("deedNumber", "45,678", 0.9, 1), "", "")}
                    : new Object[] {500, "{}"},
            0);

    ExtractionResult result = provider.extract(DocumentTypeCode.DEED, pdf(20, null), List.of("deedNumber", "publicRegistryFolio"));

    assertThat(find(result, "deedNumber").value()).isEqualTo("45,678");
    assertThat(result.warnings()).anyMatch(w -> w.contains("No se pudieron revisar las páginas"));
  }

  @Test
  void aResponseWrappedInMarkdownIsStillParsed() throws Exception {
    var provider = provider(body -> new Object[] {200, "```json\n" + json("acta de matrimonio", true, field("fullName", "ANA", 0.9, 1), "", "") + "\n```"}, 0);

    ExtractionResult result =
        provider.extract(DocumentTypeCode.MARRIAGE_CERTIFICATE, pdf(1, null), DocumentFieldSchemas.fieldsFor(DocumentTypeCode.MARRIAGE_CERTIFICATE));

    assertThat(find(result, "fullName").value()).isEqualTo("ANA");
  }

  // ---------------------------------------------------------------------

  /** PDF de n páginas; si textOf da texto para una página, todas llevan texto nativo (>200 caracteres). */
  private static byte[] pdf(int pages, Function<Integer, String> textOf) throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      for (int i = 1; i <= pages; i++) {
        PDPage page = new PDPage();
        document.addPage(page);
        if (textOf != null) {
          String special = textOf.apply(i);
          try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(font, 10);
            stream.newLineAtOffset(40, 740);
            for (int line = 0; line < 6; line++) {
              stream.showText("Texto de relleno de la pagina " + i + " del testimonio notarial, renglon " + line + ".");
              stream.newLineAtOffset(0, -14);
            }
            if (special != null) {
              stream.showText(special);
            }
            stream.endText();
          }
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
