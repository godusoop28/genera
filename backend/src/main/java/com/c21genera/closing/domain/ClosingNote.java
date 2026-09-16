package com.c21genera.closing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "closing_note")
public class ClosingNote {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID closingCaseId;

  @Column(nullable = false)
  private UUID authorUserId;

  @Column(nullable = false, length = 2000)
  private String note;

  @Column(nullable = false)
  private Instant createdAt;

  protected ClosingNote() {}

  public ClosingNote(UUID closingCaseId, UUID authorUserId, String note, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.closingCaseId = closingCaseId;
    this.authorUserId = authorUserId;
    this.note = note;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getClosingCaseId() {
    return closingCaseId;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
