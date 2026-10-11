package com.c21genera.online21.infrastructure;

import com.c21genera.online21.ProspectGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Registra el lead como prospecto en 21Online. Campos según el esquema
 * "Prospectos-prospectos.create" de su API (nombre, telefono, email,
 * idPropiedades, asignado, mensaje). Pendiente de validar con una llave real
 * qué valores acepta "asignado" y "medio"; por eso está apagado por defecto.
 */
@Service
class Online21ProspectGateway implements ProspectGateway {

  private static final Logger log = LoggerFactory.getLogger(Online21ProspectGateway.class);

  private final Online21Client client;
  private final Online21Properties properties;

  Online21ProspectGateway(Online21Client client, Online21Properties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public boolean enabled() {
    return properties.pushProspects() && client.configured();
  }

  @Override
  public Optional<Long> createProspect(ProspectRequest request) {
    if (!enabled()) {
      return Optional.empty();
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("nombre", request.name());
    payload.put("telefono", request.phone());
    if (request.email() != null) {
      payload.put("email", request.email());
    }
    if (request.propertyOnline21Id() != null) {
      payload.put("idPropiedades", request.propertyOnline21Id());
    }
    if (request.advisorOnline21Id() != null) {
      payload.put("asignado", request.advisorOnline21Id());
    }
    payload.put("mensaje", (request.channel() != null ? "[" + request.channel() + "] " : "") + (request.message() != null ? request.message() : ""));
    try {
      return Optional.ofNullable(client.createProspect(payload));
    } catch (RuntimeException e) {
      log.warn("No se pudo registrar el prospecto en 21Online ({})", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
