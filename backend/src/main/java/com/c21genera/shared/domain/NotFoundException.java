package com.c21genera.shared.domain;

import org.springframework.http.HttpStatus;

public class NotFoundException extends DomainException {

  public NotFoundException(String entity, Object id) {
    super("NOT_FOUND", HttpStatus.NOT_FOUND, entity + " no encontrado: " + id);
  }

  public NotFoundException(String message) {
    super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
  }
}
