package com.c21genera.assistant.application;

import com.c21genera.assistant.application.AssistantTools.TurnState;
import com.c21genera.assistant.application.TurnModels.HistoryMessage;
import com.c21genera.assistant.application.TurnModels.StoredProperty;
import com.c21genera.assistant.application.TurnModels.ToolTrace;
import com.c21genera.assistant.application.TurnModels.TurnInput;
import com.c21genera.assistant.application.TurnModels.TurnOutput;
import com.c21genera.assistant.domain.AssistantContract;
import com.c21genera.assistant.domain.AssistantPolicy;
import com.c21genera.assistant.domain.AssistantPolicy.Fact;
import com.c21genera.assistant.domain.AssistantPolicy.Intent;
import com.c21genera.assistant.domain.AssistantPrompt;
import com.c21genera.assistant.domain.AssistantPrompt.Greeting;
import com.c21genera.assistant.domain.AssistantPrompt.VerifiedProperty;
import com.c21genera.assistant.domain.Conversation.HandoffState;
import com.c21genera.assistant.infrastructure.AssistantProperties;
import com.c21genera.assistant.infrastructure.OpenAiResponsesClient;
import com.c21genera.assistant.infrastructure.OpenAiResponsesClient.AiCallException;
import com.c21genera.leads.LeadIntake;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Un turno del asistente (como runAssistantTurn de Inova): contexto (estado +
 * historial reciente) → modelo con herramientas acotadas → salida JSON
 * validada → controles de verdad (propiedades no verificadas, asignaciones no
 * confirmadas, términos internos) con una corrección como máximo. Lanza
 * {@link AiCallException} si OpenAI falla; quien llama decide el reintento.
 */
@Component
class AssistantAgent {

  private static final Logger log = LoggerFactory.getLogger(AssistantAgent.class);
  private static final DateTimeFormatter NOW_TEXT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy, HH:mm", Locale.forLanguageTag("es-MX"));
  /** Números de propiedad (6–7 dígitos) mencionados en la respuesta, sin contar precios ($) ni teléfonos. */
  private static final Pattern REPLY_REF = Pattern.compile("(?<![$\\d.,])\\b(\\d{6,7})\\b(?![.,]\\d)");

