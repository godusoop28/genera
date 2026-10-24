package com.c21genera.feasibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c21genera.expedientes.OperationType;
import com.c21genera.feasibility.domain.FeasibilityChatMessage;
import com.c21genera.feasibility.domain.FeasibilityChatMessage.Status;
import com.c21genera.feasibility.domain.FeasibilityReport;
import com.c21genera.feasibility.infrastructure.FeasibilityChatMessageRepository;
import com.c21genera.feasibility.infrastructure.FeasibilityPromptVersionRepository;
import com.c21genera.feasibility.infrastructure.FeasibilityProperties;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.Citation;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.ResponseState;
import com.c21genera.shared.domain.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeasibilityChatServiceTest {

  private final List<FeasibilityChatMessage> store = new ArrayList<>();
  private final FeasibilityService feasibility = mock(FeasibilityService.class);
  private final OpenAiBackgroundClient ai = mock(OpenAiBackgroundClient.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID reportId = UUID.randomUUID();
  private FeasibilityReport report;
  private FeasibilityChatService service;

  @BeforeEach
  void setUp() {
    FeasibilityChatMessageRepository repo = mock(FeasibilityChatMessageRepository.class);
    when(repo.save(any())).thenAnswer(inv -> {
      FeasibilityChatMessage m = inv.getArgument(0);
      if (!store.contains(m)) {
        store.add(m);
      }
      return m;
    });
    when(repo.findByReportIdOrderByCreatedAtAsc(any())).thenAnswer(inv -> store.stream().sorted(Comparator.comparing(FeasibilityChatMessage::getCreatedAt)).toList());
    FeasibilityPromptVersionRepository prompts = mock(FeasibilityPromptVersionRepository.class);
    when(prompts.findFirstByKeyOrderByVersionNumberDesc(any())).thenReturn(Optional.empty());

    report = mock(FeasibilityReport.class);
    when(report.getReportMarkdown()).thenReturn("## 1. FACTIBILIDAD COMPLETA\n\nPrecio recomendado $6,250,000.");
    when(report.getExpedienteId()).thenReturn(UUID.randomUUID());
    when(report.getVersionNumber()).thenReturn(2);
    when(feasibility.get(reportId)).thenReturn(report);
    when(feasibility.available()).thenReturn(true);
    when(feasibility.model()).thenReturn("gpt-test");
    when(feasibility.inputOf(report)).thenReturn(new FeasibilityService.RequestInput(null, null, 0, OperationType.SALE));
    when(feasibility.context(any(), any(), any(), any())).thenReturn("# DATOS DEL INMUEBLE\n- Domicilio: Vista Hermosa");

    FeasibilityProperties properties = new FeasibilityProperties(null, null, 0, 0, null, 0, 0, null, null, null, null, null, null);
    service =
        new FeasibilityChatService(
            repo, feasibility, new FeasibilityPromptService(prompts, Clock.systemUTC()), ai, properties, objectMapper, Clock.fixed(Instant.parse("2026-10-24T18:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  @SuppressWarnings("unchecked")
  void questionIsAnsweredInTheBackgroundWithTheFeasibilityAsContext() throws Exception {
    when(ai.createInBackground(any())).thenReturn("resp-chat-1");
    service.ask(reportId, "¿Qué escuelas hay cerca?", UUID.randomUUID(), "Emilia");

    ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
    org.mockito.Mockito.verify(ai).createInBackground(body.capture());
    String json = objectMapper.writeValueAsString(body.getValue());
    assertThat(json).contains("CONSULTAS SOBRE LA FACTIBILIDAD").contains("Precio recomendado $6,250,000").contains("Vista Hermosa").contains("web_search").contains("¿Qué escuelas hay cerca?");

    // Mientras la IA responde: la pregunta y la respuesta pendiente; no se puede preguntar otra vez.
    when(ai.get("resp-chat-1")).thenReturn(new ResponseState("in_progress", "", List.of(), null));
    assertThat(service.messages(reportId)).extracting(FeasibilityChatMessage::getStatus).containsExactly(Status.DONE, Status.PENDING);
    assertThatThrownBy(() -> service.ask(reportId, "Otra", UUID.randomUUID(), "Emilia")).isInstanceOf(ConflictException.class);

    when(ai.get("resp-chat-1")).thenReturn(new ResponseState("completed", "Hay tres escuelas a menos de 1 km.", List.of(new Citation("SEP", "https://sep.gob.mx")), null));
    List<FeasibilityChatMessage> messages = service.messages(reportId);
    assertThat(messages.get(1).getStatus()).isEqualTo(Status.DONE);
    assertThat(messages.get(1).getContent()).isEqualTo("Hay tres escuelas a menos de 1 km.");
    assertThat(messages.get(1).getSourcesJson()).contains("sep.gob.mx");

    // La siguiente pregunta lleva el historial.
    when(ai.createInBackground(any())).thenReturn("resp-chat-2");
    service.ask(reportId, "¿Y hospitales?", UUID.randomUUID(), "Emilia");
    org.mockito.Mockito.verify(ai, org.mockito.Mockito.times(2)).createInBackground(body.capture());
    assertThat(objectMapper.writeValueAsString(body.getValue())).contains("Hay tres escuelas a menos de 1 km.").contains("¿Y hospitales?");
  }

  @Test
  void notAvailableUntilTheAnalysisIsDone() {
    when(report.getReportMarkdown()).thenReturn(null);
    assertThatThrownBy(() -> service.ask(reportId, "Hola", UUID.randomUUID(), "Emilia")).isInstanceOf(ConflictException.class);
  }
}
