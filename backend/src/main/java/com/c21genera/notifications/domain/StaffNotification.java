package com.c21genera.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Aviso dentro del sistema para un usuario interno (campana del encabezado). */
@Entity
@Table(name = "staff_notification")
public class StaffNotification {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String kind;

  @Column(nullable = false)
  private String title;

  private String body;

  private String link;

  private String dedupeKey;

  private Instant readAt;

  @Column(nullable = false)
  private Instant createdAt;

  protected StaffNotification() {}

  public StaffNotification(UUID userId, String kind, String title, String body, String link, String dedupeKey, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.kind = clip(kind, 40);
    this.title = clip(title, 200);
    this.body = body;
    this.link = clip(link, 300);
    this.dedupeKey = clip(dedupeKey, 120);
    this.createdAt = createdAt;
  }

  public void markRead(Instant now) {
    if (readAt == null) {
      readAt = now;
    }
  }

  private static String clip(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getKind() {
    return kind;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getLink() {
    return link;
  }

  public String getDedupeKey() {
    return dedupeKey;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
