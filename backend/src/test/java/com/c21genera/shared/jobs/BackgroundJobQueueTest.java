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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** E2E 02/10: un reinicio del servidor dejaba jobs "en proceso" para siempre y documentos sin terminar. */
class BackgroundJobQueueTest {

  @Test
  void jobsAbandonedByADeadWorkerAreClaimedAgain() {
    Instant now = Instant.parse("2026-10-02T07:30:00Z");
    BackgroundJobRepository repository = mock(BackgroundJobRepository.class);
    when(repository.claimBatch(any(), any(), any(), anyInt())).thenReturn(List.of());
    BackgroundJobQueue queue = new BackgroundJobQueue(repository, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));

    queue.claimBatch("EXTRACT_DOCUMENT_FIELDS", "worker", 1);

    ArgumentCaptor<Instant> staleBefore = ArgumentCaptor.forClass(Instant.class);
    verify(repository).claimBatch(eq("EXTRACT_DOCUMENT_FIELDS"), eq(now), staleBefore.capture(), eq(1));
    assertThat(staleBefore.getValue()).isEqualTo(now.minus(BackgroundJobQueue.STALE_AFTER));
  }
}
