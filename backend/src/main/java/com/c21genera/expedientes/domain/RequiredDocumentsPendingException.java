package com.c21genera.expedientes.domain;

import com.c21genera.shared.domain.UnprocessableException;

public class RequiredDocumentsPendingException extends UnprocessableException {

  public RequiredDocumentsPendingException(String message) {
    super("REQUIRED_DOCUMENTS_PENDING", message);
  }
}
