package com.c21genera.assistant.application;

import com.c21genera.assistant.domain.Conversation;
import com.c21genera.assistant.domain.ConversationMessage;
import com.c21genera.assistant.infrastructure.ConversationRepository;
import com.c21genera.assistant.infrastructure.MessageRepository;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient.SendResult;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía en orden los mensajes en cola (respuestas del bot y del equipo).
 * Estados: QUEUED → SENDING → SENT | FAILED. Un tiempo de espera de Meta es
 * ambiguo (pudo llegar): se marca FAILED con aviso y no se reenvía solo.
 */
@Service
class OutboxSender {

  private static final int MAX_ATTEMPTS = 3;

  private final MessageRepository messages;
  private final ConversationRepository conversations;
  private final WhatsAppCloudClient whatsApp;
  private final TransactionTemplate tx;
  private final Clock clock;

  OutboxSender(MessageRepository messages, ConversationRepository conversations, WhatsAppCloudClient whatsApp, TransactionTemplate tx, Clock clock) {
    this.messages = messages;
    this.conversations = conversations;
    this.whatsApp = whatsApp;
    this.tx = tx;
    this.clock = clock;
  }

  void flush() {
    if (!whatsApp.canSend()) {
      return;
    }
    List<ConversationMessage> queued = messages.findQueued(PageRequest.of(0, 20));
    for (ConversationMessage m : queued) {
      Conversation c = conversations.findById(m.getConversationId()).orElse(null);
      if (c == null || c.isTest()) {
        continue;
      }
      tx.executeWithoutResult(s -> messages.findById(m.getId()).ifPresent(ConversationMessage::sending));
      SendResult result = whatsApp.sendText(c.getWaId(), m.getText());
      tx.executeWithoutResult(
          s ->
              messages
                  .findById(m.getId())
                  .ifPresent(
                      fresh -> {
                        if (result.ok()) {
                          fresh.sent(result.messageId(), clock.instant());
                        } else {
                          boolean last = !result.retryable() || result.uncertain() || fresh.getAttempts() >= MAX_ATTEMPTS;
                          fresh.failed(result.error(), last);
                        }
                      }));
    }
  }
}
