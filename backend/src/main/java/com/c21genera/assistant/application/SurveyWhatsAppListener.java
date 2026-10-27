package com.c21genera.assistant.application;

import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient.SendResult;
import com.c21genera.assistant.infrastructure.WhatsAppProperties;
import com.c21genera.shared.events.SurveyEvents.SurveyWhatsAppRequested;
import com.c21genera.surveys.SurveyDelivery;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Control de Cambios #28: manda la encuesta NPS al cliente por WhatsApp. Con
 * plantilla aprobada (WHATSAPP_SURVEY_TEMPLATE) llega aunque el cliente no
 * haya escrito en 24 h; sin plantilla se intenta como texto libre. El
 * resultado vuelve a surveys (si falla y hay correo, se manda por correo).
 */
@Component
class SurveyWhatsAppListener {

  private final WhatsAppCloudClient whatsApp;
  private final WhatsAppProperties properties;
  private final SurveyDelivery surveys;

  SurveyWhatsAppListener(WhatsAppCloudClient whatsApp, WhatsAppProperties properties, SurveyDelivery surveys) {
    this.whatsApp = whatsApp;
    this.properties = properties;
    this.surveys = surveys;
  }

  @ApplicationModuleListener
  void on(SurveyWhatsAppRequested event) {
    if (!whatsApp.canSend()) {
      surveys.recordWhatsAppDelivery(event.surveyId(), false, "WhatsApp Cloud API no está configurada");
      return;
    }
    String to = toWhatsAppNumber(event.phone());
    SendResult result =
        properties.surveyTemplate() != null && !properties.surveyTemplate().isBlank()
            ? whatsApp.sendTemplate(to, properties.surveyTemplate(), properties.advisorTemplateLanguage(), event.text())
            : whatsApp.sendText(to, event.text());
    surveys.recordWhatsAppDelivery(event.surveyId(), result.ok(), result.error());
  }

  /** 10 dígitos de México sin lada de país: se antepone 52 (formato que espera la Cloud API). */
  static String toWhatsAppNumber(String phone) {
    String digits = phone.replaceAll("\\D", "");
    return digits.length() == 10 ? "52" + digits : digits;
  }
}
