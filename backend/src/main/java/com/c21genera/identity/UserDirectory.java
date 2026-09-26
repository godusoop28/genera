package com.c21genera.identity;

import java.util.Optional;
import java.util.UUID;

/** API pública del módulo identity: datos de contacto de un usuario interno (p. ej. para avisarle al asesor). */
public interface UserDirectory {

  Optional<UserContact> contactOf(UUID userId);

  record UserContact(UUID id, String name, String email, boolean active) {}
}
