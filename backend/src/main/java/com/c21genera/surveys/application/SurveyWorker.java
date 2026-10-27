package com.c21genera.surveys.application;

import com.c21genera.surveys.domain.NpsSurvey;
import com.c21genera.surveys.domain.SurveyChannel;
import com.c21genera.surveys.domain.SurveySettings;
import com.c21genera.surveys.domain.SurveyStatus;
import com.c21genera.surveys.infrastructure.NpsSurveyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Control de Cambios #28: envía las encuestas cuyo retraso ya se cumplió,
 * manda el recordatorio a quien no ha respondido (configurable) y marca como
 * vencidas las que pasaron su vigencia.
 */
@Component
class SurveyWorker {

  private static final Logger log = LoggerFactory.getLogger(SurveyWorker.class);

  private final NpsSurveyRepository surveys;
  private final SurveyService service;
  private final TransactionTemplate tx;
  private final Clock clock;

  SurveyWorker(NpsSurveyRepository surveys, SurveyService service, TransactionTemplate tx, Clock clock) {
    this.surveys = surveys;
    this.service = service;
    this.tx = tx;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT80S")
  void tick() {
    try {
      tx.executeWithoutResult(status -> {
        dispatchDue();
        remindAndExpire();
      });
    } catch (RuntimeException e) {
      log.warn("No se pudieron procesar las encuestas NPS ({})", e.getClass().getSimpleName());
    }
  }

  /** Visible para pruebas. */
  int dispatchDue() {
    int sent = 0;
    for (NpsSurvey s : surveys.findTop50ByStatusAndSendAtLessThanEqualOrderBySendAt(SurveyStatus.SCHEDULED, clock.instant())) {
      service.dispatch(s, false);
      sent++;
    }
    return sent;
  }

  int remindAndExpire() {
    Instant now = clock.instant();
    SurveySettings settings = service.settings();
    int touched = 0;
    for (NpsSurvey s : surveys.findByStatus(SurveyStatus.SENT)) {
      if (s.getSentAt() == null) {
        continue;
      }
      if (s.getSentAt().plus(Duration.ofDays(settings.getExpireDays())).isBefore(now)) {
        s.expire();
        touched++;
        continue;
      }
      boolean remindable = s.getChannel() == SurveyChannel.WHATSAPP || s.getChannel() == SurveyChannel.EMAIL;
      Instant last = s.getLastReminderAt() != null ? s.getLastReminderAt() : s.getSentAt();
      if (settings.isReminderEnabled() && remindable && s.getRemindersSent() < settings.getMaxReminders() && last.plus(Duration.ofHours(settings.getReminderAfterHours())).isBefore(now)) {
        service.dispatch(s, true);
        touched++;
      }
    }
    return touched;
  }
}
