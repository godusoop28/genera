package com.c21genera.leads.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Configuración de ruteo de un usuario interno: si recibe leads, peso, límite
 * diario, vínculo con 21Online y estado persistente de la ronda. La
 * disponibilidad por horario NO vive aquí: la da el calendario (crm_shift).
 */
@Entity
@Table(name = "crm_advisor_profile")
public class AdvisorProfile extends AuditableEntity {

  @Id
  private UUID userId;

  @Column(nullable = false)
  private boolean receivesLeads;

  @Column(nullable = false)
  private int weight = 5;

  private Integer dailyLimit;

  private Instant pausedUntil;

  private Long online21UserId;

  private String whatsappPhone;

  /** Horario simple anterior al calendario (solo referencia para completar el calendario). */
  private String legacyShift;

  @Column(nullable = false)
  private boolean calendarNeedsSetup;

  private Instant roundLastAt;

  @Column(nullable = false)
  private int roundCurrentWeight;

  protected AdvisorProfile() {}

  public AdvisorProfile(UUID userId) {
    this.userId = userId;
  }

  public void update(boolean receivesLeads, int weight, Integer dailyLimit, Long online21UserId, String whatsappPhone) {
    this.receivesLeads = receivesLeads;
    this.weight = Math.max(0, Math.min(100, weight));
    this.dailyLimit = dailyLimit != null && dailyLimit > 0 ? dailyLimit : null;
    this.online21UserId = online21UserId;
    this.whatsappPhone = whatsappPhone == null || whatsappPhone.isBlank() ? null : whatsappPhone.replaceAll("[^0-9+]", "");
  }

  public void pauseUntil(Instant until) {
    this.pausedUntil = until;
  }

  public boolean pausedAt(Instant t) {
    return !receivesLeads || (pausedUntil != null && pausedUntil.isAfter(t));
  }

  public void calendarConfigured() {
    this.calendarNeedsSetup = false;
  }

  /** Estado de la ronda tras recibir un lead por ronda (equitativa: fecha; ponderada: peso acumulado). */
  public void roundAssigned(Instant at) {
    this.roundLastAt = at;
  }

  public void roundWeight(int current) {
    this.roundCurrentWeight = current;
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean isReceivesLeads() {
    return receivesLeads;
  }

  public int getWeight() {
    return weight;
  }

  public Integer getDailyLimit() {
    return dailyLimit;
  }

  public Instant getPausedUntil() {
    return pausedUntil;
  }

  public Long getOnline21UserId() {
    return online21UserId;
  }

  public String getWhatsappPhone() {
    return whatsappPhone;
  }

  public String getLegacyShift() {
    return legacyShift;
  }

  public boolean isCalendarNeedsSetup() {
    return calendarNeedsSetup;
  }

  public Instant getRoundLastAt() {
    return roundLastAt;
  }

  public int getRoundCurrentWeight() {
    return roundCurrentWeight;
  }
}
