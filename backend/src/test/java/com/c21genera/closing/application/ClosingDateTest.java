package com.c21genera.closing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.closing.domain.ClosingCase;
import com.c21genera.closing.domain.ClosingStatus;
import com.c21genera.closing.infrastructure.ClosingCaseRepository;
import com.c21genera.closing.infrastructure.ClosingNoteRepository;
import com.c21genera.closing.infrastructure.ClosingTaskRepository;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.ClosingEvents.ClosingCompleted;
import com.c21genera.shared.events.ClosingEvents.ClosingReopened;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** CC#32: fecha de cierre del seguimiento de cierre existente. */
class ClosingDateTest {

  // 30/10/2026 02:00 UTC = 29/10/2026 20:00 en Ciudad de México.
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T02:00:00Z"), ZoneOffset.UTC);
  private final ClosingCaseRepository cases = mock(ClosingCaseRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ClosingService service =
      new ClosingService(cases, mock(ClosingNoteRepository.class), mock(ClosingTaskRepository.class), clock, events);

  private final UUID expedienteId = UUID.randomUUID();
  private final ClosingCase closingCase = new ClosingCase(expedienteId);

  ClosingDateTest() {
    when(cases.findByExpedienteId(expedienteId)).thenReturn(Optional.of(closingCase));
  }

  @Test
  void completingRecordsTodayInMexicoCityAndPublishesTheDate() {
    service.changeStatus(expedienteId, ClosingStatus.COMPLETED);

    assertThat(closingCase.getClosedOn()).isEqualTo(LocalDate.of(2026, 10, 29));
    verify(events).publishEvent(new ClosingCompleted(expedienteId, LocalDate.of(2026, 10, 29)));
  }

  @Test
  void completingWithAnExplicitDateKeepsThatDate() {
    service.changeStatus(expedienteId, ClosingStatus.COMPLETED, LocalDate.of(2026, 10, 20));

    assertThat(closingCase.getClosedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
    verify(events).publishEvent(new ClosingCompleted(expedienteId, LocalDate.of(2026, 10, 20)));
  }

  @Test
  void aClosingDateInTheFutureIsRejected() {
    assertThatThrownBy(() -> service.changeStatus(expedienteId, ClosingStatus.COMPLETED, LocalDate.of(2026, 11, 2)))
        .isInstanceOf(UnprocessableException.class);
    assertThat(closingCase.getStatus()).isEqualTo(ClosingStatus.OPEN);
  }

  @Test
  void reopeningClearsTheDateAndTellsTheExpediente() {
    service.changeStatus(expedienteId, ClosingStatus.COMPLETED);

    service.changeStatus(expedienteId, ClosingStatus.IN_PROGRESS);

    assertThat(closingCase.getClosedOn()).isNull();
    verify(events).publishEvent(new ClosingReopened(expedienteId));
  }
}
