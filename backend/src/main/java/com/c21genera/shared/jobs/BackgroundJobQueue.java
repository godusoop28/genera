package com.c21genera.shared.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Encolado/desencolado genérico de {@link BackgroundJob}, usable por cualquier módulo. */
@Component
public class BackgroundJobQueue {

  private final BackgroundJobRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public BackgroundJobQueue(BackgroundJobRepository repository, ObjectMapper objectMapper, Clock clock) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * Se ejecuta en una transacción separada (REQUIRES_NEW) para que el job
   * quede encolado aunque el llamador sea un {@code @ApplicationModuleListener}
   * cuya propia transacción todavía no haya confirmado.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void enqueue(String type, Object payload) {
    String json = writeJson(payload);
    repository.save(new BackgroundJob(type, json, clock.instant()));
  }

  @Transactional
  public List<BackgroundJob> claimBatch(String type, String workerId, int limit) {
    List<BackgroundJob> jobs = repository.claimBatch(type, clock.instant(), limit);
    jobs.forEach(job -> job.markLocked(workerId, clock.instant()));
    return jobs;
  }

  /** {@code job} puede venir detached (cargado en una transacción ya cerrada): se re-adjunta por id. */
  @Transactional
  public void markDone(BackgroundJob job) {
    managed(job).markDone();
  }

  /** @return el estado final tras aplicar backoff/máximo de reintentos (ver {@link BackgroundJob#markFailedOrRetry}). */
  @Transactional
  public JobStatus markFailedOrRetry(BackgroundJob job, String error) {
    BackgroundJob managed = managed(job);
    managed.markFailedOrRetry(error, clock.instant(), Duration.ofMinutes(2));
    return managed.getStatus();
  }

  private BackgroundJob managed(BackgroundJob job) {
    return repository.findById(job.getId()).orElseThrow();
  }

  public <T> T readPayload(BackgroundJob job, Class<T> type) {
    try {
      return objectMapper.readValue(job.getPayload(), type);
    } catch (Exception e) {
      throw new IllegalStateException("Payload de job inválido: " + job.getId(), e);
    }
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo serializar el payload del job", e);
    }
  }
}
