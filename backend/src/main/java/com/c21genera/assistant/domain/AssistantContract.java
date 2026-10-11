package com.c21genera.assistant.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrato entre el backend y el modelo (igual que Inova,
 * lib/conversation/assistant-contract.ts): herramientas acotadas con JSON
 * Schema estricto y el esquema de la salida final. Nada de SQL, URLs
 * arbitrarias, elegir asesor ni escribir estados de éxito: la IA propone,
 * el backend decide.
 */
public final class AssistantContract {

  private AssistantContract() {}

  public static final int MAX_ROUNDS = 4;
  public static final int MAX_TOOL_CALLS = 6;
  public static final int MAX_CORRECTIONS = 1;
  public static final int HISTORY_MESSAGES = 40;
  public static final int MAX_OUTPUT_TOKENS = 2500;

  public static final List<String> TOOL_NAMES =
      List.of("search_properties", "get_property", "check_assignment", "request_commercial_handoff", "request_management", "request_human");

  private static Map<String, Object> obj(Map<String, Object> properties, List<String> required) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", required);
    schema.put("additionalProperties", false);
    return schema;
  }

  private static Map<String, Object> str(String description) {
    return description == null ? Map.of("type", "string") : Map.of("type", "string", "description", description);
  }

  private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("type", "function");
    t.put("name", name);
    t.put("description", description);
    t.put("strict", true);
    t.put("parameters", parameters);
    return t;
  }

  public static final List<Map<String, Object>> TOOLS =
      List.of(
          tool(
              "search_properties",
              "Busca en el inventario en promoción de CENTURY 21 Genera (21Online) por texto: número de propiedad, clave, colonia, municipio, tipo, presupuesto o un enlace que mandó el cliente. Devuelve hasta 5 candidatas: no afirmes cuál vio el cliente sin que lo confirme.",
              obj(
                  Map.of(
                      "query", str("Lo que describe el cliente: número, clave, colonia, zona, tipo, características, presupuesto o el enlace completo."),
                      "operation", Map.of("type", "string", "enum", List.of("sale", "rental", "any"))),
                  List.of("query", "operation"))),
          tool(
              "get_property",
              "Consulta el detalle vigente de una propiedad en 21Online por su número (p. ej. 634294) o clave interna (p. ej. GN 14). Obligatorio antes de afirmar precio, disponibilidad o características.",
              obj(Map.of("reference", str("Número de propiedad o clave interna")), List.of("reference"))),
          tool("check_assignment", "Indica si este contacto ya tiene un asesor asignado o una canalización registrada.", obj(Map.of(), List.of())),
          tool(
              "request_commercial_handoff",
              "Solicita canalizar al cliente inmobiliario con un asesor usando las reglas de asignación de la empresa (el sistema elige al asesor, no tú). Úsala cuando la propiedad está identificada, cuando ya sabes qué busca/ofrece, o si pide hablar con alguien. El resultado dice qué ocurrió realmente.",
              obj(
                  Map.of(
                      "intent", Map.of("type", "string", "enum", AssistantPolicy.HANDOFF_INTENTS, "description", "Necesidad inmobiliaria principal."),
                      "reason", str("Motivo breve del contacto para el asesor."),
                      "property_reference", Map.of("type", List.of("string", "null"), "description", "Número de propiedad verificado, o null.")),
                  List.of("intent", "reason", "property_reference"))),
          tool(
              "request_management",
              "Registra un asunto para gerencia/administración: proveedores, colaboraciones, quejas u otros temas que no son de un cliente inmobiliario. Nunca asigna asesor comercial.",
              obj(
                  Map.of(
                      "category", Map.of("type", "string", "enum", List.of("provider", "management", "agent_collaboration", "complaint", "other")),
                      "reason", str(null)),
                  List.of("category", "reason"))),
          tool(
              "request_human",
              "El cliente pide hablar con una persona o la conversación no avanza: registra la solicitud para que alguien del equipo atienda.",
              obj(Map.of("reason", str(null)), List.of("reason"))));

  private static final Map<String, Object> FACT =
      obj(Map.of("value", Map.of("type", List.of("string", "null")), "status", Map.of("type", "string", "enum", List.of("known", "declined", "unknown"))), List.of("value", "status"));

  private static Map<String, Object> factsSchema() {
    Map<String, Object> props = new LinkedHashMap<>();
    for (String key : AssistantPolicy.FACT_KEYS) {
      props.put(key, FACT);
    }
    return obj(props, AssistantPolicy.FACT_KEYS);
  }

  private static final List<String> INTENTS = java.util.Arrays.stream(AssistantPolicy.Intent.values()).map(Enum::name).toList();

  public static final Map<String, Object> OUTPUT_SCHEMA;

  static {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("reply", str("Mensaje para el cliente por WhatsApp. Vacío solo si no corresponde responder."));
    props.put("primary_intent", Map.of("type", "string", "enum", INTENTS));
    props.put("secondary_intents", Map.of("type", "array", "items", Map.of("type", "string", "enum", INTENTS)));
    props.put("facts", factsSchema());
    props.put(
        "property_refs",
        Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description", "Números de propiedad por los que el cliente pregunta o que confirmó, SOLO si una herramienta los verificó."));
    props.put("summary", str("Resumen breve y factual de toda la conversación para el asesor (máx. 3 frases)."));
    props.put("asked_clarification", Map.of("type", "boolean"));
    props.put("made_progress", Map.of("type", "boolean"));
    OUTPUT_SCHEMA =
        obj(props, List.of("reply", "primary_intent", "secondary_intents", "facts", "property_refs", "summary", "asked_clarification", "made_progress"));
  }
}
