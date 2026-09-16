package com.c21genera.documents.domain;

import com.c21genera.shared.domain.ConflictException;

public class DocumentNotReviewableException extends ConflictException {

  public DocumentNotReviewableException(String message) {
    super("DOCUMENT_NOT_REVIEWABLE", message);
  }
}
