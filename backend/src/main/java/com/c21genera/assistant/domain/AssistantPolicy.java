package com.c21genera.assistant.domain;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de negocio del asistente que NO dependen de la IA (las mismas que
 * Inova, lib/conversation/policy.ts). La IA propone; estas funciones deciden.
 * Puras para poder probarlas.
 */
public final class AssistantPolicy {

  private AssistantPolicy() {}

  public enum Intent {
    UNKNOWN("Sin clasificar"),
    PROPERTY_INQUIRY("Propiedad concreta"),
    BUY("Comprar"),
    RENT("Rentar"),
    SELL("Vender inmueble propio"),
    LEASE_OUT("Poner en renta inmueble propio"),
    INVEST("Inversión"),
    AGENT_COLLABORATION("Colaboración con asesor externo"),
    PROVIDER("Proveedor / servicios"),
    MANAGEMENT("Gerencia / administración"),
    HUMAN_REQUEST("Pide atención humana"),
    OTHER("Otro");

    public final String label;

    Intent(String label) {
      this.label = label;
    }

    public static Intent parse(String value) {
      try {
        return value == null ? UNKNOWN : Intent.valueOf(value);
      } catch (IllegalArgumentException e) {
        return UNKNOWN;
      }
    }
  }

  private static final Set<Intent> REAL_ESTATE = EnumSet.of(Intent.PROPERTY_INQUIRY, Intent.BUY, Intent.RENT, Intent.SELL, Intent.LEASE_OUT, Intent.INVEST);
  private static final Set<Intent> NON_COMMERCIAL = EnumSet.of(Intent.PROVIDER, Intent.MANAGEMENT);

  /** Intenciones con las que se puede pedir canalización comercial. */
  public static final List<String> HANDOFF_INTENTS = List.of("PROPERTY_INQUIRY", "BUY", "RENT", "SELL", "LEASE_OUT", "INVEST", "AGENT_COLLABORATION");

  public static boolean isRealEstate(Intent intent) {
    return REAL_ESTATE.contains(intent);
  }

  public static boolean hasRealEstateInterest(Intent primary, List<Intent> secondary) {
    return isRealEstate(primary) || secondary.stream().anyMatch(AssistantPolicy::isRealEstate);
  }

  public enum Trigger {
    ASSISTANT,
    HUMAN_REQUEST,
    ABANDONMENT
  }

  public record HandoffDecision(boolean allowed, String reason) {}

  /**
   * ¿Puede entrar a la asignación comercial?
   *
   * <ul>
   *   <li>Proveedor/gerencia sin interés inmobiliario: nunca.
   *   <li>Con propiedad identificada y algún interés inmobiliario (o colaboración, o pidió persona): sí.
   *   <li>Sin propiedad: solo con interés inmobiliario confirmado.
   * </ul>
   */
  public static HandoffDecision decideHandoff(Intent primary, List<Intent> secondary, boolean propertyIdentified, Trigger trigger) {
    if (NON_COMMERCIAL.contains(primary) && secondary.stream().noneMatch(AssistantPolicy::isRealEstate)) {
      return new HandoffDecision(false, "Proveedor/gerencia no entra a la asignación comercial.");
    }
    if (propertyIdentified
        && (hasRealEstateInterest(primary, secondary) || primary == Intent.AGENT_COLLABORATION || trigger == Trigger.HUMAN_REQUEST)) {
      return new HandoffDecision(true, null);
    }
    if (!hasRealEstateInterest(primary, secondary)) {
      return new HandoffDecision(
          false,
          primary == Intent.AGENT_COLLABORATION
              ? "Colaboración sin propiedad identificada: se canaliza a gerencia."
              : "Todavía no hay interés inmobiliario confirmado.");
    }
    return new HandoffDecision(true, null);
  }

  // -------------------------------------------------------------------------
  // Datos recopilados
  // -------------------------------------------------------------------------

