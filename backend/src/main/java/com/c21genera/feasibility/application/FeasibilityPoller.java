package com.c21genera.feasibility.application;

import com.c21genera.feasibility.domain.FeasibilityReport;
import com.c21genera.shared.jobs.MemoryRelief;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hace avanzar las factibilidades en curso. Consultar a OpenAI tarda milisegundos, así que no
 * ocupa el único hilo de trabajos programados; solo el armado final (mapa, Word y PowerPoint)
 * pesa algunos segundos, y después se libera memoria como en los demás trabajos pesados.
 */
@Component
class FeasibilityPoller {

  private final FeasibilityService service;

  FeasibilityPoller(FeasibilityService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT30S")
  void poll() {
    for (FeasibilityReport report : service.inProgress()) {
      FeasibilityReport.Status before = report.getStatus();
      service.advance(report);
      if (before == FeasibilityReport.Status.PRESENTING && report.getStatus() != before) {
        MemoryRelief.afterHeavyWork();
      }
    }
  }
}
