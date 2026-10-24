package com.c21genera.feasibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.CurrentDocumentView;
import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteLifecycleApi.PropertyProfile;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.LegalDetails;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.extraction.ExtractionApi;
import com.c21genera.extraction.ExtractionApi.DocumentFieldView;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion.PromptKey;
import com.c21genera.feasibility.domain.FeasibilityReport;
import com.c21genera.feasibility.domain.FeasibilityReport.Status;
import com.c21genera.feasibility.infrastructure.FeasibilityDocxRenderer;
import com.c21genera.feasibility.infrastructure.FeasibilityPptxRenderer;
import com.c21genera.feasibility.infrastructure.FeasibilityPromptVersionRepository;
import com.c21genera.feasibility.infrastructure.FeasibilityProperties;
import com.c21genera.feasibility.infrastructure.FeasibilityReportRepository;
import com.c21genera.feasibility.infrastructure.Geocoder;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.Citation;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.ResponseState;
import com.c21genera.feasibility.infrastructure.StaticMapRenderer;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Flujo completo con una IA falsa: etapa 1 (análisis) → etapa 2 (presentación) → Word y PowerPoint. */
class FeasibilityServiceTest {

  private final UUID expedienteId = UUID.randomUUID();
  private final FeasibilityReportRepository repository = mock(FeasibilityReportRepository.class);
  private final ExpedienteLifecycleApi expedientes = mock(ExpedienteLifecycleApi.class);
  private final DocumentsApi documents = mock(DocumentsApi.class);
  private final ExtractionApi extraction = mock(ExtractionApi.class);
  private final OpenAiBackgroundClient ai = mock(OpenAiBackgroundClient.class);
  private final Geocoder geocoder = mock(Geocoder.class);
  private final Map<String, byte[]> stored = new HashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private FeasibilityService service;

  private final FeasibilityPromptVersionRepository promptRepository = mock(FeasibilityPromptVersionRepository.class);
  private final FeasibilityPromptService prompts = new FeasibilityPromptService(promptRepository, Clock.systemUTC());

