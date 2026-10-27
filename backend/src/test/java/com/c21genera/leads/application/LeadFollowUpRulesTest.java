package com.c21genera.leads.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadTask;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Controles de Cambios #29 y #30: reglas puras del seguimiento (sin base de datos). */
class LeadFollowUpRulesTest {

  private static final Instant NOW = Instant.parse("2026-10-26T18:00:00Z");
  private final LeadTaskService service = new LeadTaskService(null, null, null, null, Clock.fixed(NOW, ZoneOffset.UTC));

  private static Lead lead(LeadStage stage) {
    Lead lead = new Lead("Cliente", "7770000000", LeadSource.PHONE, null, LeadInterest.BUY, NOW);
    lead.changeStage(stage, null, NOW);
    return lead;
  }

  private static LeadTask done(LeadTask.Type type, LeadTask.Result result) {
    LeadTask t = new LeadTask(UUID.randomUUID(), type, null, null, null, null);
    t.complete(result, null, null, null, null, NOW);
    return t;
  }

  @Test
  void suggestsOnlyForwardStages() {
    assertThat(service.suggestedStage(lead(LeadStage.NEW), List.of(done(LeadTask.Type.CALL, LeadTask.Result.CONTACTED)))).isEqualTo(LeadStage.CONTACTED);
    assertThat(service.suggestedStage(lead(LeadStage.APPOINTMENT), List.of(done(LeadTask.Type.VISIT, LeadTask.Result.INTERESTED)))).isEqualTo(LeadStage.VISITED);
    // Ya va más adelante: no se sugiere retroceder.
    assertThat(service.suggestedStage(lead(LeadStage.NEGOTIATION), List.of(done(LeadTask.Type.VISIT, LeadTask.Result.INTERESTED)))).isNull();
    // Una llamada sin respuesta no avanza nada.
    assertThat(service.suggestedStage(lead(LeadStage.NEW), List.of(done(LeadTask.Type.CALL, LeadTask.Result.NO_ANSWER)))).isNull();
    // Un lead cerrado no recibe sugerencias.
    assertThat(service.suggestedStage(lead(LeadStage.WON), List.of(done(LeadTask.Type.PROPOSAL, LeadTask.Result.ADVANCED)))).isNull();
  }

  @Test
  void scheduledVisitSuggestsAppointment() {
    LeadTask visit = new LeadTask(UUID.randomUUID(), LeadTask.Type.VISIT, null, null, null, null);
    visit.schedule(NOW.plusSeconds(3600), 60, 60, null, null, null);
    assertThat(service.suggestedStage(lead(LeadStage.CONTACTED), List.of(visit))).isEqualTo(LeadStage.APPOINTMENT);
  }

  @Test
  void overdueMeansPastEndWithoutResult() {
    LeadTask call = new LeadTask(UUID.randomUUID(), LeadTask.Type.CALL, null, null, null, null);
    call.schedule(NOW.minusSeconds(1800), 60, null, null, null, null);
    assertThat(call.isOverdue(NOW)).isFalse(); // termina dentro de 30 min
    assertThat(call.isOverdue(NOW.plusSeconds(3600))).isTrue();
    call.complete(LeadTask.Result.CONTACTED, 10, null, null, null, NOW);
    assertThat(call.isOverdue(NOW.plusSeconds(3600))).isFalse();
  }

  @Test
  void sourceCodesFromLabels() {
    assertThat(LeadSourceCatalog.codeOf("Inmuebles24 — anuncio pagado")).isEqualTo("INMUEBLES24_ANUNCIO_PAGADO");
    assertThat(LeadSourceCatalog.codeOf("Recomendación: Notaría Pública")).isEqualTo("RECOMENDACION_NOTARIA_PUBLICA");
    assertThat(LeadSource.WHATSAPP_BOT.defaultCode()).isEqualTo("WHATSAPP_BOT");
  }

  @Test
  void newLeadStartsWithCatalogSourceAndStageDate() {
    Lead lead = new Lead("Cliente", null, LeadSource.WALK_IN, null, null, NOW);
    assertThat(lead.getSourceCode()).isEqualTo("WALK_IN");
    assertThat(lead.getStageEnteredAt()).isEqualTo(NOW);
    assertThat(lead.getFirstContactOn()).hasToString("2026-10-26");
  }
}
