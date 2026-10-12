package com.c21genera.leads;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.leads.domain.AdvisorNotice;
import com.c21genera.leads.domain.AdvisorNotice.ClientInfo;
import com.c21genera.leads.domain.AdvisorNotice.PropertySheet;
import org.junit.jupiter.api.Test;

class AdvisorNoticeTest {

  private final ClientInfo client =
      new ClientInfo("Daniela Ruiz", "+52 777 555 0142", "comprar", "Asistente de WhatsApp · Facebook", "$6,000,000 MXN", "Pregunta si aceptan crédito Infonavit.", "Prioridad: asesor responsable de la propiedad (en turno)", null);

  private final PropertySheet sheet =
      new PropertySheet(
          "634294", "GN 14", "Casa nueva con alberca en Burgos", "Casa", "venta", "$5,980,000 MXN", "Retorno de Fátima, Burgos, Temixco", 4, "4", 2, "330", "430",
          "Casa nueva en privada con acceso controlado y vigilancia 24 h. ".repeat(40), "https://example.test/propiedad/634294");

  @Test
  void fullSheetHasClientPropertyAndLink() {
    String text = AdvisorNotice.build("Nuevo lead de CENTURY 21 Genera", client, sheet, null, AdvisorNotice.TEXT_MAX);
    assertThat(text)
        .contains("Daniela Ruiz", "+52 777 555 0142", "Número: 634294 · Clave GN 14", "Precio: $5,980,000 MXN", "Ubicación: Retorno de Fátima, Burgos, Temixco")
        .contains("4 recámara(s) · 4 baño(s) · 2 estacionamiento(s)", "Construcción 330 m² · Terreno 430 m²", "https://example.test/propiedad/634294")
        .contains("Presupuesto: $6,000,000 MXN", "Infonavit", "Asignación: Prioridad");
    assertThat(text.length()).isLessThanOrEqualTo(AdvisorNotice.TEXT_MAX);
  }

  @Test
  void templateVersionFitsAndKeepsTheEssentialsCuttingTheDescriptionFirst() {
    String text = AdvisorNotice.build("Nuevo lead de CENTURY 21 Genera", client, sheet, null, AdvisorNotice.TEMPLATE_PARAM_MAX);
    assertThat(text.length()).isLessThanOrEqualTo(AdvisorNotice.TEMPLATE_PARAM_MAX);
    assertThat(text).contains("Daniela Ruiz", "+52 777 555 0142", "634294", "Precio:", "https://example.test/propiedad/634294");
  }

  @Test
  void withoutSheetUsesTheSavedReferenceAndShowsCoordination() {
    ClientInfo conflict = new ClientInfo("Luis", null, "rentar", "Oficina", null, null, null, "Pregunta por GN 14, cuyo asesor es Ana; lo atiende Luis.");
    String text = AdvisorNotice.build("Tu cliente volvió a escribir", conflict, null, "GN 14 · Casa en Burgos, 634294--649", AdvisorNotice.TEXT_MAX);
    assertThat(text).contains("Tu cliente volvió a escribir", "GN 14 · Casa en Burgos", "⚠️ Coordinar:");
  }
}
