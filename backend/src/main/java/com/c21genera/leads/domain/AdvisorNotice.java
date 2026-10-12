package com.c21genera.leads.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Ficha del lead que recibe el asesor por WhatsApp (como lib/advisor-notice.ts
 * de Inova): todo en un mensaje, sin tener que entrar al panel. Pura: sin red
 * ni base de datos.
 *
 * <p>Solo datos públicos de la propiedad: nunca dueño, número exterior, clave
 * catastral ni comisiones. Prioridad cuando no cabe: datos del cliente, ficha y
 * enlace siempre; luego lo que busca; la descripción se recorta al final.
 *
 * <p>Dos tamaños: {@link #TEXT_MAX} para texto libre y {@link #TEMPLATE_PARAM_MAX}
 * para el parámetro de una plantilla de WhatsApp (el cuerpo completo, ya
 * sustituido, no puede pasar de 1024 caracteres; se reserva espacio para el
 * texto fijo de la plantilla).
 */
public final class AdvisorNotice {

  public static final int TEXT_MAX = 1500;
  public static final int TEMPLATE_PARAM_MAX = 850;

  private AdvisorNotice() {}

  public record PropertySheet(
      String ref,
      String internalKey,
      String title,
      String type,
      String operation,
      String price,
      String location,
      Integer bedrooms,
      String bathrooms,
      Integer parking,
      String builtM2,
      String landM2,
      String description,
      String url) {}

  public record ClientInfo(String name, String phone, String interest, String source, String budget, String summary, String assignmentReason, String coordination) {}

  /**
   * @param headline          primera línea (p. ej. "Nuevo lead de CENTURY 21 Genera" o "Seguimiento de tu cliente")
   * @param property          ficha de 21Online, o null
   * @param propertyReference referencia que dio el cliente cuando no hay ficha (clave, título guardado)
   */
  public static String build(String headline, ClientInfo client, PropertySheet property, String propertyReference, int max) {
    List<String> blocks = new ArrayList<>();
    blocks.add("*" + headline + "*");

    List<String> contact = new ArrayList<>();
    contact.add("Cliente: " + clean(client.name()));
    if (client.phone() != null) {
      contact.add("WhatsApp: " + client.phone());
    }
    if (client.assignmentReason() != null) {
      contact.add("Asignación: " + clean(client.assignmentReason()));
    }
    blocks.add(String.join("\n", contact));

    if (client.coordination() != null) {
      blocks.add("⚠️ Coordinar: " + clean(client.coordination()));
    }

    String sheet = property != null ? String.join("\n", sheetLines(property)) : null;
    String link =
        property == null
            ? null
            : property.url() != null ? "🔗 " + property.url() : "Ficha en 21Online: " + property.ref() + (property.internalKey() != null ? " (" + property.internalKey() + ")" : "");
    if (sheet == null && propertyReference != null && !propertyReference.isBlank()) {
      sheet = "*🏠 PROPIEDAD*\n" + clean(propertyReference);
    }

    List<String> wants = new ArrayList<>();
    if (client.interest() != null) {
      wants.add("Busca: " + client.interest());
    }
    if (client.budget() != null) {
      wants.add("Presupuesto: " + client.budget());
    }
    if (client.source() != null) {
      wants.add("Llegó por: " + clean(client.source()));
    }
    if (client.summary() != null && !client.summary().isBlank()) {
      wants.add("Lo que dijo: " + clean(client.summary()));
    }
    String wantsBlock = wants.isEmpty() ? null : "*👤 LO QUE BUSCA*\n" + String.join("\n", wants);

    List<String> fixed = new ArrayList<>(blocks);
    if (sheet != null) {
      fixed.add(sheet);
    }
    if (link != null) {
      fixed.add(link);
    }
    if (wantsBlock != null) {
      fixed.add(wantsBlock);
    }
    String withoutDescription = String.join("\n\n", fixed);

    String description = property != null && property.description() != null ? clean(property.description()) : "";
    int room = max - withoutDescription.length() - "\nDescripción: ".length() - 2;
    if (!description.isEmpty() && room > 80 && sheet != null) {
      int index = fixed.indexOf(sheet);
      fixed.set(index, sheet + "\nDescripción: " + truncate(description, room));
    }
    String text = String.join("\n\n", fixed);
    return text.length() <= max ? text : truncate(text, max);
  }

  static List<String> sheetLines(PropertySheet p) {
    List<String> lines = new ArrayList<>();
    lines.add("*🏠 PROPIEDAD DE INTERÉS*");
    if (p.title() != null) {
      lines.add("*" + clean(p.title()) + "*");
    }
    lines.add("Número: " + p.ref() + (p.internalKey() != null ? " · Clave " + p.internalKey() : ""));
    List<String> kind = new ArrayList<>();
    if (p.type() != null) {
      kind.add(p.type());
    }
    if (p.operation() != null) {
      kind.add("en " + p.operation());
    }
    if (!kind.isEmpty()) {
      lines.add("Tipo: " + String.join(" ", kind));
    }
    lines.add("Precio: " + (p.price() != null ? p.price() : "a consultar"));
    if (p.location() != null) {
      lines.add("Ubicación: " + p.location());
    }
    List<String> rooms = new ArrayList<>();
    if (p.bedrooms() != null) {
      rooms.add(p.bedrooms() + " recámara(s)");
    }
    if (p.bathrooms() != null) {
      rooms.add(p.bathrooms() + " baño(s)");
    }
    if (p.parking() != null) {
      rooms.add(p.parking() + " estacionamiento(s)");
    }
    if (!rooms.isEmpty()) {
      lines.add(String.join(" · ", rooms));
    }
    List<String> sizes = new ArrayList<>();
    if (p.builtM2() != null) {
      sizes.add("Construcción " + p.builtM2() + " m²");
    }
    if (p.landM2() != null) {
      sizes.add("Terreno " + p.landM2() + " m²");
    }
    if (!sizes.isEmpty()) {
      lines.add(String.join(" · ", sizes));
    }
    return lines;
  }

  static String clean(String text) {
    return text == null ? "" : text.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
  }

  static String truncate(String text, int max) {
    if (text.length() <= max) {
      return text;
    }
    if (max <= 1) {
      return "";
    }
    String cut = text.substring(0, max - 1);
    int lastSpace = cut.lastIndexOf(' ');
    return (lastSpace > max * 0.6 ? cut.substring(0, lastSpace) : cut).stripTrailing() + "…";
  }
}
