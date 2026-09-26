package com.c21genera.contracts.domain;

import com.c21genera.shared.domain.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Una firma requerida (o ya otorgada) de una versión concreta del contrato.
 * La evidencia vincula al firmante con el documento exacto: versión, huella
 * SHA-256 del PDF que vio, fecha y hora, IP y navegador (firma electrónica
 * simple por liga personal) o el archivo escaneado y quién lo registró
 * (firma autógrafa). La liga de firma usa un token de un solo propósito,
 * con vencimiento; solo se guarda su hash.
 */
@Entity
@Table(name = "contract_signature")
public class ContractSignature {

  public enum Party {
    CLIENT,
    INTERMEDIARY
  }

  public enum Status {
    PENDING,
    SIGNED,
    VOIDED
  }

  public enum Method {
    /** Liga personal: nombre escrito + trazo + aceptación, con IP, navegador y huella del documento. */
    ELECTRONIC_SIMPLE,
    /** Contrato firmado a mano, escaneado y cargado por el staff. */
    AUTOGRAPH_SCAN
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID contractGenerationId;

  @Column(nullable = false)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Party party;

  private UUID participantId;

  @Column(nullable = false)
  private String signerName;

  @Column(nullable = false)
  private String signerCapacity;

  private String signerEmail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(length = 64, unique = true)
  private String tokenHash;

  private Instant tokenExpiresAt;

  @Column(nullable = false)
  private Instant requestedAt;

  private Instant signedAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private Method method;

  @Column(length = 64)
  private String documentSha256;

  private String typedName;
  private String ipAddress;
  private String userAgent;
  private String signatureImageKey;

  @Column(length = 64)
  private String signatureImageSha256;

  private String evidenceKey;

  @Column(length = 64)
  private String evidenceSha256;

  private UUID registeredByUserId;
  private String consentText;
  private Instant voidedAt;
  private String voidedReason;

  protected ContractSignature() {}

  public ContractSignature(
      UUID contractGenerationId,
      UUID expedienteId,
      Party party,
      UUID participantId,
      String signerName,
      String signerCapacity,
      String signerEmail,
      String tokenHash,
      Instant tokenExpiresAt,
      Instant requestedAt) {
    this.id = UUID.randomUUID();
    this.contractGenerationId = contractGenerationId;
    this.expedienteId = expedienteId;
    this.party = party;
    this.participantId = participantId;
    this.signerName = signerName;
    this.signerCapacity = signerCapacity;
    this.signerEmail = signerEmail;
    this.tokenHash = tokenHash;
    this.tokenExpiresAt = tokenExpiresAt;
    this.requestedAt = requestedAt;
    this.status = Status.PENDING;
  }

  public boolean isLinkUsable(Instant now) {
    return status == Status.PENDING && tokenHash != null && (tokenExpiresAt == null || now.isBefore(tokenExpiresAt));
  }

  public void signElectronically(
      Instant when,
      String documentSha256,
      String typedName,
      String ipAddress,
      String userAgent,
      String signatureImageKey,
      String signatureImageSha256,
      String consentText,
      UUID registeredByUserId) {
    ensurePending();
    this.status = Status.SIGNED;
    this.method = Method.ELECTRONIC_SIMPLE;
    this.signedAt = when;
    this.documentSha256 = documentSha256;
    this.typedName = typedName;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.signatureImageKey = signatureImageKey;
    this.signatureImageSha256 = signatureImageSha256;
    this.consentText = consentText;
    this.registeredByUserId = registeredByUserId;
    // La liga ya cumplió su propósito: no se puede volver a usar.
    this.tokenExpiresAt = when;
  }

  public void signAutograph(Instant when, String documentSha256, String evidenceKey, String evidenceSha256, UUID registeredByUserId) {
    ensurePending();
    this.status = Status.SIGNED;
    this.method = Method.AUTOGRAPH_SCAN;
    this.signedAt = when;
    this.documentSha256 = documentSha256;
    this.evidenceKey = evidenceKey;
    this.evidenceSha256 = evidenceSha256;
    this.registeredByUserId = registeredByUserId;
    this.tokenExpiresAt = when;
  }

  public void voidBecause(Instant when, String reason) {
    if (status == Status.PENDING) {
      this.status = Status.VOIDED;
      this.voidedAt = when;
      this.voidedReason = reason;
      this.tokenExpiresAt = when;
    }
  }

  /** Nueva liga para el mismo firmante (la anterior deja de funcionar). */
  public void reissueToken(String tokenHash, Instant expiresAt) {
    ensurePending();
    this.tokenHash = tokenHash;
    this.tokenExpiresAt = expiresAt;
  }

  private void ensurePending() {
    if (status != Status.PENDING) {
      throw new ConflictException(
          "SIGNATURE_NOT_PENDING",
          status == Status.SIGNED ? "Esta firma ya fue registrada." : "Esta solicitud de firma quedó sin efecto (el contrato fue reemplazado).");
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getContractGenerationId() {
    return contractGenerationId;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Party getParty() {
    return party;
  }

  public UUID getParticipantId() {
    return participantId;
  }

  public String getSignerName() {
    return signerName;
  }

  public String getSignerCapacity() {
    return signerCapacity;
  }

  public String getSignerEmail() {
    return signerEmail;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getTokenExpiresAt() {
    return tokenExpiresAt;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public Instant getSignedAt() {
    return signedAt;
  }

  public Method getMethod() {
    return method;
  }

  public String getDocumentSha256() {
    return documentSha256;
  }

  public String getTypedName() {
    return typedName;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getSignatureImageKey() {
    return signatureImageKey;
  }

  public String getSignatureImageSha256() {
    return signatureImageSha256;
  }

  public String getEvidenceKey() {
    return evidenceKey;
  }

  public String getEvidenceSha256() {
    return evidenceSha256;
  }

  public UUID getRegisteredByUserId() {
    return registeredByUserId;
  }

  public String getConsentText() {
    return consentText;
  }

  public Instant getVoidedAt() {
    return voidedAt;
  }

  public String getVoidedReason() {
    return voidedReason;
  }
}