  public static final List<String> FACT_KEYS =
      List.of("name", "operation", "property_type", "zone", "budget_min", "budget_max", "currency", "bedrooms", "timeframe", "own_property_location", "financing", "heard_from", "notes");

  public static final Map<String, String> FACT_LABELS =
      Map.ofEntries(
          Map.entry("name", "Nombre"),
          Map.entry("operation", "Operación"),
          Map.entry("property_type", "Tipo de inmueble"),
          Map.entry("zone", "Zona"),
          Map.entry("budget_min", "Presupuesto mínimo"),
          Map.entry("budget_max", "Presupuesto máximo"),
          Map.entry("currency", "Moneda"),
          Map.entry("bedrooms", "Recámaras"),
          Map.entry("timeframe", "Plazo"),
          Map.entry("own_property_location", "Ubicación de su inmueble"),
          Map.entry("financing", "Forma de pago"),
          Map.entry("heard_from", "Dónde vio la propiedad / cómo nos conoció (dicho por el cliente)"),
          Map.entry("notes", "Notas"));

  /** Un dato conocido o "prefirió no decirlo" (declined = null value). */
  public record Fact(String value, String status) {}

  /**
   * Fusiona lo que reporta la IA con lo guardado: "known" reemplaza (el
   * cliente puede corregirse), "declined" se respeta y no se vuelve a
   * preguntar, "unknown" nunca borra un dato ya conocido.
   */
  public static Map<String, Fact> mergeFacts(Map<String, Fact> previous, Map<String, Fact> update) {
    Map<String, Fact> merged = new LinkedHashMap<>(previous);
    for (String key : FACT_KEYS) {
      Fact incoming = update.get(key);
      if (incoming == null || "unknown".equals(incoming.status())) {
        continue;
      }
      if ("declined".equals(incoming.status())) {
        merged.put(key, new Fact(null, "declined"));
        continue;
      }
      String value = incoming.value() == null ? "" : incoming.value().trim();
      if (!value.isEmpty()) {
        merged.put(key, new Fact(value.length() > 300 ? value.substring(0, 300) : value, "known"));
      }
    }
    return merged;
  }

  // -------------------------------------------------------------------------
  // Controles de verdad sobre el texto de la respuesta
  // -------------------------------------------------------------------------

  private static final Pattern INTERNAL_PROCESS = Pattern.compile("\\bruleta\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** Promesas de disponibilidad que el bot no puede confirmar. */
  private static final Pattern AVAILABILITY_PROMISE =
      Pattern.compile("\\b(está disponible (?:ahora|en este momento)|te (?:llama|escribe|contacta) (?:ahora|en (?:unos )?minutos|de inmediato)|en (?:unos )?minutos te)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  public static boolean promisesAvailability(String reply) {
    return AVAILABILITY_PROMISE.matcher(reply).find();
  }

  private static final Pattern ASSIGNMENT_CLAIM =
      Pattern.compile(
          "\\b(asign(?:é|e|ado|ada|amos)|te canaliz(?:o|é|amos|aré)|(?:ya )?registr(?:é|amos) tu solicitud|te (?:va|van) a contactar|te contactar(?:á|a|án|an)|ya (?:le )?avis(?:é|e|amos)|notifiqu(?:é|e)|ya (?:tienes|tiene) (?:un )?asesor)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  public static boolean mentionsInternalProcess(String reply) {
    return INTERNAL_PROCESS.matcher(reply).find();
  }

  /** El backend lo compara con lo que realmente ocurrió: la IA no puede decir "ya te asigné" si no pasó. */
  public static boolean claimsAssignment(String reply) {
    return ASSIGNMENT_CLAIM.matcher(reply).find();
  }

  public static String stripInternalProcess(String reply) {
    return reply.replaceAll("(?iu)\\s*\\(?[^.()]*\\bruleta\\b[^.()]*\\)?\\.?", ".").replaceAll("\\.\\.+", ".");
  }
}
