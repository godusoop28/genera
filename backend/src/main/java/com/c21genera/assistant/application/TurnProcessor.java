package com.c21genera.assistant.application;

import com.c21genera.assistant.application.ConversationJson.PreviousContext;
import com.c21genera.assistant.application.TurnModels.HistoryMessage;
import com.c21genera.assistant.application.TurnModels.StoredProperty;
import com.c21genera.assistant.application.TurnModels.TurnInput;
import com.c21genera.assistant.application.TurnModels.TurnOutput;
import com.c21genera.assistant.domain.AssistantPolicy.Intent;
import com.c21genera.assistant.domain.Conversation;
import com.c21genera.assistant.domain.ConversationMessage;
import com.c21genera.assistant.domain.ConversationTurn;
import com.c21genera.assistant.domain.Escalation;
import com.c21genera.assistant.infrastructure.ConversationRepository;
import com.c21genera.assistant.infrastructure.EscalationRepository;
import com.c21genera.assistant.infrastructure.MessageRepository;
import com.c21genera.assistant.infrastructure.OpenAiResponsesClient.AiCallException;
import com.c21genera.assistant.infrastructure.TurnRepository;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesa el turno pendiente de una conversación (como conversation-processor
 * de Inova):
 *
 * <ol>
 *   <li>Toma el "lease" de la conversación en una transacción corta.
 *   <li>Corre la IA fuera de transacción (puede tardar varios segundos).
 *   <li>Guarda el resultado con la fila bloqueada. Si mientras tanto llegó otro
 *       mensaje, la respuesta se descarta y se vuelve a procesar con todo junto
 *       (nunca contesta a medias).
 *   <li>Si OpenAI falla: reintenta a los 10 s, 30 s y 90 s; después manda una
 *       respuesta fija una sola vez, pausa la IA y deja un pendiente.
 * </ol>
 */
@Service
class TurnProcessor {

