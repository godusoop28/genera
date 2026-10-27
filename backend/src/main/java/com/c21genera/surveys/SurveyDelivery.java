package com.c21genera.surveys;

import java.util.UUID;

/**
 * API pública del módulo surveys (Control de Cambios #28): el módulo del
 * asistente manda la encuesta por WhatsApp (ver SurveyEvents) y reporta aquí
 * el resultado. Si WhatsApp falla y el cliente tiene correo, se manda por correo.
 */
public interface SurveyDelivery {

  void recordWhatsAppDelivery(UUID surveyId, boolean sent, String detail);
}
