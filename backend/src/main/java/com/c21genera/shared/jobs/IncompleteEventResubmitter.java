package com.c21genera.shared.jobs;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vuelve a entregar los eventos de dominio que quedaron sin procesar. Pasa en
 * los despliegues de Render: la instancia vieja sigue atendiendo unos segundos
 * mientras arranca la nueva y, si la apagan justo después de procesar un
 * archivo pero antes de que su listener encole la extracción con IA, el
 * documento se quedaba "procesando" para siempre (E2E 02/10: una INE subida
 * durante el despliegue de 389df90). republish-outstanding-events-on-restart
 * solo los reenvía al ARRANCAR, y la instancia nueva ya había arrancado.
 *
 * <p>Los listeners del proyecto son rápidos (encolan un trabajo, registran
 * auditoría): un evento con más de 10 minutos sin completar no está en curso,
 * se perdió.
 */
@Component
class IncompleteEventResubmitter {

  private static final Logger log = LoggerFactory.getLogger(IncompleteEventResubmitter.class);
  static final Duration OLDER_THAN = Duration.ofMinutes(10);

  private final ObjectProvider<IncompleteEventPublications> incomplete;

  IncompleteEventResubmitter(ObjectProvider<IncompleteEventPublications> incomplete) {
    this.incomplete = incomplete;
  }

  @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT5M")
  void resubmit() {
    IncompleteEventPublications publications = incomplete.getIfAvailable();
    if (publications == null) {
      return;
    }
    try {
      publications.resubmitIncompletePublicationsOlderThan(OLDER_THAN);
    } catch (RuntimeException e) {
      log.warn("No se pudieron reenviar los eventos pendientes: {}", e.getMessage());
    }
  }
}
