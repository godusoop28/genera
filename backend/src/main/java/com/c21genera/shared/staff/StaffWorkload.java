package com.c21genera.shared.staff;

import java.util.List;
import java.util.UUID;

/**
 * Control de Cambios #25: lo que un usuario interno tiene asignado (expedientes
 * en curso, leads abiertos...). Cada módulo dueño de esos datos lo implementa;
 * identity lo consulta antes de dar de baja una cuenta para pedir que se
 * reasigne primero, sin depender de los módulos de negocio.
 */
public interface StaffWorkload {

  List<Item> assignedTo(UUID userId);

  /** label: qué es ("Expedientes en curso"); count: cuántos. */
  record Item(String label, long count) {}
}
