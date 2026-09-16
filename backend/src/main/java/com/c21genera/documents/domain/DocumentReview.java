package com.c21genera.documents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_review")
public class DocumentReview {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID documentVersionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ReviewDecision decision;

  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  private ReturnReasonCode reasonCode;

  private String comment;

  @Column(nullable = false)
  private UUID reviewedBy;

  @Column(nullable = false)
  private Instant reviewedAt;

  protected DocumentReview() {}

  public DocumentReview(
      UUID documentVersionId, ReviewDecision decision, ReturnReasonCode reasonCode, String comment, UUID reviewedBy, Instant reviewedAt) {
    this.id = UUID.randomUUID();
    this.documentVersionId = documentVersionId;
    this.decision = decision;
    this.reasonCode = reasonCode;
    this.comment = comment;
    this.reviewedBy = reviewedBy;
    this.reviewedAt = reviewedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDocumentVersionId() {
    return documentVersionId;
  }

  public ReviewDecision getDecision() {
    return decision;
  }

  public ReturnReasonCode getReasonCode() {
    return reasonCode;
  }

  public String getComment() {
    return comment;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }
}
