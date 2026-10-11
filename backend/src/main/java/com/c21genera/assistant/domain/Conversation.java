package com.c21genera.assistant.domain;

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
 * Una conversación de WhatsApp con un contacto. La memoria (datos, propiedades,
 * resumen) y las decisiones viven aquí, no en WhatsApp. Se modifica siempre
 * con la fila bloqueada (ver ConversationRepository#lockById).
 */
@Entity
@Table(name = "wa_conversation")
public class Conversation extends AuditableEntity {

  public enum Control {
    /** Responde la IA. */
    AI,
    /** Alguien del equipo está atendiendo: la IA no responde. */
    HUMAN,
    /** IA en pausa (manual o por fallas repetidas). */
    PAUSED
  }

  public enum HandoffState {
    NONE,
    ASSIGNED,
    EXISTING_LEAD,
    NO_ADVISOR,
    NOT_APPLICABLE,
    HUMAN_REQUESTED
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private String waId;

  private String contactName;

  @Column(nullable = false)
  private boolean test;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Control control = Control.AI;

  @Column(nullable = false)
  private String primaryIntent = "UNKNOWN";

  @Column(nullable = false)
  private String secondaryIntents = "";

  @Column(name = "facts_json", nullable = false)
  private String factsJson = "{}";

  @Column(name = "properties_json", nullable = false)
  private String propertiesJson = "[]";

  private String summary;

  @Column(nullable = false)
  private int clarificationCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HandoffState handoffState = HandoffState.NONE;

  private Instant handoffAt;

  private UUID leadId;

  @Column(nullable = false)
  private int sessionSeq = 1;

  @Column(nullable = false)
  private long sessionStartSeq;

  private String previousContext;

  @Column(nullable = false)
  private long lastSeq;

  @Column(nullable = false)
  private long processedSeq;

  private Instant burstStartedAt;

  private Instant processAfter;

  @Column(nullable = false)
  private int failedAttempts;

  private String leaseOwner;

  private Instant leaseUntil;

  private String needsAttention;

  private Instant lastInboundAt;

  private Instant lastMessageAt;

  protected Conversation() {}

  public Conversation(String waId, String contactName, boolean test) {
    this.id = UUID.randomUUID();
    this.waId = waId;
    this.contactName = contactName;
    this.test = test;
  }

  /** Reserva el siguiente número de mensaje. */
  public long nextSeq(Instant now) {
    lastSeq++;
    lastMessageAt = now;
    return lastSeq;
  }

  public void inbound(String contactName, Instant now, Burst.Schedule schedule) {
    if (contactName != null && !contactName.isBlank()) {
      this.contactName = contactName;
    }
    this.lastInboundAt = now;
    this.burstStartedAt = schedule.burstStartedAt();
    this.processAfter = schedule.processAfter();
  }

  /**
   * Sesión nueva (tras horas sin mensajes): lo anterior pasa a contexto y la
   * IA ya no responde pedidos viejos como si fueran actuales. Se conserva el
   * lead y su asesor.
   */
  public void startNewSession(String previousContextJson) {
    this.previousContext = previousContextJson;
    this.sessionSeq++;
    this.sessionStartSeq = this.lastSeq + 1;
    this.primaryIntent = "UNKNOWN";
    this.secondaryIntents = "";
    this.factsJson = "{}";
    this.propertiesJson = "[]";
    this.summary = null;
    this.clarificationCount = 0;
    if (handoffState == HandoffState.NO_ADVISOR || handoffState == HandoffState.NOT_APPLICABLE || handoffState == HandoffState.HUMAN_REQUESTED) {
      handoffState = HandoffState.NONE;
    }
    this.handoffAt = null;
  }

  public boolean leaseFree(Instant now) {
    return leaseUntil == null || leaseUntil.isBefore(now);
  }

  public void lease(String owner, Instant until) {
    this.leaseOwner = owner;
    this.leaseUntil = until;
  }

  public void releaseLease() {
    this.leaseOwner = null;
    this.leaseUntil = null;
  }

  /** Turno aplicado: lo pendiente hasta toSeq ya quedó atendido. */
  public void processed(long toSeq) {
    this.processedSeq = Math.max(processedSeq, toSeq);
    this.failedAttempts = 0;
    if (processedSeq >= lastSeq) {
      this.processAfter = null;
      this.burstStartedAt = null;
    }
  }

  public void retryAt(Instant when) {
    this.failedAttempts++;
    this.processAfter = when;
  }

  public void skipPending() {
    this.processedSeq = lastSeq;
    this.processAfter = null;
    this.burstStartedAt = null;
  }

  public void applyUnderstanding(String primaryIntent, String secondaryIntents, String factsJson, String propertiesJson, String summary, boolean askedClarification, boolean madeProgress) {
    this.primaryIntent = primaryIntent;
    this.secondaryIntents = secondaryIntents;
    this.factsJson = factsJson;
    this.propertiesJson = propertiesJson;
    if (summary != null && !summary.isBlank()) {
      this.summary = summary.length() > 1200 ? summary.substring(0, 1200) : summary;
    }
    if (madeProgress) {
      this.clarificationCount = 0;
    } else if (askedClarification) {
      this.clarificationCount++;
    }
  }

  public void handoff(HandoffState state, UUID leadId, Instant now) {
    this.handoffState = state;
    this.handoffAt = now;
    if (leadId != null) {
      this.leadId = leadId;
    }
  }

  public void setControl(Control control) {
    this.control = control;
  }

  public void attention(String reason) {
    this.needsAttention = reason == null || reason.isBlank() ? null : (reason.length() > 300 ? reason.substring(0, 300) : reason);
  }

  public UUID getId() {
    return id;
  }

  public String getWaId() {
    return waId;
  }

  public String getContactName() {
    return contactName;
  }

  public boolean isTest() {
    return test;
  }

  public Control getControl() {
    return control;
  }

  public String getPrimaryIntent() {
    return primaryIntent;
  }

  public String getSecondaryIntents() {
    return secondaryIntents;
  }

  public String getFactsJson() {
    return factsJson;
  }

  public String getPropertiesJson() {
    return propertiesJson;
  }

  public String getSummary() {
    return summary;
  }

  public int getClarificationCount() {
    return clarificationCount;
  }

  public HandoffState getHandoffState() {
    return handoffState;
  }

  public Instant getHandoffAt() {
    return handoffAt;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public int getSessionSeq() {
    return sessionSeq;
  }

  public long getSessionStartSeq() {
    return sessionStartSeq;
  }

  public String getPreviousContext() {
    return previousContext;
  }

  public long getLastSeq() {
    return lastSeq;
  }

  public long getProcessedSeq() {
    return processedSeq;
  }

  public Instant getBurstStartedAt() {
    return burstStartedAt;
  }

  public Instant getProcessAfter() {
    return processAfter;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public String getNeedsAttention() {
    return needsAttention;
  }

  public Instant getLastInboundAt() {
    return lastInboundAt;
  }

  public Instant getLastMessageAt() {
    return lastMessageAt;
  }
}
