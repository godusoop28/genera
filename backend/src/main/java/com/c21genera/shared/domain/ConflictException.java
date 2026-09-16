package com.c21genera.shared.domain;

import org.springframework.http.HttpStatus;

/** Estado actual del recurso impide la operación solicitada (409). */
public class ConflictException extends DomainException {

  public ConflictException(String code, String message) {
    super(code, HttpStatus.CONFLICT, message);
  }
}
