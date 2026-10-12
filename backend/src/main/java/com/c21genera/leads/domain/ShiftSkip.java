package com.c21genera.leads.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fecha que una serie semanal NO genera (se editó o eliminó "solo este turno"). */
@Entity
@Table(name = "crm_shift_skip")
public class ShiftSkip {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID shiftId;

  @Column(nullable = false)
  private LocalDate occurrenceDate;

  @Column(nullable = false)
  private Instant createdAt;

  protected ShiftSkip() {}

  public ShiftSkip(UUID shiftId, LocalDate occurrenceDate, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.shiftId = shiftId;
    this.occurrenceDate = occurrenceDate;
    this.createdAt = createdAt;
  }

  public UUID getShiftId() {
    return shiftId;
  }

  public LocalDate getOccurrenceDate() {
    return occurrenceDate;
  }
}
