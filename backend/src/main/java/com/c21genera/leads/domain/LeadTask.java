package com.c21genera.leads.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #30: actividad con el prospecto (llamada, visita,
 * propuesta...). Nace programada (aparece en la agenda) o ya realizada; al
 * registrar el resultado queda en el historial del lead.
 */
@Entity
@Table(name = "lead_task")
public class LeadTask extends AuditableEntity {

  public enum Type {
    CALL,
    WHATSAPP,
    EMAIL,
    VISIT,
    OFFICE_MEETING,
    VIDEO_CALL,
    PROPOSAL,
    OFFER_FOLLOW_UP,
    NOTE,
    OTHER
  }

  public enum Status {
    SCHEDULED,
    DONE,
    CANCELLED
  }

  public enum Result {
    CONTACTED,
    NO_ANSWER,
    RESCHEDULED,
    REJECTED,
    INTERESTED,
    ADVANCED
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID leadId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Type type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  private String title;

  private Instant scheduledAt;

  private Integer durationMinutes;

  private UUID advisorUserId;

  private String propertyKey;

  private String propertyTitle;

  private String notesBefore;

  private Integer reminderMinutes;

  private Instant reminderSentAt;

  private Instant overdueNotifiedAt;

  @Enumerated(EnumType.STRING)
  private Result result;

  private Integer actualMinutes;

  private String outcomeNotes;

  private String nextAction;

  private Instant nextContactAt;

  private Instant completedAt;

  private UUID createdByUserId;

  private String createdByName;

  protected LeadTask() {}

  public LeadTask(UUID leadId, Type type, String title, UUID advisorUserId, UUID createdByUserId, String createdByName) {
    this.id = UUID.randomUUID();
    this.leadId = leadId;
    this.type = type;
    this.title = clip(blankToNull(title), 200);
    this.advisorUserId = advisorUserId;
    this.status = Status.SCHEDULED;
    this.createdByUserId = createdByUserId;
    this.createdByName = createdByName;
  }

  /** Programa (o reprograma) la actividad; si cambia la hora, el recordatorio se vuelve a enviar. */
  public void schedule(Instant at, Integer durationMinutes, Integer reminderMinutes, String propertyKey, String propertyTitle, String notesBefore) {
    if (at != null && !at.equals(this.scheduledAt)) {
      this.reminderSentAt = null;
      this.overdueNotifiedAt = null;
    }
    this.scheduledAt = at;
    this.durationMinutes = durationMinutes;
    this.reminderMinutes = reminderMinutes;
    this.propertyKey = blankToNull(propertyKey);
    this.propertyTitle = blankToNull(propertyTitle);
    this.notesBefore = clip(blankToNull(notesBefore), 4000);
  }

  public void retitle(String title) {
    this.title = clip(blankToNull(title), 200);
  }

  public void reassign(UUID advisorUserId) {
    this.advisorUserId = advisorUserId;
  }

  public void complete(Result result, Integer actualMinutes, String notes, String nextAction, Instant nextContactAt, Instant now) {
    this.status = Status.DONE;
    this.result = result;
    this.actualMinutes = actualMinutes;
    this.outcomeNotes = clip(blankToNull(notes), 4000);
    this.nextAction = clip(blankToNull(nextAction), 1000);
    this.nextContactAt = nextContactAt;
    this.completedAt = now;
    if (this.scheduledAt == null) {
      this.scheduledAt = now;
    }
  }

  public void cancel(Instant now) {
    this.status = Status.CANCELLED;
    this.completedAt = now;
  }

  public void markReminderSent(Instant now) {
    this.reminderSentAt = now;
  }

  public void markOverdueNotified(Instant now) {
    this.overdueNotifiedAt = now;
  }

  /** Programada, con su hora (más la duración) ya pasada y sin resultado. */
  public boolean isOverdue(Instant now) {
    if (status != Status.SCHEDULED || scheduledAt == null) {
      return false;
    }
    Instant end = scheduledAt.plusSeconds(60L * (durationMinutes == null ? 0 : durationMinutes));
    return end.isBefore(now);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static String clip(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }

  public UUID getId() {
    return id;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public Type getType() {
    return type;
  }

  public Status getStatus() {
    return status;
  }

  public String getTitle() {
    return title;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public String getPropertyKey() {
    return propertyKey;
  }

  public String getPropertyTitle() {
    return propertyTitle;
  }

  public String getNotesBefore() {
    return notesBefore;
  }

  public Integer getReminderMinutes() {
    return reminderMinutes;
  }

  public Instant getReminderSentAt() {
    return reminderSentAt;
  }

  public Instant getOverdueNotifiedAt() {
    return overdueNotifiedAt;
  }

  public Result getResult() {
    return result;
  }

  public Integer getActualMinutes() {
    return actualMinutes;
  }

  public String getOutcomeNotes() {
    return outcomeNotes;
  }

  public String getNextAction() {
    return nextAction;
  }

  public Instant getNextContactAt() {
    return nextContactAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getCreatedByName() {
    return createdByName;
  }
}
