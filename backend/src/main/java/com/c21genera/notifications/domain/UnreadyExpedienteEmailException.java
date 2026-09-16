package com.c21genera.notifications.domain;

import com.c21genera.shared.domain.UnprocessableException;
import java.util.UUID;

public class UnreadyExpedienteEmailException extends UnprocessableException {

  public UnreadyExpedienteEmailException(UUID expedienteId) {
    super("NO_ACCEPTED_DOCUMENTS", "El expediente " + expedienteId + " todavía no tiene documentos aceptados para enviar por correo.");
  }
}
