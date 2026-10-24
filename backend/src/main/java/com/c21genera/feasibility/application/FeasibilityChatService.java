package com.c21genera.feasibility.application;

import com.c21genera.feasibility.domain.FeasibilityChatMessage;
import com.c21genera.feasibility.domain.FeasibilityChatMessage.Role;
import com.c21genera.feasibility.domain.FeasibilityChatMessage.Status;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion.PromptKey;
import com.c21genera.feasibility.domain.FeasibilityReport;
import com.c21genera.feasibility.infrastructure.FeasibilityChatMessageRepository;
import com.c21genera.feasibility.infrastructure.FeasibilityProperties;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.ResponseState;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.UnprocessableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chat de consultas adicionales sobre una factibilidad: el asesor pregunta ("¿qué escuelas hay
 * cerca?", "¿cuánto se renta un departamento similar?", "redáctame la respuesta a esta objeción")
 * y la IA responde con la factibilidad, los datos del inmueble y búsqueda web como contexto.
 *
 * <p>Cada respuesta corre en segundo plano en OpenAI: la pregunta queda guardada con su respuesta
 * PENDIENTE y se completa cuando alguien consulta el chat (el front lo hace cada pocos segundos)
 * o, si nadie lo abre, en la siguiente vuelta de {@link FeasibilityPoller}.
 */
@Service
public class FeasibilityChatService {

  private static final Logger log = LoggerFactory.getLogger(FeasibilityChatService.class);
  static final int MAX_QUESTION = 4000;
  /** Mensajes anteriores que se mandan como historial (los más recientes). */
  static final int HISTORY = 20;
  private static final Duration ANSWER_TIMEOUT = Duration.ofMinutes(15);

  private final FeasibilityChatMessageRepository repository;
  private final FeasibilityService feasibility;
  private final FeasibilityPromptService prompts;
  private final OpenAiBackgroundClient ai;
  private final FeasibilityProperties properties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public FeasibilityChatService(
      FeasibilityChatMessageRepository repository,
      FeasibilityService feasibility,
      FeasibilityPromptService prompts,
      OpenAiBackgroundClient ai,
      FeasibilityProperties properties,
      ObjectMapper objectMapper,
      Clock clock) {
    this.repository = repository;
    this.feasibility = feasibility;
    this.prompts = prompts;
    this.ai = ai;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** Los mensajes del chat; antes completa las respuestas que la IA ya terminó. */
  public List<FeasibilityChatMessage> messages(UUID reportId) {
    List<FeasibilityChatMessage> list = repository.findByReportIdOrderByCreatedAtAsc(reportId);
    list.stream().filter(m -> m.getStatus() == Status.PENDING).forEach(this::refresh);
    return list;
  }

  public FeasibilityChatMessage ask(UUID reportId, String question, UUID userId, String userName) {
    String text = question == null ? "" : question.strip();
    if (text.isEmpty()) {
      throw new UnprocessableException("QUESTION_REQUIRED", "Escribe tu pregunta.");
    }
    if (text.length() > MAX_QUESTION) {
      throw new UnprocessableException("QUESTION_TOO_LONG", "La pregunta puede tener hasta " + MAX_QUESTION + " caracteres.");
    }
    FeasibilityReport report = feasibility.get(reportId);
    if (report.getReportMarkdown() == null) {
      throw new ConflictException("FEASIBILITY_NOT_READY", "La factibilidad todavía no tiene el análisis listo; espera a que termine para hacer consultas.");
    }
    if (!feasibility.available()) {
      throw new UnprocessableException("AI_NOT_CONFIGURED", "La inteligencia artificial no está configurada en el servidor.");
    }
    List<FeasibilityChatMessage> history = messages(reportId);
    if (history.stream().anyMatch(m -> m.getStatus() == Status.PENDING)) {
      throw new ConflictException("ANSWER_PENDING", "La IA todavía está respondiendo la pregunta anterior; espera un momento.");
    }

    String responseId = ai.createInBackground(request(report, history, text));
    FeasibilityChatMessage asked = repository.save(FeasibilityChatMessage.question(reportId, text, userId, userName, clock.instant()));
    repository.save(FeasibilityChatMessage.pendingAnswer(reportId, responseId, clock.instant().plusMillis(1)));
    return asked;
  }

  /** Respuestas que nadie ha consultado (llamado por el poller). */
  public void refreshPending() {
    repository.findByStatus(Status.PENDING).forEach(this::refresh);
  }

  private void refresh(FeasibilityChatMessage m) {
    try {
      if (Duration.between(m.getCreatedAt(), clock.instant()).compareTo(ANSWER_TIMEOUT) > 0) {
        ai.cancel(m.getAiResponseId());
        m.failed("La IA tardó demasiado en responder; vuelve a preguntar.", clock.instant());
        repository.save(m);
        return;
      }
      ResponseState state = ai.get(m.getAiResponseId());
      if (!state.finished()) {
        return;
      }
      if (state.completed() && !state.text().isBlank()) {
        m.answered(state.text(), objectMapper.writeValueAsString(state.citations()), clock.instant());
      } else {
        m.failed("La IA no pudo responder: " + (state.error() != null ? state.error() : state.status()), clock.instant());
      }
      repository.save(m);
    } catch (OpenAiBackgroundClient.AiException e) {
      log.warn("Chat de factibilidad: error temporal consultando la respuesta {}: {}", m.getId(), e.getMessage());
    } catch (IOException e) {
      m.failed("Respuesta ilegible de la IA", clock.instant());
      repository.save(m);
    }
  }

  private Map<String, Object> request(FeasibilityReport report, List<FeasibilityChatMessage> history, String question) {
    FeasibilityService.RequestInput input = feasibility.inputOf(report);
    StringBuilder context = new StringBuilder();
    context.append(feasibility.context(report.getExpedienteId(), input, List.of(), List.of()));
    context.append("\n\n# FACTIBILIDAD ELABORADA (v").append(report.getVersionNumber()).append(")\n\n").append(report.getReportMarkdown());
    if (report.getPresentationJson() != null) {
      context.append("\n\n# DATOS DE LA PRESENTACIÓN PARA EL PROPIETARIO (JSON: comparables con ligas, precio recomendado, pendientes)\n\n")
          .append(report.getPresentationJson());
    }

    List<Object> items = new ArrayList<>();
    items.add(Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", context.toString()))));
    items.add(Map.of("role", "assistant", "content", "Tengo la factibilidad y los datos del inmueble. ¿Qué necesitas consultar?"));
    List<FeasibilityChatMessage> done = history.stream().filter(m -> m.getStatus() == Status.DONE && m.getContent() != null).toList();
    for (FeasibilityChatMessage m : done.subList(Math.max(0, done.size() - HISTORY), done.size())) {
      items.add(Map.of("role", m.getRole() == Role.USER ? "user" : "assistant", "content", m.getContent()));
    }
    items.add(Map.of("role", "user", "content", question));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", feasibility.model());
    body.put("instructions", prompts.instructions(PromptKey.CHAT).text() + FeasibilityPrompts.operationRules(input.rent()));
    body.put("input", items);
    body.put("tools", List.of(Map.of("type", "web_search", "search_context_size", "medium", "user_location", Map.of("type", "approximate", "country", "MX", "region", "Morelos"))));
    body.put("max_output_tokens", 16_000);
    String effort = properties.chatReasoningEffort();
    if (effort != null && !effort.isBlank()) {
      body.put("reasoning", Map.of("effort", effort));
    }
    return body;
  }
}
