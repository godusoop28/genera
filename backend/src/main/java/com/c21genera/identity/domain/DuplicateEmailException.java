package com.c21genera.identity.domain;

import com.c21genera.shared.domain.ConflictException;

public class DuplicateEmailException extends ConflictException {

  public DuplicateEmailException(String email) {
    super("DUPLICATE_EMAIL", "Ya existe un usuario con el correo " + email + ".");
  }
}
