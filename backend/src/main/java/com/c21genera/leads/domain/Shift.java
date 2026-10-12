package com.c21genera.leads.domain;

import com.c21genera.leads.domain.Schedule.Kind;
import com.c21genera.leads.domain.Schedule.Recurrence;
import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Turno de un asesor (una fecha o serie semanal). Nunca se borra físicamente: se marca como eliminado. */
@Entity
@Table(name = "crm_shift")
public class Shift extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID advisorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Kind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Recurrence recurrence;

  private String daysOfWeek;

  @Column(nullable = false)
  private LocalDate startDate;

  private LocalDate endDate;

  @Column(nullable = false)
  private LocalTime startTime;

  @Column(nullable = false)
  private LocalTime endTime;

  @Column(nullable = false)
  private boolean assignable = true;

  private String notes;

  private UUID originShiftId;

  private UUID createdById;

  private String createdByName;

  private String updatedByName;

  private Instant deletedAt;

  private String deletedByName;

  protected Shift() {}

  public Shift(UUID advisorUserId, UUID createdById, String createdByName) {
    this.id = UUID.randomUUID();
    this.advisorUserId = advisorUserId;
    this.createdById = createdById;
    this.createdByName = createdByName;
  }

  public void define(
      Kind kind, Recurrence recurrence, Set<DayOfWeek> days, LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime, boolean assignable, String notes) {
    this.kind = kind;
    this.recurrence = recurrence;
    this.daysOfWeek = recurrence == Recurrence.WEEKLY ? days.stream().sorted().map(d -> Integer.toString(d.getValue())).collect(Collectors.joining(",")) : null;
    this.startDate = startDate;
    this.endDate = recurrence == Recurrence.WEEKLY ? endDate : null;
    this.startTime = startTime;
    this.endTime = endTime;
    this.assignable = assignable;
    this.notes = notes == null || notes.isBlank() ? null : notes.trim();
  }

  public void reassignAdvisor(UUID advisorUserId) {
    this.advisorUserId = advisorUserId;
  }

  public void endSeriesOn(LocalDate lastDate) {
    this.endDate = lastDate;
  }

  public void originatedFrom(UUID seriesId) {
    this.originShiftId = seriesId;
  }

  public void updatedBy(String name) {
    this.updatedByName = name;
  }

  public void delete(Instant at, String byName) {
    this.deletedAt = at;
    this.deletedByName = byName;
  }

  public Set<DayOfWeek> days() {
    if (daysOfWeek == null || daysOfWeek.isBlank()) {
      return EnumSet.noneOf(DayOfWeek.class);
    }
    return Arrays.stream(daysOfWeek.split(",")).map(String::trim).map(Integer::parseInt).map(DayOfWeek::of).collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
  }

  public Schedule.Rule toRule(Set<LocalDate> skipped) {
    return new Schedule.Rule(id, advisorUserId, kind, recurrence, days(), startDate, endDate, startTime, endTime, assignable, skipped);
  }

  public UUID getId() {
    return id;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public Kind getKind() {
    return kind;
  }

  public Recurrence getRecurrence() {
    return recurrence;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public boolean isAssignable() {
    return assignable;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getOriginShiftId() {
    return originShiftId;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public String getUpdatedByName() {
    return updatedByName;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }
}
