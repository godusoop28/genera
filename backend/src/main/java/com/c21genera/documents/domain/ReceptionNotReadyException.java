package com.c21genera.documents.domain;

import com.c21genera.shared.domain.ConflictException;
import java.util.UUID;

public class ReceptionNotReadyException extends ConflictException {

  public ReceptionNotReadyException(UUID expedienteId) {
    super(
        "RECEPTION_NOT_READY",
        "El expediente " + expedienteId + " todavía no tiene todos los documentos obligatorios aceptados.");
  }
}
