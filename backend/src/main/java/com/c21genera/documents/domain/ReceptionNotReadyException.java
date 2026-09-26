package com.c21genera.documents.domain;

import com.c21genera.shared.domain.ConflictException;
import java.util.UUID;

public class ReceptionNotReadyException extends ConflictException {

  public ReceptionNotReadyException(UUID expedienteId) {
    super(
        "RECEPTION_NOT_READY",
        "Todavía hay documentos obligatorios sin aceptar (o sin marcar como \"No aplica\"); no se puede firmar la recepción.");
  }
}
