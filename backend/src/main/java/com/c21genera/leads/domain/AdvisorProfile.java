package com.c21genera.leads.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Configuración de ruteo de un usuario interno: si recibe leads de la ruleta y cuántos. */
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

  private LocalTime shiftStart;

  private LocalTime shiftEnd;

  private Instant pausedUntil;

  private Long online21UserId;

  private String whatsappPhone;

  protected AdvisorProfile() {}

  public AdvisorProfile(UUID userId) {
    this.userId = userId;
  }

  public void update(
      boolean receivesLeads, int weight, Integer dailyLimit, LocalTime shiftStart, LocalTime shiftEnd, Long online21UserId, String whatsappPhone) {
    this.receivesLeads = receivesLeads;
    this.weight = Math.max(0, Math.min(100, weight));
    this.dailyLimit = dailyLimit != null && dailyLimit > 0 ? dailyLimit : null;
    this.shiftStart = shiftStart;
    this.shiftEnd = shiftEnd;
    this.online21UserId = online21UserId;
    this.whatsappPhone = whatsappPhone == null || whatsappPhone.isBlank() ? null : whatsappPhone.replaceAll("[^0-9+]", "");
  }

  public void pauseUntil(Instant until) {
    this.pausedUntil = until;
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

  public LocalTime getShiftStart() {
    return shiftStart;
  }

  public LocalTime getShiftEnd() {
    return shiftEnd;
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
}
