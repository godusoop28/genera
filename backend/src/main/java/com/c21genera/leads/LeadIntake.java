package com.c21genera.leads;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública del módulo leads para canales automáticos (el asistente de
 * WhatsApp). La IA nunca elige asesor: pide la canalización y este módulo
 * decide con las reglas de la empresa y devuelve lo que realmente ocurrió.
 */
public interface LeadIntake {

  HandoffResult handoff(HandoffRequest request);

  /** Lead reciente (ventana de 30 días) del mismo teléfono, para no reasignar a quien ya tiene asesor. */
  Optional<RecentLead> recentLeadFor(String phone);

  /** Resultado del aviso por WhatsApp al asesor (lo envía el módulo del asistente). */
  void recordAdvisorNotice(UUID leadId, boolean sent, String detail);

  /**
   * @param requestKey  llave de idempotencia: repetir la misma petición no crea otro lead
   * @param dryRun      simulador: elige asesor pero no guarda nada
   */
  record HandoffRequest(
      String requestKey,
      String name,
      String phone,
      LeadSource source,
      String sourceDetail,
      LeadInterest interest,
      PropertyRef property,
      String summary,
      BigDecimal budget,
      UUID conversationId,
      boolean dryRun) {}

  /** Propiedad de 21Online identificada con certeza (nunca una "parecida"). */
  record PropertyRef(long online21Id, String key, String title, Long advisorOnline21Id) {}

  enum HandoffStatus {
    /** Lead nuevo asignado a un asesor. */
    ASSIGNED,
    /** Ya tenía un lead reciente con asesor: se anexó la nueva solicitud. */
    EXISTING,
    /** Se registró el lead pero no había asesor disponible: queda para el equipo. */
    NO_ADVISOR
  }

  record HandoffResult(HandoffStatus status, UUID leadId, String advisorName, AssignmentMethod method) {}

  record RecentLead(UUID leadId, String advisorName, LeadStage stage) {}
}
