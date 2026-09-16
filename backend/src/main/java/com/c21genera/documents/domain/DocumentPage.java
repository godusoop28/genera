package com.c21genera.documents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Una fotografía/página original dentro de una {@link DocumentVersion}, en orden (ver AGENTS §43). */
@Entity
@Table(name = "document_page")
public class DocumentPage {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID documentVersionId;

  @Column(nullable = false)
  private int pageNumber;

  @Column(nullable = false)
  private String storageKeyOriginal;

  @Column(nullable = false)
  private String originalFilename;

  @Column(nullable = false)
  private String mimeType;

  @Column(nullable = false)
  private long size;

  @Column(nullable = false, length = 64)
  private String sha256;

  protected DocumentPage() {}

  public DocumentPage(
      UUID documentVersionId,
      int pageNumber,
      String storageKeyOriginal,
      String originalFilename,
      String mimeType,
      long size,
      String sha256) {
    this.id = UUID.randomUUID();
    this.documentVersionId = documentVersionId;
    this.pageNumber = pageNumber;
    this.storageKeyOriginal = storageKeyOriginal;
    this.originalFilename = originalFilename;
    this.mimeType = mimeType;
    this.size = size;
    this.sha256 = sha256;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDocumentVersionId() {
    return documentVersionId;
  }

  public int getPageNumber() {
    return pageNumber;
  }

  public String getStorageKeyOriginal() {
    return storageKeyOriginal;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getSize() {
    return size;
  }

  public String getSha256() {
    return sha256;
  }
}
