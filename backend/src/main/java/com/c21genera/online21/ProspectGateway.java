package com.c21genera.online21;

import java.util.Optional;

/**
 * API pública del módulo online21: registra un lead como prospecto en
 * 21Online (POST /api/prospectos) para que el asesor lo vea en la
 * herramienta que ya usa. Desactivado por defecto
 * ({@code app.online21.push-prospects=false}).
 */
public interface ProspectGateway {

  boolean enabled();

  /** Devuelve el id del prospecto creado, o vacío si no se pudo (el lead sigue igual en Genera). */
  Optional<Long> createProspect(ProspectRequest request);

  record ProspectRequest(String name, String phone, String email, Long propertyOnline21Id, Long advisorOnline21Id, String channel, String message) {}
}
