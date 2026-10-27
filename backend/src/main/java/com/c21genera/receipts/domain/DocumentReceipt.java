package com.c21genera.receipts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #31: un acuse de recepción emitido. Guarda la foto de lo
 * que cubrió (documentos, versión del aviso de privacidad y textos) para que
 * siempre se sepa qué comprendía cada emisión; los anteriores se conservan.
 */
@Entity
@Table(name = "document_receipt")
public class DocumentReceipt {

  public enum SignatureMethod {
    /** El representante firmó en el sistema (contraseña + firma autógrafa digitalizada). */
    ELECTRONIC_SIMPLE,
    /** Se firmó por otro medio autorizado (papel, DocuSign…) y se cargó el archivo. */
    UPLOADED
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private int sequenceNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReceiptStatus status;

  @Column(nullable = false)
  private String expedienteFolio;

  private String propertyKey;

  @Column(nullable = false)
  private String clientName;

  private String clientEmail;

  @Column(nullable = false)
  private String responsibleEntity;

  private String representativeName;

  private String representativeTitle;

  @Column(nullable = false)
  private String responsibilityText;

  @Column(nullable = false)
  private String privacyText;

  @Column(nullable = false)
  private boolean textsApproved;

  private Integer privacyNoticeVersion;

  private String privacyNoticeSha256;

  private String privacyNoticeKey;

  private Instant privacyAcceptedAt;

  @Column(nullable = false)
  private String itemsJson;

  @Column(nullable = false)
  private int itemCount;

  @Column(nullable = false)
  private String draftPdfKey;

  private String draftSha256;

  private String signedPdfKey;

  private String signedSha256;

  @Enumerated(EnumType.STRING)
  private SignatureMethod signatureMethod;

  private String signedByName;

  private UUID signedByUserId;

  private Instant signedAt;

  private String sentTo;

  private String sentChannel;

  private Instant sentAt;

  private String sentByName;

  private String sendDetail;

  private Instant requestedSignatureAt;

  private String requestedByName;

  @Column(nullable = false)
  private String generatedByName;

  @Column(nullable = false)
  private Instant generatedAt;

  @Version
  private long version;

  protected DocumentReceipt() {}

  /** Datos del encabezado al emitir el acuse. */
  public record Header(
      UUID expedienteId,
      String folio,
      String propertyKey,
      String clientName,
      String clientEmail,
      String responsibleEntity,
      String representativeName,
      String representativeTitle,
      String responsibilityText,
      String privacyText,
      boolean textsApproved,
      Integer privacyNoticeVersion,
      String privacyNoticeSha256,
      String privacyNoticeKey,
      Instant privacyAcceptedAt) {}

  public DocumentReceipt(UUID id, int sequenceNumber, Header h, String itemsJson, int itemCount, String draftPdfKey, String draftSha256, String generatedByName, Instant now) {
    this.id = id;
    this.expedienteId = h.expedienteId();
    this.sequenceNumber = sequenceNumber;
    this.status = ReceiptStatus.DRAFT;
    this.expedienteFolio = h.folio();
    this.propertyKey = h.propertyKey();
    this.clientName = h.clientName();
    this.clientEmail = h.clientEmail();
    this.responsibleEntity = h.responsibleEntity();
    this.representativeName = h.representativeName();
    this.representativeTitle = h.representativeTitle();
    this.responsibilityText = h.responsibilityText();
    this.privacyText = h.privacyText();
    this.textsApproved = h.textsApproved();
    this.privacyNoticeVersion = h.privacyNoticeVersion();
    this.privacyNoticeSha256 = h.privacyNoticeSha256();
    this.privacyNoticeKey = h.privacyNoticeKey();
    this.privacyAcceptedAt = h.privacyAcceptedAt();
    this.itemsJson = itemsJson;
    this.itemCount = itemCount;
    this.draftPdfKey = draftPdfKey;
    this.draftSha256 = draftSha256;
    this.generatedByName = generatedByName;
    this.generatedAt = now;
  }

  public Header header() {
    return new Header(
        expedienteId,
        expedienteFolio,
        propertyKey,
        clientName,
        clientEmail,
        responsibleEntity,
        representativeName,
        representativeTitle,
        responsibilityText,
        privacyText,
        textsApproved,
        privacyNoticeVersion,
        privacyNoticeSha256,
        privacyNoticeKey,
        privacyAcceptedAt);
  }

  /** "EXP-2026-0012-A2": número de acuse que se imprime. */
  public String number() {
    return expedienteFolio + "-A" + sequenceNumber;
  }

  public void requestSignature(String byName, Instant now) {
    this.status = ReceiptStatus.PENDING_SIGNATURE;
    this.requestedByName = byName;
    this.requestedSignatureAt = now;
  }

  public void signed(SignatureMethod method, String pdfKey, String sha256, UUID userId, String byName, Instant now) {
    this.status = ReceiptStatus.SIGNED;
    this.signatureMethod = method;
    this.signedPdfKey = pdfKey;
    this.signedSha256 = sha256;
    this.signedByUserId = userId;
    this.signedByName = byName;
    this.signedAt = now;
  }

  public void sent(String to, String channel, String detail, String byName, Instant now) {
    this.status = ReceiptStatus.SENT;
    this.sentTo = to;
    this.sentChannel = channel;
    this.sendDetail = detail;
    this.sentByName = byName;
    this.sentAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public ReceiptStatus getStatus() {
    return status;
  }

  public String getExpedienteFolio() {
    return expedienteFolio;
  }

  public String getClientName() {
    return clientName;
  }

  public String getClientEmail() {
    return clientEmail;
  }

  public String getResponsibleEntity() {
    return responsibleEntity;
  }

  public String getRepresentativeName() {
    return representativeName;
  }

  public String getRepresentativeTitle() {
    return representativeTitle;
  }

  public boolean isTextsApproved() {
    return textsApproved;
  }

  public Integer getPrivacyNoticeVersion() {
    return privacyNoticeVersion;
  }

  public String getPrivacyNoticeKey() {
    return privacyNoticeKey;
  }

  public String getItemsJson() {
    return itemsJson;
  }

  public int getItemCount() {
    return itemCount;
  }

  public String getDraftPdfKey() {
    return draftPdfKey;
  }

  public String getDraftSha256() {
    return draftSha256;
  }

  public String getSignedPdfKey() {
    return signedPdfKey;
  }

  public String getSignedSha256() {
    return signedSha256;
  }

  public SignatureMethod getSignatureMethod() {
    return signatureMethod;
  }

  public String getSignedByName() {
    return signedByName;
  }

  public Instant getSignedAt() {
    return signedAt;
  }

  public String getSentTo() {
    return sentTo;
  }

  public String getSentChannel() {
    return sentChannel;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public String getSentByName() {
    return sentByName;
  }

  public String getSendDetail() {
    return sendDetail;
  }

  public Instant getRequestedSignatureAt() {
    return requestedSignatureAt;
  }

  public String getRequestedByName() {
    return requestedByName;
  }

  public String getGeneratedByName() {
    return generatedByName;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }
}
