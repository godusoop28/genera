package com.c21genera.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Historial de cada aviso: qué se envió, a quién, cuándo y si se entregó (o por qué falló). */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

  public enum Status {
    QUEUED,
    SENT,
    /** Falló un intento; se reintentará automáticamente. */
    RETRYING,
    FAILED,
    /** No se envió porque no hay destinatario (p. ej. el cliente no registró correo). */
    SKIPPED
  }

  @Id
  private UUID id;

  private UUID expedienteId;

  @Column(nullable = false, length = 64)
  private String kind;

  private String recipient;

  @Column(nullable = false)
  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  private String lastError;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant sentAt;

  protected NotificationLog() {}

  public NotificationLog(UUID expedienteId, String kind, String recipient, String subject, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.kind = kind.length() > 64 ? kind.substring(0, 64) : kind;
    this.recipient = recipient;
    this.subject = subject;
    this.createdAt = createdAt;
    this.status = recipient == null || recipient.isBlank() ? Status.SKIPPED : Status.QUEUED;
    if (this.status == Status.SKIPPED) {
      this.lastError = "No hay un correo electrónico registrado para este destinatario.";
    }
  }

  public void markSent(Instant when) {
    this.attempts++;
    this.status = Status.SENT;
    this.sentAt = when;
    this.lastError = null;
  }

  public void markAttemptFailed(String error, boolean finalAttempt) {
    this.attempts++;
    this.status = finalAttempt ? Status.FAILED : Status.RETRYING;
    this.lastError = error == null ? "Error desconocido" : error.length() > 1000 ? error.substring(0, 1000) : error;
  }

  /** "ju***@gmail.com": para la bitácora, que nunca guarda el correo completo. */
  public String maskedRecipient() {
    if (recipient == null || !recipient.contains("@")) {
      return "(sin destinatario)";
    }
    String[] parts = recipient.split("@", 2);
    String local = parts[0];
    return (local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***") + "@" + parts[1];
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public String getKind() {
    return kind;
  }

  public String getRecipient() {
    return recipient;
  }

  public String getSubject() {
    return subject;
  }

  public Status getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }
}