  @BeforeEach
  void setUp() {
    FileStorage storage = mock(FileStorage.class);
    when(storage.store(anyString(), any(InputStream.class), anyLong(), anyString()))
        .thenAnswer(
            inv -> {
              String key = inv.getArgument(0);
              stored.put(key, ((InputStream) inv.getArgument(1)).readAllBytes());
              return new FileStorage.StoredObjectMetadata(key, stored.get(key).length, "x");
            });
    when(storage.get(anyString())).thenAnswer(inv -> new ByteArrayInputStream(stored.get((String) inv.getArgument(0))));
    stored.put("pdf/predial.pdf", "%PDF-1.4 predial".getBytes());
    stored.put("pdf/ine.pdf", "%PDF-1.4 ine".getBytes());

    when(repository.save(any(FeasibilityReport.class))).thenAnswer(inv -> inv.getArgument(0));
    when(repository.findFirstByExpedienteIdOrderByVersionNumberDesc(expedienteId)).thenReturn(Optional.empty());
    when(expedientes.getSummary(expedienteId))
        .thenReturn(
            new ExpedienteSummary(
                expedienteId, "EXP-2026-000123", "Juan Pérez", ExpedienteStatus.DOCUMENTS_RECEIVED, PersonType.FISICA, SignerCharacter.PROPIETARIO,
                AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING, PropertyLegalStatus.LIBRE_GRAVAMEN,
                "Calle Río Balsas 12, Vista Hermosa, Cuernavaca, Morelos", UUID.randomUUID(), null, List.of(), LegalDetails.empty()));
    when(expedientes.getManualData(expedienteId))
        .thenReturn(
            new ManualClientDataView(
                new BigDecimal("6900000"), null, null, null, null, null, null, null, null, 3, 3, 2, "Bueno", "Agua, luz, gas", "Alberca",
                new BigDecimal("420"), new BigDecimal("320")));
    when(expedientes.getPropertyProfile(expedienteId))
        .thenReturn(new PropertyProfile("Casa", "Casa en privada", null, OperationType.SALE, "Casa Balsas", "CVEA03", "Emilia Rodríguez"));
    when(documents.currentDocumentsOf(expedienteId))
        .thenReturn(
            List.of(
                new CurrentDocumentView(UUID.randomUUID(), "PROPERTY_TAX", "Predial 2026", "UPLOADED", "pdf/predial.pdf"),
                new CurrentDocumentView(UUID.randomUUID(), "INE", "INE de Juan", "ACCEPTED", "pdf/ine.pdf")));
    when(extraction.documentFieldsOf(expedienteId))
        .thenReturn(
            List.of(
                new DocumentFieldView("PROPERTY_TAX", "Predial 2026", "landArea", "420 m2", false),
                new DocumentFieldView("PROPERTY_TAX", "Predial 2026", "ownerFullName", "JUAN PÉREZ LÓPEZ", false),
                new DocumentFieldView("INE", "INE de Juan", "curp", "PELJ800101HMSRPN09", true)));
    when(ai.configured()).thenReturn(true);
    when(ai.defaultModel()).thenReturn("gpt-test");
    when(ai.uploadFile(anyString(), any(), anyString())).thenReturn("file-1");
    when(geocoder.locate(anyString())).thenReturn(Optional.of(new Geocoder.LatLng(18.93, -99.225, true)));

    FeasibilityProperties properties = new FeasibilityProperties(null, "medium", 0, 0, null, 0, 0, "http://127.0.0.1:9/{z}/{x}/{y}.png", null, null);
    service =
        new FeasibilityService(
            repository, expedientes, documents, extraction, storage, ai, properties, geocoder, new StaticMapRenderer(properties), new FeasibilityDocxRenderer(),
            new FeasibilityPptxRenderer(), prompts, objectMapper, Clock.fixed(Instant.parse("2026-10-24T18:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  @SuppressWarnings("unchecked")
  void usesTheAnalystPromptEditedInSettingsAndRecordsItsVersion() throws Exception {
    when(promptRepository.findFirstByKeyOrderByVersionNumberDesc(PromptKey.ANALYST))
        .thenReturn(Optional.of(new FeasibilityPromptVersion(PromptKey.ANALYST, 3, "# PROMPT EDITADO EN CONFIGURACIÓN v3", null,
            Instant.parse("2026-10-24T17:00:00Z"), null, "Admin")));
    FeasibilityReport report = service.request(expedienteId, null, null, null, List.of(), UUID.randomUUID(), "Emilia");
    when(ai.createInBackground(any())).thenReturn("resp-1");
    service.advance(report);
    ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
    verify(ai).createInBackground(body.capture());
    assertThat((String) body.getValue().get("instructions"))
        .startsWith("# PROMPT EDITADO EN CONFIGURACIÓN v3")
        .contains("REGLAS DE OPERACIÓN DENTRO DEL SISTEMA CENTURY 21 GENERA")
        .doesNotContain("ANALISTA DE FACTIBILIDAD 3.0");
    assertThat(report.getAnalystPromptVersion()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("unchecked")
  void runsBothStagesAndStoresWordAndPowerPoint() throws Exception {
    FeasibilityReport report = service.request(expedienteId, null, null, "Remodelada en 2022", List.of(), UUID.randomUUID(), "Emilia");
    assertThat(report.getStatus()).isEqualTo(Status.ANALYZING);

    // Etapa 1: solo se adjunta el PDF del inmueble (no la INE) y el contexto no lleva datos personales.
    when(ai.createInBackground(any())).thenReturn("resp-1", "resp-2");
    service.advance(report);
    verify(ai).uploadFile("property_tax.pdf", "%PDF-1.4 predial".getBytes(), "application/pdf");
    ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
    verify(ai).createInBackground(body.capture());
    String request = objectMapper.writeValueAsString(body.getValue());
    assertThat(request)
        .contains("Analista de Factibilidad 3.0")
        .contains("web_search")
        .contains("file-1")
        .contains("6900000")
        .contains("landArea: 420 m2")
        .contains("Remodelada en 2022")
        .doesNotContain("JUAN PÉREZ LÓPEZ")
        .doesNotContain("PELJ800101HMSRPN09");
    assertThat(report.getAiResponseId()).isEqualTo("resp-1");
    assertThat(report.getAnalystPromptVersion()).isZero(); // texto original del cliente

    // Mientras la IA trabaja no cambia nada.
    when(ai.get("resp-1")).thenReturn(new ResponseState("in_progress", "", List.of(), null));
    service.advance(report);
    assertThat(report.getStatus()).isEqualTo(Status.ANALYZING);

    // Etapa 1 lista → se pide la presentación con JSON estricto y se liberan los archivos.
    when(ai.get("resp-1"))
        .thenReturn(new ResponseState("completed", "## 1. FACTIBILIDAD COMPLETA\n\nTexto.\n\n## 2. RESUMEN EJECUTIVO\n\n- Punto", List.of(new Citation("Portal", "https://portal.mx/1")), null));
    service.advance(report);
    assertThat(report.getStatus()).isEqualTo(Status.PRESENTING);
    verify(ai).deleteFile("file-1");
    verify(ai, org.mockito.Mockito.times(2)).createInBackground(body.capture());
    assertThat(objectMapper.writeValueAsString(body.getValue())).contains("json_schema").contains("FACTIBILIDAD INMOBILIARIA (FUENTE PRINCIPAL)");

    // Etapa 2 lista → Word y PowerPoint guardados.
    String json = objectMapper.writeValueAsString(com.c21genera.feasibility.infrastructure.FeasibilityRenderersTest.sample());
    when(ai.get("resp-2")).thenReturn(new ResponseState("completed", json, List.of(), null));
    service.advance(report);
    assertThat(report.getStatus()).as(report.getError()).isEqualTo(Status.READY);
    assertThat(report.getDocxStorageKey()).endsWith("factibilidad-EXP-2026-000123.docx");
    assertThat(report.getPptxStorageKey()).endsWith("presentacion-factibilidad-EXP-2026-000123.pptx");
    assertThat(stored.get(report.getPptxStorageKey())).isNotEmpty();
    assertThat(report.getPresentationJson()).contains("\"C1\"");
  }

  @Test
  void failedResponseLeavesTheReasonAndReleasesFiles() {
    FeasibilityReport report = service.request(expedienteId, null, new BigDecimal("5000000"), null, List.of(), UUID.randomUUID(), "Emilia");
    when(ai.createInBackground(any())).thenReturn("resp-1");
    service.advance(report);
    when(ai.get("resp-1")).thenReturn(new ResponseState("failed", "", List.of(), "model not found"));
    service.advance(report);
    assertThat(report.getStatus()).isEqualTo(Status.FAILED);
    assertThat(report.getError()).contains("model not found");
    verify(ai).deleteFile("file-1");
  }

  @Test
  void onlyOneFeasibilityAtATimePerExpediente() {
    when(repository.existsByExpedienteIdAndStatusIn(any(), any())).thenReturn(true);
    assertThatThrownBy(() -> service.request(expedienteId, null, null, null, List.of(), UUID.randomUUID(), "Emilia")).isInstanceOf(ConflictException.class);
    verify(repository, never()).save(any());
  }
}
