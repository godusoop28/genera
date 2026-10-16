package com.c21genera.notary.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Control de Cambios #23: paquete enviado a la notaría (inicial o de cierre) con la versión elegida
 * de cada documento. Es un registro histórico: nunca se modifica después de crearse.
 */
@Entity
@Table(name = "notary_submission")
public class NotarySubmission {

  public enum Kind {
    INITIAL,
    CLOSING
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Kind kind;

  @Column(length = 254)
  private String recipientEmail;

  private String note;

  /** Avisos que se aceptaron al enviar (p. ej. un documento de cierre sin confirmar como vigente). */
  private String warnings;

  private UUID sentByUserId;

  @Column(length = 160)
  private String sentByName;

  @Column(nullable = false)
  private Instant sentAt;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "submission_id", nullable = false)
  @OrderColumn(name = "position")
  private List<NotarySubmissionItem> items = new ArrayList<>();

  protected NotarySubmission() {}

  public NotarySubmission(
      UUID expedienteId, Kind kind, String recipientEmail, String note, String warnings, UUID byUserId, String byName, Instant now) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.kind = kind;
    this.recipientEmail = recipientEmail;
    this.note = note;
    this.warnings = warnings;
    this.sentByUserId = byUserId;
    this.sentByName = byName;
    this.sentAt = now;
  }

  public void addItem(UUID documentId, UUID versionId, int versionNumber, String documentName) {
    items.add(new NotarySubmissionItem(documentId, versionId, versionNumber, documentName));
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Kind getKind() {
    return kind;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getNote() {
    return note;
  }

  public String getWarnings() {
    return warnings;
  }

  public String getSentByName() {
    return sentByName;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public List<NotarySubmissionItem> getItems() {
    return List.copyOf(items);
  }
}