  private static final Logger log = LoggerFactory.getLogger(TurnProcessor.class);
  private static final Duration LEASE = Duration.ofSeconds(150);
  private static final List<Duration> RETRIES = List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(90));
  static final String FALLBACK_REPLY =
      "Gracias por tu mensaje. En este momento no puedo responderte automáticamente; una persona del equipo de CENTURY 21 Genera revisará tu solicitud.";

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final TurnRepository turns;
  private final EscalationRepository escalations;
  private final InboundService inbound;
  private final AssistantAgent agent;
  private final ConversationJson json;
  private final WhatsAppCloudClient whatsApp;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final String owner = "worker-" + UUID.randomUUID().toString().substring(0, 8);

  TurnProcessor(
      ConversationRepository conversations,
      MessageRepository messages,
      TurnRepository turns,
      EscalationRepository escalations,
      InboundService inbound,
      AssistantAgent agent,
      ConversationJson json,
      WhatsAppCloudClient whatsApp,
      TransactionTemplate tx,
      Clock clock) {
    this.conversations = conversations;
    this.messages = messages;
    this.turns = turns;
    this.escalations = escalations;
    this.inbound = inbound;
    this.agent = agent;
    this.json = json;
    this.whatsApp = whatsApp;
    this.tx = tx;
    this.clock = clock;
  }

  void process(UUID conversationId) {
    TurnInput input = tx.execute(status -> claim(conversationId));
    if (input == null) {
      return;
    }
    long toSeq = input.toSeq();
    long started = System.currentTimeMillis();
    try {
      TurnOutput output = agent.run(input);
      tx.executeWithoutResult(status -> apply(conversationId, input, output, toSeq, (int) (System.currentTimeMillis() - started)));
    } catch (AiCallException e) {
      log.warn("Turno del asistente falló en {} ({})", conversationId, e.getMessage());
      tx.executeWithoutResult(status -> fail(conversationId, input, toSeq, e, (int) (System.currentTimeMillis() - started)));
    } catch (RuntimeException e) {
      log.error("Error inesperado en el turno del asistente {}", conversationId, e);
      tx.executeWithoutResult(status -> fail(conversationId, input, toSeq, new AiCallException("Error interno: " + e.getClass().getSimpleName(), false, e), (int) (System.currentTimeMillis() - started)));
    }
  }

  private TurnInput claim(UUID id) {
    Instant now = clock.instant();
    Conversation c = conversations.lockById(id).orElse(null);
    if (c == null || c.getProcessAfter() == null || c.getProcessAfter().isAfter(now) || !c.leaseFree(now)) {
      return null;
    }
    if (!inbound.shouldRespond(c)) {
      c.skipPending();
      return null;
    }
    if (c.getProcessedSeq() >= c.getLastSeq()) {
      c.processed(c.getLastSeq());
      return null;
    }
    c.lease(owner, now.plus(LEASE));

    List<ConversationMessage> recent = new ArrayList<>(messages.findRecent(id, PageRequest.of(0, 40)));
    Collections.reverse(recent);
    List<HistoryMessage> history =
        recent.stream()
            .filter(m -> m.getRole() != ConversationMessage.Role.SYSTEM)
            .map(m -> new HistoryMessage(m.getRole().name(), m.getText(), m.getSeq() < c.getSessionStartSeq(), m.getSeq() > c.getProcessedSeq()))
            .toList();
    boolean anyAssistantReply = recent.stream().anyMatch(m -> m.getRole() == ConversationMessage.Role.BOT || m.getRole() == ConversationMessage.Role.TEAM);
    boolean replyInSession = recent.stream().anyMatch(m -> m.getSeq() >= c.getSessionStartSeq() && m.getRole() != ConversationMessage.Role.CLIENT);
    PreviousContext previous = json.previous(c.getPreviousContext());
    return new TurnInput(
        c.getId(),
        c.getWaId(),
        c.getContactName(),
        c.isTest(),
        c.getSessionSeq(),
        !anyAssistantReply,
        anyAssistantReply && !replyInSession && c.getSessionSeq() > 1,
        Intent.parse(c.getPrimaryIntent()),
        ConversationJson.intents(c.getSecondaryIntents()),
        json.facts(c.getFactsJson()),
        json.properties(c.getPropertiesJson()),
        previous == null || previous.properties() == null ? List.of() : previous.properties(),
        c.getSummary(),
        c.getHandoffState(),
        c.getHandoffAt() != null,
        c.getClarificationCount(),
        previousText(previous),
        history,
        c.getLastSeq());
  }

  private void apply(UUID id, TurnInput input, TurnOutput out, long toSeq, int durationMs) {
    Instant now = clock.instant();
    Conversation c = conversations.lockById(id).orElseThrow();
    if (out.handoff() != null) {
      c.handoff(out.handoff().state(), out.handoff().leadId(), now);
    }
    String trace = json.write(out.trace());
    if (c.getLastSeq() > toSeq) {
      // Llegó otro mensaje mientras pensaba: no se contesta a medias; el siguiente turno lo incluye.
      turns.save(new ConversationTurn(id, c.getProcessedSeq() + 1, toSeq, "SUPERSEDED", out.model(), out.inputTokens(), out.outputTokens(), trace, null, durationMs, now));
      c.releaseLease();
      return;
    }
    c.applyUnderstanding(
        out.primaryIntent().name(),
        ConversationJson.intentsCsv(out.secondaryIntents()),
        json.write(out.facts()),
        json.write(out.properties()),
        out.summary(),
        out.askedClarification(),
        out.madeProgress());
    if (out.reply() != null && !out.reply().isBlank()) {
      queueReply(c, out.reply(), now);
    }
    turns.save(new ConversationTurn(id, c.getProcessedSeq() + 1, toSeq, "COMPLETED", out.model(), out.inputTokens(), out.outputTokens(), trace, null, durationMs, now));
    c.processed(toSeq);
    c.releaseLease();
  }

  private void fail(UUID id, TurnInput input, long toSeq, AiCallException e, int durationMs) {
    Instant now = clock.instant();
    Conversation c = conversations.lockById(id).orElseThrow();
    turns.save(new ConversationTurn(id, c.getProcessedSeq() + 1, toSeq, "FAILED", agent.model(), null, null, null, e.getMessage(), durationMs, now));
    if (e.retryable() && c.getFailedAttempts() < RETRIES.size()) {
      c.retryAt(now.plus(RETRIES.get(c.getFailedAttempts())));
      c.releaseLease();
      return;
    }
    queueReply(c, FALLBACK_REPLY, now);
    c.setControl(Conversation.Control.PAUSED);
    c.attention("El asistente no pudo responder: " + e.getMessage());
    if (!c.isTest()) {
      escalations.save(new Escalation(id, Escalation.Type.PROCESSING_ERROR, e.getMessage() == null ? "Falla del asistente" : e.getMessage(), now));
    }
    c.skipPending();
    c.releaseLease();
  }

  /** Respuesta en cola; el simulador y un WhatsApp sin configurar la guardan sin enviar. */
  void queueReply(Conversation c, String text, Instant now) {
    long seq = c.nextSeq(now);
    ConversationMessage message = new ConversationMessage(c.getId(), seq, ConversationMessage.Role.BOT, text, null, ConversationMessage.Status.QUEUED, now);
    if (c.isTest()) {
      message.notSent("Simulador: no se envía por WhatsApp");
    } else if (!whatsApp.canSend()) {
      message.notSent("WhatsApp Cloud API no está configurada");
    }
    messages.save(message);
  }

  private static String previousText(PreviousContext p) {
    if (p == null) {
      return null;
    }
    List<String> lines = new ArrayList<>();
    if ("ASSIGNED".equals(p.handoffState()) || "EXISTING_LEAD".equals(p.handoffState())) {
      lines.add("- Quedó canalizado con un asesor (asesor vigente; no se reasigna).");
    } else if ("NOT_APPLICABLE".equals(p.handoffState())) {
      lines.add("- Su asunto quedó registrado para gerencia.");
    }
    if (p.properties() != null && !p.properties().isEmpty()) {
      lines.add("- Propiedades de las que habló: " + String.join("; ", p.properties().stream().map(sp -> sp.ref() + " (" + sp.title() + ")").toList()));
    }
    if (p.summary() != null) {
      lines.add("- Resumen: " + p.summary());
    }
    return lines.isEmpty() ? null : String.join("\n", lines);
  }

  /** Para el barrido de abandono: propiedades que quedaron en la sesión. */
  List<StoredProperty> storedProperties(Conversation c) {
    return json.properties(c.getPropertiesJson());
  }
}
