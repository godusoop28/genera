package com.c21genera.surveys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Control de Cambios #28, parte 4: configuración independiente de cada punto de contacto. */
@Entity
@Table(name = "survey_touchpoint_config")
public class TouchpointConfig {

  @Id
  @Enumerated(EnumType.STRING)
  private Touchpoint touchpoint;

  @Column(nullable = false)
  private boolean active;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SurveyChannel channel;

  @Column(nullable = false)
  private int delayMinutes;

  @Column(nullable = false)
  private boolean askComment;

  private String introMessage;

  private String updatedByName;

  @Column(nullable = false)
  private Instant updatedAt;

  protected TouchpointConfig() {}

  public void update(boolean active, SurveyChannel channel, int delayMinutes, boolean askComment, String introMessage, String byName, Instant now) {
    this.active = active;
    this.channel = channel;
    this.delayMinutes = Math.max(0, delayMinutes);
    this.askComment = askComment;
    this.introMessage = introMessage == null || introMessage.isBlank() ? null : introMessage.strip();
    this.updatedByName = byName;
    this.updatedAt = now;
  }

  public Touchpoint getTouchpoint() {
    return touchpoint;
  }

  public boolean isActive() {
    return active;
  }

  public SurveyChannel getChannel() {
    return channel;
  }

  public int getDelayMinutes() {
    return delayMinutes;
  }

  public boolean isAskComment() {
    return askComment;
  }

  public String getIntroMessage() {
    return introMessage;
  }

  public String getUpdatedByName() {
    return updatedByName;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
