package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por surveys (Control de Cambios #28). */
public final class SurveyEvents {

  private SurveyEvents() {}

  /**
   * Hay que mandar la encuesta NPS por WhatsApp. assistant la envía (con la
   * plantilla aprobada si existe; si no, como texto libre) y reporta el
   * resultado a surveys.SurveyDelivery. text: mensaje con la liga.
   */
  public record SurveyWhatsAppRequested(UUID surveyId, String phone, String text) {}
}
