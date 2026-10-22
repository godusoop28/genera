package com.c21genera.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Respuesta guardada del equipo para contestar más rápido en Conversaciones. */
@Entity
@Table(name = "wa_saved_reply")
public class SavedReply {

  @Id
  private UUID id;

  @Column(nullable = false, length = 80)
  private String title;

  @Column(nullable = false)
  private String text;

  @Column(length = 200)
  private String createdBy;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected SavedReply() {}

  public SavedReply(String title, String text, String createdBy, Instant now) {
    this.id = UUID.randomUUID();
    this.createdBy = createdBy;
    this.createdAt = now;
    edit(title, text, now);
  }

  public void edit(String title, String text, Instant now) {
    this.title = title.strip();
    this.text = text.strip();
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getText() {
    return text;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
