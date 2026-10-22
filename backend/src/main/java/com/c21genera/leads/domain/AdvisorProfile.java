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

  // Copia de su usuario de 21Online (21Online manda; se actualiza en cada sincronización).
  private String online21Name;

  private String online21Email;

  private String online21MobilePhone;

  private String online21OfficePhone;

  private String online21Whatsapp;

  private String online21PhotoUrl;

  private String online21Position;

  private String online21ProfileUrl;

  private Instant online21SyncedAt;

  protected AdvisorProfile() {}

  public AdvisorProfile(UUID userId) {
    this.userId = userId;
  }

  public void update(boolean receivesLeads, int weight, Integer dailyLimit, Long online21UserId, String whatsappPhone) {
    this.receivesLeads = receivesLeads;
    this.weight = Math.max(0, Math.min(100, weight));
    this.dailyLimit = dailyLimit != null && dailyLimit > 0 ? dailyLimit : null;
    linkOnline21(online21UserId);
    this.whatsappPhone = whatsappPhone == null || whatsappPhone.isBlank() ? null : whatsappPhone.replaceAll("[^0-9+]", "");
  }

  /** Vínculo con su usuario de 21Online (sincronización por correo); no toca lo demás del perfil. */
  public void linkOnline21(Long online21UserId) {
    if (!java.util.Objects.equals(this.online21UserId, online21UserId)) {
      // Otro usuario (o ninguno): la copia anterior ya no corresponde.
      online21Snapshot(new Online21Snapshot(null, null, null, null, null, null, null, null, null));
    }
    this.online21UserId = online21UserId;
  }

  /** Datos de su usuario de 21Online tal como están allá. */
  public record Online21Snapshot(
      String name, String email, String mobilePhone, String officePhone, String whatsapp, String photoUrl, String position, String profileUrl, Instant syncedAt) {}

  public void online21Snapshot(Online21Snapshot s) {
    this.online21Name = cut(s.name(), 200);
    this.online21Email = cut(s.email(), 200);
    this.online21MobilePhone = cut(s.mobilePhone(), 40);
    this.online21OfficePhone = cut(s.officePhone(), 40);
    this.online21Whatsapp = cut(s.whatsapp(), 40);
    this.online21PhotoUrl = cut(s.photoUrl(), 500);
    this.online21Position = cut(s.position(), 80);
    this.online21ProfileUrl = cut(s.profileUrl(), 500);
    this.online21SyncedAt = s.syncedAt();
  }

  /** null si nunca se ha sincronizado con 21Online. */
  public Online21Snapshot getOnline21Snapshot() {
    if (online21SyncedAt == null) {
      return null;
    }
    return new Online21Snapshot(
        online21Name, online21Email, online21MobilePhone, online21OfficePhone, online21Whatsapp, online21PhotoUrl, online21Position, online21ProfileUrl, online21SyncedAt);
  }

  private static String cut(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
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
