package com.c21genera.leads.application;

import com.c21genera.identity.UserDirectory;
import com.c21genera.identity.UserDirectory.StaffMember;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Nombres del personal (vía la API pública de identity) para mostrarlos junto a cada lead. */
@Component
class StaffNames {

  static final ZoneId MEXICO_CITY = ZoneId.of("America/Mexico_City");

  private final UserDirectory userDirectory;

  StaffNames(UserDirectory userDirectory) {
    this.userDirectory = userDirectory;
  }

  List<StaffMember> activeStaff() {
    return userDirectory.activeStaff();
  }

  Map<UUID, String> names() {
    Map<UUID, String> map = new HashMap<>();
    for (StaffMember m : userDirectory.activeStaff()) {
      map.put(m.id(), m.name());
    }
    return map;
  }

  /** Nombre de un usuario aunque ya esté inactivo (los leads viejos lo siguen mostrando). */
  String nameOf(UUID userId, Map<UUID, String> cache) {
    if (userId == null) {
      return null;
    }
    return cache.computeIfAbsent(userId, id -> userDirectory.contactOf(id).map(UserDirectory.UserContact::name).orElse("Usuario eliminado"));
  }
}
