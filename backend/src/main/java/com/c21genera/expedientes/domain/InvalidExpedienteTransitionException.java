package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.shared.domain.ConflictException;

public class InvalidExpedienteTransitionException extends ConflictException {

  public InvalidExpedienteTransitionException(ExpedienteStatus from, ExpedienteStatus to) {
    super("INVALID_EXPEDIENTE_TRANSITION", "No se puede pasar de " + from + " a " + to + ".");
  }
}
