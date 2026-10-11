package com.c21genera.assistant.application;

import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient.SendResult;
import com.c21genera.assistant.infrastructure.WhatsAppProperties;
import com.c21genera.leads.LeadIntake;
import com.c21genera.shared.events.LeadEvents.LeadAssigned;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Avisa al asesor por WhatsApp cuando se le asigna un lead (como el aviso de
 * ManyChat en Inova). Con plantilla aprobada llega aunque el asesor no haya
 * escrito en 24 h; sin plantilla se intenta texto libre. El resultado queda en
 * el historial del lead.
 */
@Component
class AdvisorNoticeListener {

  private final WhatsAppCloudClient whatsApp;
  private final WhatsAppProperties properties;
  private final LeadIntake leads;

  AdvisorNoticeListener(WhatsAppCloudClient whatsApp, WhatsAppProperties properties, LeadIntake leads) {
    this.whatsApp = whatsApp;
    this.properties = properties;
    this.leads = leads;
  }

  @ApplicationModuleListener
  void on(LeadAssigned event) {
    if (event.advisorWhatsapp() == null || event.advisorWhatsapp().isBlank()) {
      return;
    }
    if (!whatsApp.canSend()) {
      leads.recordAdvisorNotice(event.leadId(), false, "WhatsApp Cloud API no está configurada");
      return;
    }
    String to = event.advisorWhatsapp().replaceAll("\\D", "");
    SendResult result =
        properties.advisorTemplate() != null && !properties.advisorTemplate().isBlank()
            ? whatsApp.sendTemplate(to, properties.advisorTemplate(), properties.advisorTemplateLanguage(), event.notice())
            : whatsApp.sendText(to, event.notice());
    leads.recordAdvisorNotice(event.leadId(), result.ok(), result.error());
  }
}
