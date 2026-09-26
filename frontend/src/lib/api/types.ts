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
  | "CONTRACT_SIGNED"
  | "PROPERTY_ACCEPTED"
  | "PROPERTY_REJECTED"
  | "CLOSED";

export type BackendPersonType = "FISICA" | "MORAL";
export type BackendSignerCharacter = "PROPIETARIO" | "COPROPIETARIO" | "APODERADO" | "REPRESENTANTE_LEGAL";
export type BackendAccreditationType = "ESCRITURA_PUBLICA" | "CONTRATO_PRIVADO";
export type BackendPropertyCaseType = "HOUSING" | "DEPARTMENT" | "RESIDENTIAL_LAND" | "COMMERCIAL";
export type BackendPropertyLegalStatus = "LIBRE_GRAVAMEN" | "CON_GRAVAMEN" | "EN_REVISION";
export type BackendParticipantRole = "OWNER" | "CO_OWNER" | "ATTORNEY" | "LEGAL_REPRESENTATIVE";
export type BackendCivilStatus = "SOLTERO" | "CASADO" | "UNION_LIBRE" | "DIVORCIADO" | "VIUDO";
export type BackendMaritalRegime = "SOCIEDAD_CONYUGAL" | "SEPARACION_DE_BIENES";
export type BackendIdDocumentType = "INE" | "PASAPORTE" | "CEDULA_PROFESIONAL" | "FM2_RESIDENTE";

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

/** Datos individuales de cada propietario / copropietario / representante / apoderado. */
export interface ParticipantDetails {
  nationality: string | null;
  idDocumentType: BackendIdDocumentType | null;
  idDocumentNumber: string | null;
  idDocumentIssuer: string | null;
  birthDate: string | null;
  civilStatus: BackendCivilStatus | null;
  maritalRegime: BackendMaritalRegime | null;
  rfc: string | null;
  curp: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
}

export interface ParticipantResponse extends ParticipantDetails {
  id: string;
  role: BackendParticipantRole;
  fullName: string;
  ordinal: number;
}

export interface ParticipantRequest extends Partial<ParticipantDetails> {
  role: BackendParticipantRole;
  fullName: string;
}

