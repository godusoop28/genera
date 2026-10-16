package com.c21genera.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** API pública del módulo identity: datos de contacto de un usuario interno (p. ej. para avisarle al asesor). */
public interface UserDirectory {

  Optional<UserContact> contactOf(UUID userId);

  /** Personal interno activo con su rol (p. ej. para elegir a qué asesor se asigna un lead). */
  List<StaffMember> activeStaff();

  /**
   * Control de Cambios #17: confirma una acción delicada (autorizar una inconsistencia) pidiendo
   * otra vez la contraseña del usuario activo.
   */
  boolean passwordMatches(UUID userId, String rawPassword);

  record UserContact(UUID id, String name, String email, boolean active) {}

  record StaffMember(UUID id, String name, String email, String roleCode) {}
}
