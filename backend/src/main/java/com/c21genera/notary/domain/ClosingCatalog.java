package com.c21genera.notary.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.Set;

/**
 * Control de Cambios #23: documentos que en el cierre (oferta aceptada) deben entregarse
 * actualizados, vigentes y al corriente de pago. Catálogo inicial para compraventa.
 * TODO: Confirmar dato canónico con CENTURY 21 Genera antes de producción (catálogo por tipo de operación).
 */
public final class ClosingCatalog {

  private ClosingCatalog() {}

  public static final Set<DocumentTypeCode> CLOSING_TYPES =
      Set.of(
          DocumentTypeCode.PROPERTY_TAX,
          DocumentTypeCode.WATER_RECEIPT,
          DocumentTypeCode.ELECTRICITY_RECEIPT,
          DocumentTypeCode.LIEN_CERTIFICATE,
          DocumentTypeCode.TAX_STATUS_CERTIFICATE,
          DocumentTypeCode.PROOF_OF_ADDRESS,
          DocumentTypeCode.INE,
          DocumentTypeCode.PASSPORT);

  public static boolean requiredForClosing(DocumentTypeCode type) {
    return type != null && CLOSING_TYPES.contains(type);
  }
}
