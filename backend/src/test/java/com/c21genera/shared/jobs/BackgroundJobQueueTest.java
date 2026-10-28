package com.c21genera.shared.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** E2E 02/10: un reinicio del servidor dejaba jobs "en proceso" para siempre y documentos sin terminar. */
class BackgroundJobQueueTest {

  @Test
  void jobsAbandonedByADeadWorkerAreClaimedAgain() {
    Instant now = Instant.parse("2026-10-02T07:30:00Z");
    MutableClock clock = new MutableClock(now.minus(Duration.ofHours(1)));
    BackgroundJobRepository repository = mock(BackgroundJobRepository.class);
    when(repository.claimBatch(any(), any(), any(), anyInt())).thenReturn(List.of());
    BackgroundJobQueue queue = new BackgroundJobQueue(repository, new ObjectMapper(), clock);
    clock.now = now;

    queue.claimBatch("EXTRACT_DOCUMENT_FIELDS", "worker", 1);

    ArgumentCaptor<Instant> staleBefore = ArgumentCaptor.forClass(Instant.class);
    verify(repository).claimBatch(eq("EXTRACT_DOCUMENT_FIELDS"), eq(now), staleBefore.capture(), eq(1));
    assertThat(staleBefore.getValue()).isEqualTo(now.minus(BackgroundJobQueue.STALE_AFTER));
  }

  /** 28/10: la INE cargada 3 min antes de un reinicio por memoria se quedó "Procesando". */
  @Test
  void jobsLockedBeforeThisProcessStartedAreClaimedRightAway() {
    Instant bootedAt = Instant.parse("2026-10-28T22:04:12Z");
    MutableClock clock = new MutableClock(bootedAt);
    BackgroundJobRepository repository = mock(BackgroundJobRepository.class);
    when(repository.claimBatch(any(), any(), any(), anyInt())).thenReturn(List.of());
    BackgroundJobQueue queue = new BackgroundJobQueue(repository, new ObjectMapper(), clock);
    clock.now = bootedAt.plusSeconds(15);

    queue.claimBatch("PROCESS_DOCUMENT_VERSION", "worker", 1);

    ArgumentCaptor<Instant> staleBefore = ArgumentCaptor.forClass(Instant.class);
    verify(repository).claimBatch(eq("PROCESS_DOCUMENT_VERSION"), any(), staleBefore.capture(), eq(1));
    assertThat(staleBefore.getValue()).isEqualTo(bootedAt);
  }

  @Test
  void aJobThatKeepsKillingTheServerEventuallyFails() {
    BackgroundJob job = new BackgroundJob("PROCESS_DOCUMENT_VERSION", "{}", Instant.EPOCH);
    for (int i = 0; i < 5; i++) {
      job.markLocked("worker", Instant.EPOCH);
    }
    assertThat(job.exceededAttempts()).isFalse();

    job.markLocked("worker", Instant.EPOCH);
    assertThat(job.exceededAttempts()).isTrue();
    job.markFailedOrRetry("reinicios", Instant.EPOCH, Duration.ofMinutes(2));
    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
  }

  private static final class MutableClock extends Clock {
    Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
