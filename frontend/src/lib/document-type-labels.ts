// Nombres legibles para los DocumentTypeCode del backend (ver
// backend/shared/domain/DocumentTypeCode.java). El backend nunca decide
// nombres de UI, así que este mapeo vive aquí, no allá.
export const documentTypeLabels: Record<string, string> = {
  INE: "Identificación oficial (INE)",
  PASSPORT: "Pasaporte",
  CURP: "CURP",
  TAX_STATUS_CERTIFICATE: "Constancia de Situación Fiscal",
  DEED: "Escritura completa (testimonios)",
  PROOF_OF_ADDRESS: "Comprobante de domicilio",
  LIEN_CERTIFICATE: "Certificado de libertad de gravamen",
  PROPERTY_TAX: "Predial",
  POWER_OF_ATTORNEY: "Poder notarial",
  CONDOMINIUM_REGIME: "Régimen de condominio",
  WATER_RECEIPT: "Recibo de agua",
  ELECTRICITY_RECEIPT: "Recibo de CFE",
  CADASTRAL_PLAN: "Plano catastral",
  APPRAISAL: "Avalúo",
  LAND_USE: "Uso de suelo",
  SUCCESSION: "Sucesión",
  ADJUDICATION: "Adjudicación",
  WILL: "Testamento",
  MORTGAGE: "Hipoteca",
  LEASE_AGREEMENT: "Contrato de arrendamiento",
  RPP_REGISTRATION_SLIP: "Boleta de inscripción al RPP",
  MARRIAGE_CERTIFICATE: "Acta de matrimonio",
  PRIVATE_CONTRACT: "Contrato privado de compraventa ratificado",
  INCORPORATION_DEED: "Acta constitutiva",
  OTHER: "Otro",
};

export function documentTypeLabel(type: string): string {
  return documentTypeLabels[type] ?? type;
}
