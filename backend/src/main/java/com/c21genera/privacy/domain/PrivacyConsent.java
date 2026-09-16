package com.c21genera.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Consentimiento de privacidad de un expediente (ver AGENTS §63/§66). Las
 * finalidades secundarias se registran por separado y NUNCA bloquean el
 * proceso si son rechazadas.
 */
@Entity
@Table(name = "privacy_consent")
public class PrivacyConsent {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true)
  private UUID expedienteId;

  @Column(nullable = false)
  private UUID noticeTemplateId;

  @Column(nullable = false)
  private boolean mainPurposesAccepted;

  @Column(nullable = false)
  private boolean secondaryPurposesAccepted;

  @Column(nullable = false)
  private Instant acceptedAt;

  @Column(nullable = false)
  private String ipAddress;

  private String userAgent;

  private String signatureStorageKey;

  private String signatureSha256;

  protected PrivacyConsent() {}

  public PrivacyConsent(
      UUID expedienteId,
      UUID noticeTemplateId,
      boolean mainPurposesAccepted,
      boolean secondaryPurposesAccepted,
      Instant acceptedAt,
      String ipAddress,
      String userAgent,
      String signatureStorageKey,
      String signatureSha256) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.noticeTemplateId = noticeTemplateId;
    this.mainPurposesAccepted = mainPurposesAccepted;
    this.secondaryPurposesAccepted = secondaryPurposesAccepted;
    this.acceptedAt = acceptedAt;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.signatureStorageKey = signatureStorageKey;
    this.signatureSha256 = signatureSha256;
  }

  /** Permite volver a registrar el consentimiento (p. ej. el cliente vuelve a firmar). */
  public void reRecord(
      UUID noticeTemplateId,
      boolean mainPurposesAccepted,
      boolean secondaryPurposesAccepted,
      Instant acceptedAt,
      String ipAddress,
      String userAgent,
      String signatureStorageKey,
      String signatureSha256) {
    this.noticeTemplateId = noticeTemplateId;
    this.mainPurposesAccepted = mainPurposesAccepted;
    this.secondaryPurposesAccepted = secondaryPurposesAccepted;
    this.acceptedAt = acceptedAt;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    if (signatureStorageKey != null) {
      this.signatureStorageKey = signatureStorageKey;
      this.signatureSha256 = signatureSha256;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public UUID getNoticeTemplateId() {
    return noticeTemplateId;
  }

  public boolean isMainPurposesAccepted() {
    return mainPurposesAccepted;
  }

  public boolean isSecondaryPurposesAccepted() {
    return secondaryPurposesAccepted;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getSignatureStorageKey() {
    return signatureStorageKey;
  }

  public String getSignatureSha256() {
    return signatureSha256;
  }
}
