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

export type BackendDocumentStatus =
  | "PENDING"
  | "UPLOADED"
  | "READY_FOR_REVIEW"
  | "ACCEPTED"
  | "RETURNED"
  | "REJECTED"
  | "REPLACED";

export type BackendProcessingStatus = "QUEUED" | "PROCESSING" | "QUALITY_FAILED" | "PROCESSED" | "FAILED";

export type ReturnReasonCode =
  | "BLURRY_IMAGE"
  | "INCOMPLETE_DOCUMENT"
  | "EXPIRED_DOCUMENT"
  | "ILLEGIBLE_INFORMATION"
  | "WRONG_DOCUMENT"
  | "MISSING_PAGE"
  | "OTHER";

export interface DocumentResponse {
  id: string;
  expedienteId: string;
  requirementCode: string;
  type: string;
  participantId: string | null;
  required: boolean;
  status: BackendDocumentStatus;
  currentVersionNumber: number;
}

export interface DocumentVersionResponse {
  id: string;
  documentId: string;
  versionNumber: number;
  uploadedAt: string;
  uploadedVia: string;
  processingStatus: BackendProcessingStatus;
}

export interface DownloadResponse {
  url: string;
}

export interface ExtractedFieldObservationResponse {
  id: string;
  documentId: string;
  documentVersionId: string;
  fieldName: string;
  detectedValue: string | null;
  confirmedValue: string | null;
  origin: string;
  confidence: number | null;
  updatedAt: string;
}

export interface DataConflictResponse {
  id: string;
  fieldName: string;
  description: string;
  resolved: boolean;
  detectedAt: string;
  resolvedAt: string | null;
}

export type ContractGenerationStatus = "GENERATED" | "SIGNED" | "DELIVERED";

export interface ContractGenerationResponse {
  id: string;
  expedienteId: string;
  versionNumber: number;
  docxStorageKey: string;
  pdfStorageKey: string;
  generatedAt: string;
  generatedBy: string;
  sha256: string;
  status: ContractGenerationStatus;
  signedAt: string | null;
  deliveredAt: string | null;
  deliveryMethod: string | null;
}

export interface ContractCalculationsResponse {
  price: string;
  priceWritten: string;
  commission: string;
  vat: string;
  totalCommissionWithVat: string;
  penalty: string;
  exclusivityDays: number;
  exclusivityEndDate: string;
}

// --- Portal público del cliente (sin cuenta, autenticado por token en la URL) ---

export interface PublicExpedienteResponse {
  folio: string;
  status: BackendExpedienteStatus;
}

export interface PrivacyNoticeResponse {
  templateId: string;
  type: string;
  version: number;
  sha256: string;
  effectiveFrom: string;
}

export interface PrivacyConsentResponse {
  mainPurposesAccepted: boolean;
  secondaryPurposesAccepted: boolean;
  acceptedAt: string;
  hasSignature: boolean;
}

export type BackendCivilStatus = "SOLTERO" | "CASADO" | "UNION_LIBRE" | "DIVORCIADO" | "VIUDO";

export interface ManualClientDataResponse {
  civilStatus: BackendCivilStatus | null;
  authorizedPrice: string | null;
  email: string | null;
  phone: string | null;
  notificationAddress: string | null;
  visitInstructions: string | null;
  marketingDataAuthorized: boolean | null;
  receiveAdsAuthorized: boolean | null;
  additionalServicesRequested: string | null;
  bedrooms: number | null;
  bathrooms: number | null;
  parkingSpots: number | null;
  conservationStatus: string | null;
  availableServices: string | null;
  relevantFeatures: string | null;
  contractSignatureDate: string | null;
}

export interface SubmitDocumentsResponse {
  submitted: boolean;
  status: BackendExpedienteStatus;
}

export interface EmailAttachmentPreview {
  fileName: string;
  downloadUrl: string;
}

export type BackendRoleCode = "ADMINISTRATOR" | "ADVISOR" | "DOCUMENT_REVIEWER";
export type BackendUserStatus = "ACTIVE" | "INACTIVE";

export interface UserResponse {
  id: string;
  name: string;
  email: string;
  roleCode: BackendRoleCode;
  roleName: string;
  status: BackendUserStatus;
  lastActivityAt: string | null;
}

export interface RoleResponse {
  id: string;
  code: BackendRoleCode;
  name: string;
  description: string;
  permissions: string[];
}

export interface PermissionResponse {
  code: string;
  description: string;
}

export interface EmailPreviewResponse {
  subject: string;
  body: string;
  attachments: EmailAttachmentPreview[];
  documentTypesWithoutFile: string[];
}
