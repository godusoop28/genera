package com.c21genera.closing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tarea interna posterior a la firma del contrato. Algunas se completan
 * solas al ocurrir el evento (entrega del contrato, decisión del inmueble);
 * las demás las marca el staff, con nota opcional.
 */
@Entity
@Table(name = "closing_task")
public class ClosingTask {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID closingCaseId;

  @Column(nullable = false, length = 48)
  private String code;

  @Column(nullable = false)
  private String title;

  private LocalDate dueDate;

  @Column(nullable = false)
  private boolean done;

  private Instant doneAt;
  private UUID doneByUserId;
  private String doneNote;

  @Column(nullable = false)
  private Instant createdAt;

  protected ClosingTask() {}

  public ClosingTask(UUID closingCaseId, String code, String title, LocalDate dueDate, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.closingCaseId = closingCaseId;
    this.code = code;
    this.title = title;
    this.dueDate = dueDate;
    this.createdAt = createdAt;
  }

  public void complete(Instant when, UUID byUserId, String note) {
    if (done) {
      return;
    }
    this.done = true;
    this.doneAt = when;
    this.doneByUserId = byUserId;
    this.doneNote = note;
  }

  public UUID getId() {
    return id;
  }

  public UUID getClosingCaseId() {
    return closingCaseId;
  }

  public String getCode() {
    return code;
  }

  public String getTitle() {
    return title;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public boolean isDone() {
    return done;
  }

  public Instant getDoneAt() {
    return doneAt;
  }

  public UUID getDoneByUserId() {
    return doneByUserId;
  }

  public String getDoneNote() {
    return doneNote;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
