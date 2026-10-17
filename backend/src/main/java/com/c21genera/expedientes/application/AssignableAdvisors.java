package com.c21genera.expedientes.application;

import com.c21genera.identity.UserDirectory;
import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.shared.domain.UnprocessableException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Control de Cambios #24: a quién se puede asignar un expediente. Solo personal activo que
 * atiende clientes (los mismos roles que reciben leads); los asesores aparecen primero. El
 * revisor de documentos no lleva cartera.
 */
@Component
public class AssignableAdvisors {

  private static final List<String> ASSIGNABLE_ROLES = List.of("ADVISOR", "DIRECTOR", "ADMINISTRATOR");

  private final UserDirectory userDirectory;

  public AssignableAdvisors(UserDirectory userDirectory) {
    this.userDirectory = userDirectory;
  }

  public List<StaffMember> list() {
    return userDirectory.activeStaff().stream()
        .filter(m -> ASSIGNABLE_ROLES.contains(m.roleCode()))
        .sorted(Comparator.comparing((StaffMember m) -> ASSIGNABLE_ROLES.indexOf(m.roleCode())).thenComparing(StaffMember::name))
        .toList();
  }

  public StaffMember require(UUID advisorId) {
    if (advisorId == null) {
      throw new UnprocessableException("ADVISOR_REQUIRED", "Selecciona el asesor asignado al inmueble.");
    }
    return list().stream()
        .filter(m -> m.id().equals(advisorId))
        .findFirst()
        .orElseThrow(() -> new UnprocessableException("ADVISOR_INACTIVE", "Ese asesor no está activo. Elige otro de la lista."));
  }
}
