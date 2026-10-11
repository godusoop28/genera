package com.c21genera.assistant.application;

import com.c21genera.assistant.domain.AssistantPolicy.Intent;
import com.c21genera.assistant.domain.Conversation;
import com.c21genera.assistant.domain.ConversationMessage;
import com.c21genera.assistant.domain.Escalation;
import com.c21genera.assistant.infrastructure.AssistantProperties;
import com.c21genera.assistant.infrastructure.ConversationRepository;
import com.c21genera.assistant.infrastructure.EscalationRepository;
import com.c21genera.assistant.infrastructure.MessageRepository;
import com.c21genera.assistant.infrastructure.OpenAiResponsesClient;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import com.c21genera.assistant.infrastructure.WhatsAppProperties;
import com.c21genera.online21.PropertyCatalog;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bandeja del panel: conversaciones, historial, pausar/reanudar la IA, responder como equipo y simulador. */
@Service
@Transactional
public class ConversationAdminService {

  public record ConversationItem(
      UUID id,
      String contactName,
      String phone,
      boolean test,
      String control,
      String intent,
      String handoffState,
      UUID leadId,
      String needsAttention,
      String lastMessage,
      String lastMessageRole,
      Instant lastMessageAt,
      long openEscalations) {}

  public record MessageView(UUID id, String role, String text, String status, String error, Instant createdAt) {}

  public record EscalationView(UUID id, String type, String reason, String status, Instant createdAt) {}

  public record ConversationDetail(ConversationItem conversation, String summary, List<MessageView> messages, List<EscalationView> escalations) {}

  public record Status(String mode, boolean aiConfigured, String model, boolean whatsappConfigured, boolean webhookConfigured, boolean inventoryAvailable) {}

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final EscalationRepository escalations;
  private final InboundService inbound;
  private final AssistantAgent agent;
  private final OpenAiResponsesClient openAi;
  private final WhatsAppCloudClient whatsApp;
  private final WhatsAppProperties whatsAppProperties;
  private final AssistantProperties properties;
  private final PropertyCatalog catalog;
  private final Clock clock;

  ConversationAdminService(
      ConversationRepository conversations,
      MessageRepository messages,
      EscalationRepository escalations,
      InboundService inbound,
      AssistantAgent agent,
      OpenAiResponsesClient openAi,
      WhatsAppCloudClient whatsApp,
      WhatsAppProperties whatsAppProperties,
      AssistantProperties properties,
      PropertyCatalog catalog,
      Clock clock) {
    this.conversations = conversations;
    this.messages = messages;
    this.escalations = escalations;
    this.inbound = inbound;
    this.agent = agent;
    this.openAi = openAi;
    this.whatsApp = whatsApp;
    this.whatsAppProperties = whatsAppProperties;
    this.properties = properties;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Status status() {
    return new Status(
        properties.mode().name(),
        openAi.configured(),
        agent.model(),
        whatsApp.canSend(),
        whatsAppProperties.verifyToken() != null && !whatsAppProperties.verifyToken().isBlank() && whatsAppProperties.appSecret() != null && !whatsAppProperties.appSecret().isBlank(),
        catalog.available());
  }

  @Transactional(readOnly = true)
  public List<ConversationItem> list() {
    Set<UUID> withOpen = escalations.findByStatus("OPEN").stream().map(Escalation::getConversationId).collect(Collectors.toSet());
    return conversations.findAllByOrderByLastMessageAtDesc(PageRequest.of(0, 100)).stream().map(c -> item(c, withOpen.contains(c.getId()) ? 1 : 0)).toList();
  }

  @Transactional(readOnly = true)
  public ConversationDetail get(UUID id) {
    Conversation c = conversations.findById(id).orElseThrow(() -> new NotFoundException("Conversación no encontrada"));
    List<Escalation> esc = escalations.findByConversationIdOrderByCreatedAtDesc(id);
    return new ConversationDetail(
        item(c, esc.stream().filter(e -> "OPEN".equals(e.getStatus())).count()),
        c.getSummary(),
        messages.findByConversationIdOrderBySeqAsc(id).stream()
            .map(m -> new MessageView(m.getId(), m.getRole().name(), m.getText(), m.getStatus().name(), m.getError(), m.getCreatedAt()))
            .toList(),
        esc.stream().map(e -> new EscalationView(e.getId(), e.getType().name(), e.getReason(), e.getStatus(), e.getCreatedAt())).toList());
  }

  public ConversationDetail setControl(UUID id, Conversation.Control control) {
    Conversation c = conversations.lockById(id).orElseThrow(() -> new NotFoundException("Conversación no encontrada"));
    c.setControl(control);
    if (control == Conversation.Control.AI) {
      c.attention(null);
      // Al reanudar no se contesta lo que llegó durante la pausa.
      c.skipPending();
    }
    return get(id);
  }

  /** Respuesta del equipo: pausa la IA en esta conversación para no pisarse. */
  public ConversationDetail teamReply(UUID id, String text) {
    if (text == null || text.isBlank()) {
      throw new UnprocessableException("MESSAGE_EMPTY", "El mensaje está vacío.");
    }
    Instant now = clock.instant();
    Conversation c = conversations.lockById(id).orElseThrow(() -> new NotFoundException("Conversación no encontrada"));
    c.setControl(Conversation.Control.HUMAN);
    c.skipPending();
    long seq = c.nextSeq(now);
    ConversationMessage m = new ConversationMessage(id, seq, ConversationMessage.Role.TEAM, text.trim(), null, ConversationMessage.Status.QUEUED, now);
    if (c.isTest()) {
      m.notSent("Simulador: no se envía por WhatsApp");
    } else if (!whatsApp.canSend()) {
      m.notSent("WhatsApp Cloud API no está configurada");
    }
    messages.save(m);
    return get(id);
  }

  public void resolveEscalation(UUID escalationId) {
    escalations.findById(escalationId).ifPresent(e -> e.resolve(clock.instant()));
  }

  /** Simulador del panel: conversación de prueba por usuario; nunca envía por WhatsApp ni crea leads reales. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  public UUID simulate(UUID userId, String userName, String text) {
    if (text == null || text.isBlank()) {
      throw new UnprocessableException("MESSAGE_EMPTY", "El mensaje está vacío.");
    }
    return inbound.receive(simulatorWaId(userId), userName + " (simulador)", text.trim(), null, true);
  }

  public void resetSimulation(UUID userId) {
    conversations.lockByWaId(simulatorWaId(userId)).ifPresent(conversations::delete);
  }

  public static String simulatorWaId(UUID userId) {
    return "sim" + userId.toString().replace("-", "").substring(0, 12);
  }

  private ConversationItem item(Conversation c, long openEscalations) {
    ConversationMessage last = messages.findRecent(c.getId(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    return new ConversationItem(
        c.getId(),
        c.getContactName(),
        c.isTest() ? null : "+" + c.getWaId(),
        c.isTest(),
        c.getControl().name(),
        Intent.parse(c.getPrimaryIntent()).label,
        c.getHandoffState().name(),
        c.getLeadId(),
        c.getNeedsAttention(),
        last == null ? null : last.getText(),
        last == null ? null : last.getRole().name(),
        c.getLastMessageAt(),
        openEscalations);
  }
}
