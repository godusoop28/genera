package com.c21genera.notary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Un documento del paquete enviado a la notaría, con la versión que se envió. */
@Entity
@Table(name = "notary_submission_item")
public class NotarySubmissionItem {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private UUID documentVersionId;

  @Column(nullable = false)
  private int versionNumber;

  @Column(nullable = false, length = 200)
  private String documentName;

  protected NotarySubmissionItem() {}

  NotarySubmissionItem(UUID documentId, UUID documentVersionId, int versionNumber, String documentName) {
    this.id = UUID.randomUUID();
    this.documentId = documentId;
    this.documentVersionId = documentVersionId;
    this.versionNumber = versionNumber;
    this.documentName = documentName;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getDocumentVersionId() {
    return documentVersionId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public String getDocumentName() {
    return documentName;
  }
}
