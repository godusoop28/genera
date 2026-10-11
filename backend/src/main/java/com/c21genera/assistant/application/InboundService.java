package com.c21genera.assistant.application;

import com.c21genera.assistant.application.ConversationJson.PreviousContext;
import com.c21genera.assistant.domain.Burst;
import com.c21genera.assistant.domain.Conversation;
import com.c21genera.assistant.domain.ConversationMessage;
import com.c21genera.assistant.infrastructure.AssistantProperties;
import com.c21genera.assistant.infrastructure.AssistantProperties.Mode;
import com.c21genera.assistant.infrastructure.ConversationRepository;
import com.c21genera.assistant.infrastructure.MessageRepository;
import com.c21genera.assistant.infrastructure.OpenAiResponsesClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recepción de mensajes del cliente: se PERSISTEN de inmediato (la respuesta
 * al webhook de Meta debe ser rápida) y se agenda el turno de la IA cuando
 * termine la ráfaga. La IA corre después, en AssistantWorker.
 */
@Service
public class InboundService {

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final ConversationJson json;
  private final AssistantProperties properties;
  private final OpenAiResponsesClient openAi;
  private final Clock clock;

  InboundService(ConversationRepository conversations, MessageRepository messages, ConversationJson json, AssistantProperties properties, OpenAiResponsesClient openAi, Clock clock) {
    this.conversations = conversations;
    this.messages = messages;
    this.json = json;
    this.properties = properties;
    this.openAi = openAi;
    this.clock = clock;
  }

  /** @return id de la conversación, o null si el mensaje ya se había recibido (reentrega del webhook). */
  @Transactional
  public UUID receive(String waId, String contactName, String text, String externalId, boolean test) {
    if (externalId != null && messages.existsByExternalId(externalId)) {
      return null;
    }
    Instant now = clock.instant();
    Conversation conversation =
        conversations.lockByWaId(waId).orElseGet(() -> conversations.saveAndFlush(new Conversation(waId, contactName, test)));
    if (conversation.getLastInboundAt() != null && Duration.between(conversation.getLastInboundAt(), now).compareTo(properties.sessionIdle()) > 0) {
      conversation.startNewSession(json.write(previousContext(conversation, now)));
    }
    long seq = conversation.nextSeq(now);
    messages.save(new ConversationMessage(conversation.getId(), seq, ConversationMessage.Role.CLIENT, text, externalId, ConversationMessage.Status.RECEIVED, now));
    // Si ya había una ráfaga pendiente se respeta su inicio (tope maxWait desde el primer mensaje).
    Instant burstStart = conversation.getProcessAfter() == null ? null : conversation.getBurstStartedAt();
    conversation.inbound(contactName, now, Burst.schedule(now, burstStart, properties.debounce(), properties.maxWait()));
    if (!shouldRespond(conversation)) {
      // Guardado para el panel, sin turno de IA: al reanudarla no contestará mensajes viejos.
      conversation.skipPending();
    }
    return conversation.getId();
  }

  /** ¿Le toca responder a la IA en esta conversación? */
  public boolean shouldRespond(Conversation c) {
    if (c.getControl() != Conversation.Control.AI || !openAi.configured()) {
      return false;
    }
    if (c.isTest()) {
      return true; // El simulador del panel funciona aunque el asistente esté apagado para clientes.
    }
    return switch (properties.mode()) {
      case OFF -> false;
      case TEST_ONLY -> properties.testWaIds().contains(c.getWaId());
      case ON -> true;
    };
  }

  public Mode mode() {
    return properties.mode();
  }

  private PreviousContext previousContext(Conversation c, Instant now) {
    return new PreviousContext(c.getSummary(), json.properties(c.getPropertiesJson()), c.getHandoffState().name(), null, now.toString());
  }
}
