package com.c21genera.shared.events;

import java.util.UUID;

/**
 * Quién ejecutó una acción, para que la bitácora nunca atribuya al cliente
 * algo que hizo el staff (o viceversa). Viaja dentro de los eventos de
 * integración; solo contiene el nombre y rol que ya viajan en el JWT del
 * usuario interno, nunca datos del cliente.
 */
public record Actor(ActorType type, UUID userId, String name, String role) {

  public enum ActorType {
    /** El cliente, a través de su liga pública (no tiene cuenta). */
    CLIENT,
    /** Un usuario interno autenticado (asesor, revisor, administrador, director...). */
    STAFF,
    /** Un proceso automático del sistema (procesamiento de archivos, IA, tareas programadas). */
    SYSTEM
  }

  public static Actor client() {
    return new Actor(ActorType.CLIENT, null, null, null);
  }

  public static Actor system() {
    return new Actor(ActorType.SYSTEM, null, null, null);
  }

  public static Actor staff(UUID userId, String name, String role) {
    return new Actor(ActorType.STAFF, userId, name, role);
  }

  public boolean isStaff() {
    return type == ActorType.STAFF;
  }
}
