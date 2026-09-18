// Tipos que reflejan las respuestas REALES del backend (ver /backend/src/main/java/com/c21genera/**/web/*Dtos.java).
// No confundir con src/types/expediente.ts, que es el modelo del prototipo mock.

export type BackendExpedienteStatus =
  | "DRAFT"
  | "WAITING_PRIVACY"
  | "WAITING_DOCUMENTS"
  | "DOCUMENTS_RECEIVED"
  | "UNDER_REVIEW"
  | "CORRECTIONS_REQUESTED"
  | "DOCUMENTS_APPROVED"
  | "RECEPTION_SIGNED"
  | "CONTRACT_PREPARATION"
  | "READY_FOR_SIGNATURE"
  | "PROPERTY_ACCEPTED"
  | "PROPERTY_REJECTED"
  | "CLOSED";

export type BackendPersonType = "FISICA" | "MORAL";
export type BackendSignerCharacter = "PROPIETARIO" | "COPROPIETARIO" | "APODERADO";
export type BackendAccreditationType = "ESCRITURA_PUBLICA" | "CONTRATO_PRIVADO";
export type BackendPropertyCaseType = "HOUSING" | "DEPARTMENT" | "RESIDENTIAL_LAND" | "COMMERCIAL";
export type BackendPropertyLegalStatus = "LIBRE_GRAVAMEN" | "CON_GRAVAMEN" | "EN_REVISION";
export type BackendParticipantRole = "OWNER" | "CO_OWNER" | "ATTORNEY" | "LEGAL_REPRESENTATIVE";

export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
}

export interface MeResponse {
  id: string;
  name: string;
  email: string;
  role: string;
  permissions: string[];
}

export interface ParticipantResponse {
  id: string;
  role: BackendParticipantRole;
  fullName: string;
  ordinal: number;
}

export interface ExpedienteResponse {
  id: string;
  folio: string;
  ownerDisplayName: string;
  status: BackendExpedienteStatus;
  personType: BackendPersonType;
  signerCharacter: BackendSignerCharacter;
  accreditationType: BackendAccreditationType;
  condominiumRegime: boolean;
  propertyCaseType: BackendPropertyCaseType;
  declaredLegalStatus: BackendPropertyLegalStatus;
  propertyAddress: string | null;
  decisionReason: string | null;
  decidedByUserId: string | null;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RequirementResponse {
  requirementCode: string;
  type: string;
  required: boolean;
  conditional: boolean;
  participantId: string | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ActivityResponse {
  id: string;
  occurredAt: string;
  category: string;
  message: string;
}

export interface ComplianceCheckItemResponse {
  code: string;
  passed: boolean;
  detail: string;
}

export interface ComplianceChecklistResponse {
  expedienteId: string;
  allPassed: boolean;
  items: ComplianceCheckItemResponse[];
}

export interface ClosingCaseResponse {
  id: string;
  expedienteId: string;
  status: "OPEN" | "IN_PROGRESS" | "COMPLETED";
  contractDelivered: boolean;
  contractDeliveredAt: string | null;
}

export interface ClosingNoteResponse {
  id: string;
  authorUserId: string;
  note: string;
  createdAt: string;
}

export interface ProblemDetailBody {
  title?: string;
  detail?: string;
  code?: string;
  status?: number;
}
