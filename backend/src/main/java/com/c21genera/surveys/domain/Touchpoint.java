package com.c21genera.surveys.domain;

/**
 * Control de Cambios #28, parte 2: los 17 puntos de contacto con el cliente.
 * Del 1 al 10 los dispara el sistema; del 11 al 17 los activa el asesor (el
 * 17, seguimiento post-venta, se programa solo al registrar el cierre).
 */
public enum Touchpoint {
  REGISTRATION(1, true, "Registro inicial / alta del expediente", "con el registro de tu expediente"),
  DOCUMENT_PREP(2, true, "Preparación de documentos", "con las instrucciones para preparar tus documentos"),
  DOCUMENTS_UPLOADED(3, true, "Carga de documentos completada", "al cargar tus documentos"),
  PRIVACY_SIGNED(4, true, "Firma del aviso de privacidad", "con la firma del aviso de privacidad"),
  AI_VALIDATION(5, true, "Validación de documentos completada", "con la revisión de tus documentos"),
  DOCUMENT_UPDATE(6, true, "Solicitud de documento adicional o actualización", "con la solicitud de actualizar un documento"),
  CONTRACT_SENT(7, true, "Envío del contrato para firma", "con el envío de tu contrato"),
  CONTRACT_SIGNED(8, true, "Firma del contrato completada", "con la firma de tu contrato"),
  NOTARY_SENT(9, true, "Envío de documentos a notaría", "con el envío de tus documentos a la notaría"),
  CLOSING(10, true, "Cierre de la transacción", "con el cierre de tu operación"),
  FIRST_VISIT(11, false, "Primera visita al inmueble", "en la visita al inmueble"),
  PROPOSAL(12, false, "Presentación de propuesta o valuación", "con la presentación de la propuesta"),
  NEGOTIATION(13, false, "Negociación de oferta", "en la negociación de la oferta"),
  IN_PERSON_SIGNING(14, false, "Firma presencial del contrato", "en la firma del contrato"),
  KEY_HANDOVER(15, false, "Entrega de llaves / cierre presencial", "en la entrega del inmueble"),
  ISSUE_RESOLVED(16, false, "Atención de una inconformidad", "con la atención de tu solicitud"),
  POST_SALE(17, false, "Seguimiento post-venta (30 días)", "con todo el proceso");

  private final int number;
  private final boolean systemTriggered;
  private final String label;
  private final String moment;

  Touchpoint(int number, boolean systemTriggered, String label, String moment) {
    this.number = number;
    this.systemTriggered = systemTriggered;
    this.label = label;
    this.moment = moment;
  }

  public int number() {
    return number;
  }

  /** true: lo dispara un evento del sistema; false: lo activa el asesor. */
  public boolean systemTriggered() {
    return systemTriggered;
  }

  public String label() {
    return label;
  }

  /** Complemento para el mensaje al cliente: "¿cómo te fue …?". */
  public String moment() {
    return moment;
  }
}