export interface ExpedienteResponse {
  id: string;
  folio: string;
  ownerDisplayName: string;
  status: BackendExpedienteStatus;
  personType: BackendPersonType;
  signerCharacter: BackendSignerCharacter;
  signedByAttorney: boolean;
  accreditationType: BackendAccreditationType;
  condominiumRegime: boolean;
  propertyCaseType: BackendPropertyCaseType;
  declaredLegalStatus: BackendPropertyLegalStatus;
  propertyAddress: string | null;
  decisionReason: string | null;
  decidedByUserId: string | null;
  decidedAt: string | null;
  createdByUserId: string;
  correctable: boolean;
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

export interface ChangeResponse {
  id: string;
  changedAt: string;
  actorType: string;
  actorName: string | null;
  actorRole: string | null;
  section: string;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
}

export interface CompanyData {
  companyType?: string | null;
  rfc?: string | null;
  instrumentNumber?: string | null;
  instrumentDate?: string | null;
  notaryTitle?: string | null;
  notaryNumber?: string | null;
  notaryPlace?: string | null;
  notaryName?: string | null;
  commerceRegistryPlace?: string | null;
  mercantileFolio?: string | null;
}

export interface RepresentationData {
  capacity?: string | null;
  instrumentNumber?: string | null;
  instrumentDate?: string | null;
  notaryTitle?: string | null;
  notaryNumber?: string | null;
  notaryPlace?: string | null;
  notaryName?: string | null;
  registryPlace?: string | null;
  registryFolio?: string | null;
}

export interface DeedData {
  number?: string | null;
  date?: string | null;
  notaryName?: string | null;
  notaryNumber?: string | null;
  notaryPlace?: string | null;
  registryData?: string | null;
}

export interface PrivateContractData {
  sellerName?: string | null;
  buyerName?: string | null;
  date?: string | null;
  ratificationDate?: string | null;
  ratifiedBefore?: string | null;
  notaryNumber?: string | null;
  notaryPlace?: string | null;
  notaryName?: string | null;
  registryDate?: string | null;
  registryPlace?: string | null;
  realFolio?: string | null;
}

export interface CondominiumData {
  deedNumber?: string | null;
  date?: string | null;
  notaryNumber?: string | null;
  notaryPlace?: string | null;
  notaryName?: string | null;
  registryDate?: string | null;
  realFolio?: string | null;
}

/** Dato detectado por la revisión automática en un documento del expediente. */
export interface ExpedienteObservationResponse {
  id: string;
  documentId: string;
  documentType: string;
  participantId: string | null;
  documentStatus: string;
  fieldName: string;
  value: string;
  confirmed: boolean;
  confidence: number | null;
}

export interface LegalDetails {
  company?: CompanyData | null;
  representation?: RepresentationData | null;
  deed?: DeedData | null;
  privateContract?: PrivateContractData | null;
  condominium?: CondominiumData | null;
  propertyChecklist?: Record<string, boolean> | null;
  advertisingMedia?: string | null;
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
  action: string | null;
  actorType: "CLIENT" | "STAFF" | "SYSTEM" | "UNKNOWN" | null;
  actorName: string | null;
  actorRole: string | null;
  documentId: string | null;
  documentLabel: string | null;
  message: string;
}

export interface NotificationResponse {
  id: string;
  kind: string;
  recipient: string;
  subject: string;
  status: "QUEUED" | "SENT" | "RETRYING" | "FAILED" | "SKIPPED";
  attempts: number;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
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
  signedContractId: string | null;
}

export interface ClosingTaskResponse {
  id: string;
  code: string;
  title: string;
  dueDate: string | null;
  done: boolean;
  doneAt: string | null;
  doneByUserId: string | null;
  doneNote: string | null;
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
  | "REPLACED"
  | "NOT_APPLICABLE";

export type BackendProcessingStatus = "QUEUED" | "PROCESSING" | "QUALITY_FAILED" | "PROCESSED" | "FAILED";

export type ReturnReasonCode =
  | "BLURRY_IMAGE"
  | "INCOMPLETE_DOCUMENT"
  | "EXPIRED_DOCUMENT"
  | "ILLEGIBLE_INFORMATION"
  | "WRONG_DOCUMENT"
  | "MISSING_PAGE"
  | "OTHER";

export interface LatestVersionSummary {
  id: string;
  versionNumber: number;
  uploadedAt: string;
  uploadedVia: "PUBLIC_PORTAL" | "INTERNAL";
  uploadedByName: string | null;
  processingStatus: BackendProcessingStatus;
  processingError: string | null;
  aiTypeMatches: boolean | null;
  aiLegible: boolean | null;
  aiDetectedKind: string | null;
  aiObservations: string | null;
  aiAssessedAt: string | null;
  blockingIssues: string[];
}

export interface DocumentResponse {
  id: string;
  expedienteId: string;
  requirementCode: string;
  type: string;
  participantId: string | null;
  required: boolean;
  status: BackendDocumentStatus;
  currentVersionNumber: number;
  notApplicableJustification: string | null;
  notApplicableAt: string | null;
  lastReviewDecision: "ACCEPTED" | "RETURNED" | "REJECTED" | null;
  lastReviewReasonCode: ReturnReasonCode | null;
  lastReviewComment: string | null;
  lastReviewedAt: string | null;
  latestVersion: LatestVersionSummary | null;
}

export interface PublicDocumentResponse {
  id: string;
  type: string;
  participantId: string | null;
  required: boolean;
  status: BackendDocumentStatus;
  currentVersionNumber: number;
  correctionReasonCode: ReturnReasonCode | null;
  correctionComment: string | null;
  qualityIssue: string | null;
  looksLikeWrongDocument: boolean;
  processing: boolean;
}

export interface DocumentVersionResponse {
  id: string;
  documentId: string;
  versionNumber: number;
  uploadedAt: string;
  uploadedVia: string;
  uploadedByName: string | null;
  processingStatus: BackendProcessingStatus;
  processingError: string | null;
}

export interface ReviewHistoryResponse {
  id: string;
  documentVersionId: string;
  decision: "ACCEPTED" | "RETURNED" | "REJECTED";
  reasonCode: ReturnReasonCode | null;
  comment: string | null;
  reviewedBy: string;
  reviewedAt: string;
  overrideJustification: string | null;
  overriddenIssues: string | null;
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
  resolvedAutomatically: boolean;
  resolutionNote: string | null;
  resolvedByUserId: string | null;
  detectedAt: string;
  resolvedAt: string | null;
}

export type ContractGenerationStatus =
  | "DRAFT_INCOMPLETE"
  | "GENERATED"
  | "PARTIALLY_SIGNED"
  | "SIGNED"
  | "DELIVERED"
  | "SUPERSEDED";

export interface ContractSignatureResponse {
  id: string;
  party: "CLIENT" | "INTERMEDIARY";
  participantId: string | null;
  signerName: string;
  signerCapacity: string;
  hasEmail: boolean;
  status: "PENDING" | "SIGNED" | "VOIDED";
  method: "ELECTRONIC_SIMPLE" | "AUTOGRAPH_SCAN" | null;
  requestedAt: string;
  signedAt: string | null;
  linkExpiresAt: string | null;
  documentSha256: string | null;
  ipAddress: string | null;
  typedName: string | null;
  evidenceSha256: string | null;
  registeredByUserId: string | null;
  voidedReason: string | null;
}

export interface ContractGenerationResponse {
  id: string;
  expedienteId: string;
  versionNumber: number;
  generatedAt: string;
  generatedBy: string;
  documentSha256: string | null;
  status: ContractGenerationStatus;
  variantSummary: string | null;
  missingItems: string[];
  hasPdf: boolean;
  hasSignedPackage: boolean;
  signedAt: string | null;
  deliveredAt: string | null;
  deliveryMethod: string | null;
  supersededAt: string | null;
  supersededReason: string | null;
  signatures: ContractSignatureResponse[];
}

export interface SigningLinkResponse {
  signatureId: string;
  signerName: string;
  signerCapacity: string;
  url: string;
  expiresAt: string;
  emailed: boolean;
}

export interface GenerateContractResponse {
  contract: ContractGenerationResponse;
  signingLinks: SigningLinkResponse[];
}

export interface ContractReadinessResponse {
  ready: boolean;
  blockers: string[];
  missingData: string[];
  variant: string;
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

export interface SigningViewResponse {
  folio: string;
  versionNumber: number;
  generatedAt: string;
  signerName: string;
  signerCapacity: string;
  documentSha256: string;
  pdfUrl: string;
  consentText: string;
  alreadySigned: boolean;
}

export interface PublicLinkStatusResponse {
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  lastUsedAt: string | null;
  usable: boolean;
}

// --- Portal público del cliente (sin cuenta, autenticado por token en la URL) ---

export interface PublicExpedienteResponse {
  folio: string;
  status: BackendExpedienteStatus;
  personType: BackendPersonType;
  maskedOwnerName: string;
  maskedPropertyAddress: string;
}

export interface PublicParticipantResponse {
  id: string;
  role: BackendParticipantRole;
  displayName: string;
  civilStatus: BackendCivilStatus | null;
  maritalRegime: BackendMaritalRegime | null;
}

export interface PublicClientDataResponse {
  email: string | null;
  phone: string | null;
  notificationAddress: string | null;
  civilStatus: BackendCivilStatus | null;
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

export interface ManualClientDataResponse {
  civilStatus: BackendCivilStatus | null;
  authorizedPrice: string | number | null;
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
  landAreaM2: string | number | null;
  builtAreaM2: string | number | null;
}

export interface SubmitDocumentsResponse {
  submitted: boolean;
  status: BackendExpedienteStatus;
}

export interface EmailAttachmentPreview {
  fileName: string;
  downloadUrl: string;
}

export type BackendRoleCode = "ADMINISTRATOR" | "DIRECTOR" | "ADVISOR" | "DOCUMENT_REVIEWER";
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
