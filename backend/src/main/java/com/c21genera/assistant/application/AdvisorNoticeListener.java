package com.c21genera.assistant.application;

import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import com.c21genera.assistant.infrastructure.WhatsAppCloudClient.SendResult;
import com.c21genera.assistant.infrastructure.WhatsAppProperties;
import com.c21genera.leads.LeadIntake;
import com.c21genera.shared.events.LeadEvents.LeadAssigned;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Manda al asesor la ficha del lead por WhatsApp (como el aviso de ManyChat en
 * Inova). Con plantilla aprobada (WHATSAPP_ADVISOR_TEMPLATE) llega aunque el
 * asesor no haya escrito en 24 h, con la ficha compacta en una sola línea
 * (WhatsApp no permite saltos de línea en parámetros); sin plantilla se manda
 * la ficha completa como texto libre, que solo llega dentro de la ventana de
 * 24 h. El resultado queda en el historial del lead.
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
            ? whatsApp.sendTemplate(to, properties.advisorTemplate(), properties.advisorTemplateLanguage(), event.compactNotice())
            : whatsApp.sendText(to, event.notice());
    leads.recordAdvisorNotice(event.leadId(), result.ok(), result.error());
  }
}
