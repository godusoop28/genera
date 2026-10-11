package com.c21genera.assistant.application;

import com.c21genera.assistant.domain.AssistantPolicy.Fact;
import com.c21genera.assistant.domain.AssistantPolicy.Intent;
import com.c21genera.assistant.domain.Conversation.HandoffState;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Datos que entran y salen de un turno del asistente (sin entidades JPA: el turno corre fuera de transacción). */
final class TurnModels {

  private TurnModels() {}

  record HistoryMessage(String role, String text, boolean previousSession, boolean isNew) {}

  record StoredProperty(String ref, String title) {}

  /** Foto de la conversación al iniciar el turno. */
  record TurnInput(
      UUID conversationId,
      String waId,
      String contactName,
      boolean test,
      int sessionSeq,
      boolean firstReply,
      boolean returningSession,
      Intent primaryIntent,
      List<Intent> secondaryIntents,
      Map<String, Fact> facts,
      List<StoredProperty> sessionProperties,
      List<StoredProperty> previousProperties,
      String summary,
      HandoffState handoffState,
      boolean handoffInSession,
      int clarificationCount,
      String previousSessionText,
      List<HistoryMessage> history,
      /** Último mensaje incluido en este turno (se captura con la fila bloqueada). */
      long toSeq) {}

  record ToolTrace(String name, boolean ok, String note) {}

  /** Canalización que ocurrió en el turno (se aplica a la conversación al guardar). */
  record HandoffOutcome(HandoffState state, UUID leadId) {}

  record TurnOutput(
      String reply,
      Intent primaryIntent,
      List<Intent> secondaryIntents,
      Map<String, Fact> facts,
      List<StoredProperty> properties,
      String summary,
      boolean askedClarification,
      boolean madeProgress,
      HandoffOutcome handoff,
      List<ToolTrace> trace,
      int inputTokens,
      int outputTokens,
      int corrections,
      String model) {}
}
