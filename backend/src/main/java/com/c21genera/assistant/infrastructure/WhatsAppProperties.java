package com.c21genera.assistant.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp Cloud API (Meta). Sin phoneNumberId/accessToken no se envía nada
 * (los mensajes quedan en el panel como no enviados).
 *
 * <p>verifyToken: lo inventas tú y lo pones igual en Meta al registrar el
 * webhook. appSecret: el "App Secret" de la app de Meta; con él se valida la
 * firma X-Hub-Signature-256 de cada llamada (sin él se rechazan todas).
 *
 * <p>advisorTemplate: plantilla aprobada por Meta para avisar al asesor fuera
 * de la ventana de 24 h, con un solo parámetro de cuerpo (el aviso). Vacío =
 * se intenta texto libre, que solo llega si el asesor escribió en las últimas
 * 24 h.
 */
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
    String apiVersion,
    String phoneNumberId,
    String accessToken,
    String verifyToken,
    String appSecret,
    String advisorTemplate,
    String advisorTemplateLanguage,
    // Control de Cambios #28: plantilla de la encuesta NPS al cliente (mismo idioma que la del asesor).
    String surveyTemplate) {

  public WhatsAppProperties {
    if (apiVersion == null || apiVersion.isBlank()) {
      apiVersion = "v23.0";
    }
    if (advisorTemplateLanguage == null || advisorTemplateLanguage.isBlank()) {
      advisorTemplateLanguage = "es_MX";
    }
  }

  public boolean canSend() {
    return phoneNumberId != null && !phoneNumberId.isBlank() && accessToken != null && !accessToken.isBlank();
  }
}
