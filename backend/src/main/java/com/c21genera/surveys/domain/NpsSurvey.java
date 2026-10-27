package com.c21genera.surveys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Control de Cambios #28: una encuesta NPS enviada (o programada) a un
 * cliente en un punto de contacto, con los datos que se capturan solos
 * (asesor, punto de contacto, canal, expediente) y su respuesta.
 */
@Entity
@Table(name = "nps_survey")
public class NpsSurvey {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Id
  private UUID id;

  @Column(nullable = false)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Touchpoint touchpoint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SurveyChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SurveyStatus status;

  private UUID expedienteId;

  private String expedienteFolio;

  private UUID leadId;

  private String clientName;

  private String clientPhone;

  private String clientEmail;

  private String contactKey;

  private UUID advisorUserId;

  private String advisorName;

  private String introMessage;

  @Column(nullable = false)
  private boolean askComment;

  @Column(nullable = false)
  private Instant sendAt;

  private Instant sentAt;

  @Column(nullable = false)
  private int remindersSent;

  private Instant lastReminderAt;

  private String deliveryDetail;

  private Integer score;

  private String comment;

  private Instant answeredAt;

  private String createdByName;

  @Column(nullable = false)
  private Instant createdAt;

  protected NpsSurvey() {}

  public NpsSurvey(Touchpoint touchpoint, SurveyChannel channel, Contact contact, String introMessage, boolean askComment, Instant sendAt, String createdByName, Instant now) {
    this.id = UUID.randomUUID();
    this.token = newToken();
    this.touchpoint = touchpoint;
    this.channel = channel;
    this.status = SurveyStatus.SCHEDULED;
    this.expedienteId = contact.expedienteId();
    this.expedienteFolio = contact.expedienteFolio();
    this.leadId = contact.leadId();
    this.clientName = blank(contact.name());
    this.clientPhone = blank(contact.phone());
    this.clientEmail = blank(contact.email());
    this.contactKey = contact.key();
    this.advisorUserId = contact.advisorUserId();
    this.advisorName = blank(contact.advisorName());
    this.introMessage = blank(introMessage);
    this.askComment = askComment;
    this.sendAt = sendAt;
    this.createdByName = createdByName;
    this.createdAt = now;
  }

  /**
   * Datos del cliente y del asesor al momento de crear la encuesta. key:
   * identifica al cliente para la regla anti-saturación (teléfono, correo,
   * expediente o lead, en ese orden).
   */
  public record Contact(UUID expedienteId, String expedienteFolio, UUID leadId, String name, String phone, String email, UUID advisorUserId, String advisorName) {

    public String key() {
      String digits = phone == null ? "" : phone.replaceAll("\\D", "");
      if (digits.length() >= 10) {
        return "tel:" + digits.substring(digits.length() - 10);
      }
      if (email != null && !email.isBlank()) {
        return "mail:" + email.strip().toLowerCase(java.util.Locale.ROOT);
      }
      if (expedienteId != null) {
        return "exp:" + expedienteId;
      }
      return leadId != null ? "lead:" + leadId : null;
    }
  }

  private static String newToken() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public void skip(String reason) {
    this.status = SurveyStatus.SKIPPED;
    this.deliveryDetail = reason;
  }

  public void markSent(Instant now, String detail) {
    this.status = SurveyStatus.SENT;
    this.sentAt = now;
    this.deliveryDetail = detail;
  }

  public void markFailed(String detail) {
    this.status = SurveyStatus.FAILED;
    this.deliveryDetail = detail;
  }

  public void deliveryResult(String detail) {
    this.deliveryDetail = detail;
  }

  /** Cambia de canal (p. ej. de WhatsApp a correo si el cliente no tiene teléfono). */
  public void fallbackTo(SurveyChannel channel) {
    this.channel = channel;
  }

  public void reminded(Instant now) {
    this.remindersSent++;
    this.lastReminderAt = now;
  }

  public void expire() {
    this.status = SurveyStatus.EXPIRED;
  }

  public void answer(int score, Instant now) {
    this.score = score;
    this.status = SurveyStatus.ANSWERED;
    this.answeredAt = now;
    if (this.sentAt == null) {
      this.sentAt = now;
    }
  }

  public void comment(String comment) {
    this.comment = comment == null || comment.isBlank() ? null : (comment.length() > 2000 ? comment.substring(0, 2000) : comment.strip());
  }

  /** QR: quien responde puede dejar su nombre. */
  public void respondent(String name) {
    if (name != null && !name.isBlank()) {
      this.clientName = name.strip();
    }
  }

  public NpsCategory category() {
    return score == null ? null : NpsCategory.of(score);
  }

  private static String blank(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  public UUID getId() {
    return id;
  }

  public String getToken() {
    return token;
  }

  public Touchpoint getTouchpoint() {
    return touchpoint;
  }

  public SurveyChannel getChannel() {
    return channel;
  }

  public SurveyStatus getStatus() {
    return status;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public String getExpedienteFolio() {
    return expedienteFolio;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public String getClientName() {
    return clientName;
  }

  public String getClientPhone() {
    return clientPhone;
  }

  public String getClientEmail() {
    return clientEmail;
  }

  public String getContactKey() {
    return contactKey;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public String getAdvisorName() {
    return advisorName;
  }

  public String getIntroMessage() {
    return introMessage;
  }

  public boolean isAskComment() {
    return askComment;
  }

  public Instant getSendAt() {
    return sendAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public int getRemindersSent() {
    return remindersSent;
  }

  public Instant getLastReminderAt() {
    return lastReminderAt;
  }

  public String getDeliveryDetail() {
    return deliveryDetail;
  }

  public Integer getScore() {
    return score;
  }

  public String getComment() {
    return comment;
  }

  public Instant getAnsweredAt() {
    return answeredAt;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
