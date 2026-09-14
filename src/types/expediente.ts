export type ContractType = "Compraventa" | "Arrendamiento" | "Otro";

export type OwnerCount = 1 | 2 | 3;

export type AccreditationType = "Escritura pública" | "Contrato privado" | "Otro";

export type PersonType = "Persona física" | "Persona moral";

export type PropertyLegalStatus = "Libre de gravamen" | "Con gravamen" | "En proceso";

export interface ExpedienteConfig {
  contractType: ContractType;
  ownerCount: OwnerCount;
  accreditation: AccreditationType;
  personType: PersonType;
  legalStatus: PropertyLegalStatus;
  condominiumRegime: boolean;
  signedByRepresentative: boolean;
}

export type DocumentStatus =
  | "pending"
  | "reviewing"
  | "uploaded"
  | "validated"
  | "error"
  | "converted";

export interface DocumentRequirement {
  id: string;
  name: string;
  description?: string;
  required: boolean;
  conditional?: boolean;
  status?: DocumentStatus;
}

export type ExpedienteStatus =
  | "Borrador"
  | "Esperando documentos"
  | "Documentos recibidos"
  | "En revisión"
  | "Listo para contrato";

export interface Expediente {
  id: string;
  folio: string;
  ownerName: string;
  contractType: ContractType;
  status: ExpedienteStatus;
  updatedAt: string;
}

export interface ReceivedDocument {
  id: string;
  name: string;
  receivedAt: string;
  status: "Validado" | "Convertido a PDF";
}

export interface ExtractedField {
  label: string;
  value: string;
  source: "auto" | "manual";
  suffix?: string;
}

export interface CalculatedDataItem {
  label: string;
  value: string;
}
