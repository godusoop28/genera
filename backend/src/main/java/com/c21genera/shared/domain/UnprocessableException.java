package com.c21genera.shared.domain;

import org.springframework.http.HttpStatus;

/** La petición es válida sintácticamente pero viola una regla de negocio (422). */
public class UnprocessableException extends DomainException {

  public UnprocessableException(String code, String message) {
    super(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
  }
}
