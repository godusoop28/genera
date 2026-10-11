package com.c21genera.assistant.application;

import com.c21genera.assistant.application.TurnModels.StoredProperty;
import com.c21genera.assistant.domain.AssistantPolicy.Fact;
import com.c21genera.assistant.domain.AssistantPolicy.Intent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Lectura/escritura de los campos JSON (texto) de la conversación. Tolerante: un JSON dañado no tumba el bot. */
@Component
class ConversationJson {

  record PreviousContext(String summary, List<StoredProperty> properties, String handoffState, String advisorName, String endedAt) {}

  private final ObjectMapper objectMapper;

  ConversationJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  Map<String, Fact> facts(String json) {
    try {
      return json == null ? new LinkedHashMap<>() : objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Fact>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  List<StoredProperty> properties(String json) {
    try {
      return json == null ? new ArrayList<>() : objectMapper.readValue(json, new TypeReference<ArrayList<StoredProperty>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  PreviousContext previous(String json) {
    try {
      return json == null ? null : objectMapper.readValue(json, PreviousContext.class);
    } catch (Exception e) {
      return null;
    }
  }

  String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static List<Intent> intents(String csv) {
    List<Intent> out = new ArrayList<>();
    if (csv == null || csv.isBlank()) {
      return out;
    }
    for (String part : csv.split(",")) {
      Intent i = Intent.parse(part.trim());
      if (i != Intent.UNKNOWN) {
        out.add(i);
      }
    }
    return out;
  }

  static String intentsCsv(List<Intent> intents) {
    return String.join(",", intents.stream().map(Enum::name).toList());
  }
}
