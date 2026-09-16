package com.c21genera.shared.domain;

import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de dominio/reglas de negocio. Cada subclase fija
 * su propio código estable (para el cliente) y el HTTP status semántico que
 * le corresponde (ver AGENTS §116-117, §198).
 */
public abstract class DomainException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  protected DomainException(String code, HttpStatus status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }
}
