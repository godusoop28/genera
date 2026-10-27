package com.c21genera.surveys.domain;

/** Control de Cambios #28, parte 3: por dónde llega la encuesta al cliente. */
public enum SurveyChannel {
  WHATSAPP,
  EMAIL,
  /** Dentro del portal del cliente (su liga de carga de documentos). */
  SYSTEM,
  /** El asesor genera la liga y la comparte en el momento (interacciones presenciales). */
  ADVISOR_LINK,
  /** Liga fija por punto de contacto, impresa como QR en materiales físicos. */
  QR
}