  private final OpenAiResponsesClient openAi;
  private final AssistantTools tools;
  private final AssistantProperties properties;
  private final LeadIntake leads;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  AssistantAgent(OpenAiResponsesClient openAi, AssistantTools tools, AssistantProperties properties, LeadIntake leads, ObjectMapper objectMapper, Clock clock) {
    this.openAi = openAi;
    this.tools = tools;
    this.properties = properties;
    this.leads = leads;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  String model() {
    return properties.model() != null && !properties.model().isBlank() ? properties.model() : openAi.defaultModel();
  }

  TurnOutput run(TurnInput in) {
    TurnState s = new TurnState(in);
    for (StoredProperty p : in.sessionProperties()) {
      s.verified.put(p.ref(), p.title());
    }
    for (StoredProperty p : in.previousProperties()) {
      s.verified.put(p.ref(), p.title());
      s.previousRefs.add(p.ref());
    }
    for (HistoryMessage m : in.history()) {
      if (m.isNew() && "CLIENT".equals(m.role())) {
        tools.referencesIn(m.text()).forEach(r -> s.clientRefs.add(r.toUpperCase(Locale.ROOT)));
      }
    }

    List<Object> items = new ArrayList<>();
    items.add(Map.of("role", "developer", "content", AssistantPrompt.stateBlock(state(in))));
    items.addAll(historyItems(in.history()));

    String model = model();
    int inputTokens = 0;
    int outputTokens = 0;
    int corrections = 0;

    for (int round = 0; round < AssistantContract.MAX_ROUNDS + AssistantContract.MAX_CORRECTIONS; round++) {
      boolean lastRound = round >= AssistantContract.MAX_ROUNDS - 1 || s.toolCalls >= AssistantContract.MAX_TOOL_CALLS;
      JsonNode response = openAi.create(request(model, items, lastRound));
      inputTokens += response.path("usage").path("input_tokens").asInt(0);
      outputTokens += response.path("usage").path("output_tokens").asInt(0);

      List<JsonNode> calls = new ArrayList<>();
      String outputText = null;
      for (JsonNode item : response.path("output")) {
        String type = item.path("type").asText();
        if ("function_call".equals(type)) {
          calls.add(item);
        } else if ("message".equals(type)) {
          for (JsonNode c : item.path("content")) {
            if ("output_text".equals(c.path("type").asText())) {
              outputText = c.path("text").asText();
            }
          }
        }
      }

      if (!calls.isEmpty()) {
        response.path("output").forEach(items::add);
        for (JsonNode call : calls) {
          Object output;
          String name = call.path("name").asText();
          if (!AssistantContract.TOOL_NAMES.contains(name)) {
            output = Map.of("ok", false, "error", "unknown_tool");
          } else if (s.toolCalls >= AssistantContract.MAX_TOOL_CALLS) {
            output = Map.of("ok", false, "error", "tool_limit_reached", "hint", "Responde con lo que ya sabes.");
          } else {
            s.toolCalls++;
            try {
              output = tools.execute(s, name, objectMapper.readTree(call.path("arguments").asText("{}")));
            } catch (Exception e) {
              log.warn("Herramienta {} falló ({})", name, e.getClass().getSimpleName());
              s.trace.add(new ToolTrace(name, false, "error interno"));
              output = Map.of("ok", false, "error", "internal_error");
            }
          }
          items.add(Map.of("type", "function_call_output", "call_id", call.path("call_id").asText(), "output", json(output)));
        }
        continue;
      }

      if (outputText == null || outputText.isBlank()) {
        throw new AiCallException("El modelo no devolvió respuesta final (" + response.path("status").asText() + ")", true, null);
      }
      JsonNode finalOut;
      try {
        finalOut = objectMapper.readTree(outputText);
      } catch (Exception e) {
        throw new AiCallException("La salida del modelo no es JSON válido", true, e);
      }
      String reply = finalOut.path("reply").asText("").trim();
      if (reply.length() > 1500) {
        reply = reply.substring(0, 1500);
      }

      // --- Controles de verdad del backend ---
      List<String> unverified = unverifiedRefs(reply, s);
      boolean falseClaim = AssistantPolicy.claimsAssignment(reply) && !assignmentConfirmed(s) && !s.humanRequested;
      boolean internal = AssistantPolicy.mentionsInternalProcess(reply);
      boolean pendingPromise = isPending(s) && AssistantPolicy.promisesAvailability(reply);
      internal = internal || pendingPromise;
      if ((!unverified.isEmpty() || falseClaim || internal) && corrections < AssistantContract.MAX_CORRECTIONS) {
        corrections++;
        response.path("output").forEach(items::add);
        List<String> fixes = new ArrayList<>(List.of("Corrige tu respuesta antes de enviarla:"));
        if (!unverified.isEmpty()) {
          fixes.add("- Mencionas " + String.join(", ", unverified) + " sin haberlo verificado con una herramienta. Verifícalo o no lo menciones.");
        }
        if (falseClaim) {
          fixes.add("- Afirmas una asignación o aviso que el sistema NO confirmó. No lo afirmes; usa la herramienta o di lo que realmente ocurrió.");
        }
        if (pendingPromise) {
          fixes.add("- La solicitud quedó pendiente: no digas que el asesor está disponible ni prometas tiempos; di que le contactarán en su horario de atención.");
        } else if (internal) {
          fixes.add("- No menciones la 'ruleta' ni cómo se elige al asesor; di solo a quién quedó asignada la solicitud.");
        }
        items.add(Map.of("role", "developer", "content", String.join("\n", fixes)));
        continue;
      }
      if (!unverified.isEmpty() || falseClaim) {
        throw new AiCallException("La respuesta sigue afirmando datos no verificados tras la corrección", false, null);
      }
      if (pendingPromise) {
        throw new AiCallException("La respuesta promete disponibilidad de un lead pendiente tras la corrección", false, null);
      }
      if (internal) {
        reply = AssistantPolicy.stripInternalProcess(reply);
      }

      Map<String, Fact> facts = AssistantPolicy.mergeFacts(in.facts(), factsOf(finalOut.path("facts")));
      List<StoredProperty> props = new ArrayList<>(in.sessionProperties());
      for (JsonNode ref : finalOut.path("property_refs")) {
        String id = ref.asText().replaceAll("--.*", "").trim();
        String title = s.verified.get(id);
        if (title != null && props.stream().noneMatch(p -> p.ref().equals(id))) {
          props.add(new StoredProperty(id, title));
        }
      }
      List<Intent> secondary = new ArrayList<>();
      for (JsonNode i : finalOut.path("secondary_intents")) {
        Intent parsed = Intent.parse(i.asText());
        if (parsed != Intent.UNKNOWN && !secondary.contains(parsed) && secondary.size() < 6) {
          secondary.add(parsed);
        }
      }
      return new TurnOutput(
          reply,
          Intent.parse(finalOut.path("primary_intent").asText()),
          secondary,
          facts,
          props,
          finalOut.path("summary").asText(null),
          finalOut.path("asked_clarification").asBoolean(false),
          finalOut.path("made_progress").asBoolean(false),
          s.handoff,
          s.trace,
          inputTokens,
          outputTokens,
          corrections,
          model);
    }
    throw new AiCallException("Se alcanzó el límite de rondas sin respuesta final", true, null);
  }

  private Map<String, Object> request(String model, List<Object> items, boolean lastRound) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("instructions", AssistantPrompt.SYSTEM_PROMPT);
    body.put("input", items);
    body.put("tools", AssistantContract.TOOLS);
    body.put("tool_choice", lastRound ? "none" : "auto");
    body.put("text", Map.of("format", Map.of("type", "json_schema", "name", "assistant_turn", "schema", AssistantContract.OUTPUT_SCHEMA, "strict", true)));
    body.put("max_output_tokens", AssistantContract.MAX_OUTPUT_TOKENS);
    if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
      body.put("reasoning", Map.of("effort", properties.reasoningEffort()));
    }
    return body;
  }

  private AssistantPrompt.State state(TurnInput in) {
    Greeting greeting = in.firstReply() ? Greeting.FIRST_CONTACT : in.returningSession() ? Greeting.RETURNING_SESSION : Greeting.NONE;
    return new AssistantPrompt.State(
        greeting,
        in.primaryIntent(),
        in.secondaryIntents(),
        in.facts(),
        in.sessionProperties().stream().map(p -> new VerifiedProperty(p.ref(), p.title())).toList(),
        in.summary(),
        handoffText(in),
        in.clarificationCount(),
        properties.maxClarifications(),
        in.contactName(),
        NOW_TEXT.format(clock.instant().atZone(java.time.ZoneId.of("America/Mexico_City"))),
        in.previousSessionText(),
        tools.inventoryAvailable());
  }

  private String handoffText(TurnInput in) {
    String advisor = null;
    if (in.handoffState() == HandoffState.ASSIGNED || in.handoffState() == HandoffState.EXISTING_LEAD || in.handoffState() == HandoffState.PENDING_OWNER || !in.handoffInSession()) {
      advisor = leads.recentLeadFor(AssistantTools.phone(in)).map(LeadIntake.RecentLead::advisorName).orElse(null);
    }
    String with = advisor != null ? " con " + advisor : "";
    if (!in.handoffInSession()) {
      return advisor != null
          ? "en una conversación anterior quedó canalizado" + with + " (asesor vigente). No reasignes: si trae una solicitud nueva, regístrala con request_commercial_handoff (el sistema la anexa a su asesor) y menciona a su asesor por su nombre"
          : "todavía no canalizado en esta sesión";
    }
    return switch (in.handoffState()) {
      case ASSIGNED -> "ya canalizado" + with + " en esta sesión (no vuelvas a pedir datos; el asesor le dará seguimiento)";
      case EXISTING_LEAD -> "ya tenía asesor asignado" + with + " (no pidas otro)";
      case NO_ADVISOR -> "quedó registrado pendiente: un asesor lo contactará al iniciar su horario de atención (no prometas horas ni digas que hay alguien disponible)";
      case PENDING_OWNER -> "quedó reservado" + with + ", asesor de la propiedad, que lo contactará en su próximo horario de atención (no digas que está disponible ahora)";
      case NOT_APPLICABLE -> "no es cliente inmobiliario; su asunto quedó registrado para gerencia";
      case HUMAN_REQUESTED -> "pidió hablar con una persona; quedó registrado para el equipo";
      case NONE -> "todavía no canalizado";
    };
  }

  private static List<Object> historyItems(List<HistoryMessage> history) {
    List<Object> items = new ArrayList<>();
    List<HistoryMessage> recent = history.size() > AssistantContract.HISTORY_MESSAGES ? history.subList(history.size() - AssistantContract.HISTORY_MESSAGES, history.size()) : history;
    boolean inPrevious = false;
    boolean anyPrevious = recent.stream().anyMatch(HistoryMessage::previousSession);
    for (int i = 0; i < recent.size(); i++) {
      HistoryMessage m = recent.get(i);
      if (m.previousSession() && !inPrevious) {
        items.add(Map.of("role", "developer", "content", "--- Conversación anterior (ya atendida; contexto, NO solicitudes actuales) ---"));
        inPrevious = true;
      }
      if (!m.previousSession() && (inPrevious || (i == 0 && anyPrevious))) {
        items.add(Map.of("role", "developer", "content", "--- Nueva conversación: lo que sigue es lo actual ---"));
        inPrevious = false;
      }
      switch (m.role()) {
        case "CLIENT" -> items.add(Map.of("role", "user", "content", m.text()));
        case "TEAM" -> items.add(Map.of("role", "assistant", "content", "[Respuesta de una persona del equipo] " + m.text()));
        case "BOT" -> items.add(Map.of("role", "assistant", "content", m.text()));
        default -> {}
      }
    }
    return items;
  }

  private List<String> unverifiedRefs(String reply, TurnState s) {
    List<String> out = new ArrayList<>();
    Matcher m = REPLY_REF.matcher(reply);
    while (m.find()) {
      if (!s.verified.containsKey(m.group(1))) {
        out.add(m.group(1));
      }
    }
    return out;
  }

  private static boolean isPending(TurnState s) {
    HandoffState st = s.handoff != null ? s.handoff.state() : s.input.handoffState();
    return st == HandoffState.PENDING_OWNER || st == HandoffState.NO_ADVISOR;
  }

  private static boolean assignmentConfirmed(TurnState s) {
    // "Quedó registrada con X" también es real cuando está reservado para el asesor de la propiedad.
    if (s.handoff != null && (s.handoff.state() == HandoffState.ASSIGNED || s.handoff.state() == HandoffState.EXISTING_LEAD || s.handoff.state() == HandoffState.PENDING_OWNER || s.handoff.state() == HandoffState.NO_ADVISOR)) {
      return true;
    }
    return s.input.handoffState() == HandoffState.ASSIGNED || s.input.handoffState() == HandoffState.EXISTING_LEAD || s.input.handoffState() == HandoffState.PENDING_OWNER;
  }

  private static Map<String, Fact> factsOf(JsonNode node) {
    Map<String, Fact> facts = new LinkedHashMap<>();
    for (String key : AssistantPolicy.FACT_KEYS) {
      JsonNode f = node.path(key);
      if (f.isObject()) {
        facts.put(key, new Fact(f.hasNonNull("value") ? f.get("value").asText() : null, f.path("status").asText("unknown")));
      }
    }
    return facts;
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return "{\"ok\":false,\"error\":\"serialization\"}";
    }
  }
}
