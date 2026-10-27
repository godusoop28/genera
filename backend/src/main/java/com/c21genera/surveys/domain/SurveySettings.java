package com.c21genera.surveys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Control de Cambios #28: reglas generales de las encuestas (una sola fila). */
@Entity
@Table(name = "survey_settings")
public class SurveySettings {

  @Id
  private short id = 1;

  /** No mandar otra encuesta automática al mismo cliente antes de estos días. */
  @Column(nullable = false)
  private int minDaysBetween;

  /** Máximo de encuestas automáticas al mismo cliente en periodDays. */
  @Column(nullable = false)
  private int maxPerPeriod;

  @Column(nullable = false)
  private int periodDays;

  @Column(nullable = false)
  private boolean reminderEnabled;

  @Column(nullable = false)
  private int reminderAfterHours;

  @Column(nullable = false)
  private int maxReminders;

  @Column(nullable = false)
  private int expireDays;

  /** Cada asesor ve el NPS de sus clientes (si no, solo dirección y quien tenga NPS_VIEW). */
  @Column(nullable = false)
  private boolean advisorsSeeOwn;

  private String updatedByName;

  @Column(nullable = false)
  private Instant updatedAt;

  protected SurveySettings() {}

  public void update(int minDaysBetween, int maxPerPeriod, int periodDays, boolean reminderEnabled, int reminderAfterHours, int maxReminders, int expireDays, boolean advisorsSeeOwn, String byName, Instant now) {
    this.minDaysBetween = Math.max(0, minDaysBetween);
    this.maxPerPeriod = Math.max(1, maxPerPeriod);
    this.periodDays = Math.max(1, periodDays);
    this.reminderEnabled = reminderEnabled;
    this.reminderAfterHours = Math.max(1, reminderAfterHours);
    this.maxReminders = Math.max(0, Math.min(5, maxReminders));
    this.expireDays = Math.max(1, expireDays);
    this.advisorsSeeOwn = advisorsSeeOwn;
    this.updatedByName = byName;
    this.updatedAt = now;
  }

  public int getMinDaysBetween() {
    return minDaysBetween;
  }

  public int getMaxPerPeriod() {
    return maxPerPeriod;
  }

  public int getPeriodDays() {
    return periodDays;
  }

  public boolean isReminderEnabled() {
    return reminderEnabled;
  }

  public int getReminderAfterHours() {
    return reminderAfterHours;
  }

  public int getMaxReminders() {
    return maxReminders;
  }

  public int getExpireDays() {
    return expireDays;
  }

  public boolean isAdvisorsSeeOwn() {
    return advisorsSeeOwn;
  }

  public String getUpdatedByName() {
    return updatedByName;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
