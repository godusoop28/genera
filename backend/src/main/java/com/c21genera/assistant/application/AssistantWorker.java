package com.c21genera.assistant.application;

import com.c21genera.assistant.application.AssistantTools.TurnState;
import com.c21genera.assistant.application.TurnModels.TurnInput;
import com.c21genera.assistant.domain.AssistantPolicy;
import com.c21genera.assistant.domain.AssistantPolicy.Intent;
import com.c21genera.assistant.domain.AssistantPolicy.Trigger;
import com.c21genera.assistant.domain.Conversation;
import com.c21genera.assistant.infrastructure.AssistantProperties;
import com.c21genera.assistant.infrastructure.ConversationRepository;
import com.c21genera.online21.PropertyCatalog;
import com.c21genera.online21.PropertyCatalog.CatalogProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Corre los turnos pendientes cada 2 s, envía la cola de mensajes y, cada
 * minuto, canaliza a quien mostró interés inmobiliario y dejó de responder
 * (regla de abandono de Inova: nunca para proveedores ni gerencia).
 */
@Component
class AssistantWorker {

  private static final Logger log = LoggerFactory.getLogger(AssistantWorker.class);
  private static final List<String> REAL_ESTATE = List.of("PROPERTY_INQUIRY", "BUY", "RENT", "SELL", "LEASE_OUT", "INVEST");

  private final ConversationRepository conversations;
  private final TurnProcessor processor;
  private final OutboxSender outbox;
  private final AssistantTools tools;
  private final ConversationJson json;
  private final PropertyCatalog catalog;
  private final AssistantProperties properties;
  private final TransactionTemplate tx;
  private final Clock clock;

  AssistantWorker(
      ConversationRepository conversations,
      TurnProcessor processor,
      OutboxSender outbox,
      AssistantTools tools,
      ConversationJson json,
      PropertyCatalog catalog,
      AssistantProperties properties,
      TransactionTemplate tx,
      Clock clock) {
    this.conversations = conversations;
    this.processor = processor;
    this.outbox = outbox;
    this.tools = tools;
    this.json = json;
    this.catalog = catalog;
    this.properties = properties;
    this.tx = tx;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "PT2S", initialDelayString = "PT20S")
  void tick() {
    try {
      for (UUID id : conversations.findDue(clock.instant(), PageRequest.of(0, 5))) {
        processor.process(id);
      }
      outbox.flush();
    } catch (RuntimeException e) {
      log.warn("Ciclo del asistente con error ({})", e.getClass().getSimpleName());
    }
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT90S")
  void sweepAbandoned() {
    if (properties.mode() != AssistantProperties.Mode.ON || properties.abandonHandoffAfter().isZero()) {
      return;
    }
    Instant now = clock.instant();
    Instant idleSince = now.minus(properties.abandonHandoffAfter());
    // Solo conversaciones recientes: no se canaliza a alguien que dejó de escribir hace días.
    Instant notBefore = now.minus(Duration.ofHours(12));
    for (UUID id : conversations.findAbandoned(idleSince, notBefore, REAL_ESTATE, PageRequest.of(0, 10))) {
      try {
        handoffAbandoned(id);
      } catch (RuntimeException e) {
        log.warn("No se pudo canalizar por abandono la conversación {} ({})", id, e.getClass().getSimpleName());
      }
    }
  }

  private void handoffAbandoned(UUID id) {
    Conversation c = conversations.findById(id).orElse(null);
    if (c == null) {
      return;
    }
    TurnInput input =
        new TurnInput(
            c.getId(), c.getWaId(), c.getContactName(), c.isTest(), c.getSessionSeq(), false, false,
            Intent.parse(c.getPrimaryIntent()), ConversationJson.intents(c.getSecondaryIntents()), json.facts(c.getFactsJson()),
            json.properties(c.getPropertiesJson()), List.of(), c.getSummary(), c.getHandoffState(), false, c.getClarificationCount(), null, List.of(), c.getLastSeq());
    if (!AssistantPolicy.decideHandoff(input.primaryIntent(), input.secondaryIntents(), false, Trigger.ABANDONMENT).allowed()) {
      return;
    }
    CatalogProperty property =
        input.sessionProperties().isEmpty() ? null : catalog.find(input.sessionProperties().get(0).ref()).property().orElse(null);
    TurnState state = new TurnState(input);
    Map<String, Object> result = tools.runHandoff(state, input.primaryIntent(), "Dejó de responder al asistente con interés confirmado", property, Trigger.ABANDONMENT);
    if (state.handoff == null) {
      return;
    }
    tx.executeWithoutResult(
        s -> {
          Conversation locked = conversations.lockById(id).orElseThrow();
          locked.handoff(state.handoff.state(), state.handoff.leadId(), clock.instant());
          Object message = result.get("customer_message");
          if (message instanceof String text && !text.isBlank()) {
            processor.queueReply(locked, text, clock.instant());
          }
        });
  }
}
