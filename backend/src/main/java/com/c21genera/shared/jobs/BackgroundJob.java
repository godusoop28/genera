package com.c21genera.shared.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Cola de trabajos persistente genérica (ver AGENTS §34-35): sin
 * Kafka/RabbitMQ por ahora, pero diseñada para poder agregarlos después sin
 * cambiar el dominio de los módulos que la usan (documentprocessing,
 * extraction, ...).
 */
@Entity
@Table(name = "background_job")
public class BackgroundJob {

  private static final int MAX_ATTEMPTS = 5;

  @Id
  private UUID id;

  @Column(nullable = false, length = 64)
  private String type;

  @Column(nullable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private JobStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private Instant nextAttemptAt;

  private Instant lockedAt;

  @Column(length = 64)
  private String lockedBy;

  @Column(nullable = false)
  private Instant createdAt;

  private String lastError;

  protected BackgroundJob() {}

  public BackgroundJob(String type, String payload, Instant now) {
    this.id = UUID.randomUUID();
    this.type = type;
    this.payload = payload;
    this.status = JobStatus.QUEUED;
    this.attempts = 0;
    this.nextAttemptAt = now;
    this.createdAt = now;
  }

  public void markLocked(String worker, Instant now) {
    this.status = JobStatus.PROCESSING;
    this.lockedAt = now;
    this.lockedBy = worker;
    this.attempts++;
  }

  public void markDone() {
    this.status = JobStatus.DONE;
    this.lockedAt = null;
    this.lockedBy = null;
    this.lastError = null;
  }

  /** Reintenta con backoff hasta MAX_ATTEMPTS; después queda en FAILED definitivamente. */
  public void markFailedOrRetry(String error, Instant now, java.time.Duration backoff) {
    this.lastError = error;
    this.lockedAt = null;
    this.lockedBy = null;
    if (this.attempts >= MAX_ATTEMPTS) {
      this.status = JobStatus.FAILED;
    } else {
      this.status = JobStatus.QUEUED;
      this.nextAttemptAt = now.plus(backoff);
    }
  }

  public UUID getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public String getPayload() {
    return payload;
  }

  public JobStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }
}
