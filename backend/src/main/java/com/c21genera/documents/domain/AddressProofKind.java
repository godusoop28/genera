package com.c21genera.documents.domain;

/**
 * Control de Cambios #13: documentos aceptados como comprobante de domicilio. El cliente indica
 * cuál entrega para identificarlo en el expediente; la IA reconoce estos cinco y rechaza otros.
 */
public enum AddressProofKind {
  ELECTRICITY("Recibo de luz / energía eléctrica"),
  BANK_STATEMENT("Estado de cuenta bancario"),
  TELMEX("Recibo de Telmex"),
  PHONE("Recibo de telefonía"),
  INTERNET("Recibo de internet");

  private final String label;

  AddressProofKind(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** null si viene vacío; error de validación si no es uno de los aceptados. */
  public static AddressProofKind parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new com.c21genera.shared.domain.UnprocessableException(
          "ADDRESS_PROOF_KIND_INVALID",
          "Ese comprobante no se acepta. Elige recibo de luz, estado de cuenta bancario, recibo de Telmex, de telefonía o de internet.");
    }
  }
}
