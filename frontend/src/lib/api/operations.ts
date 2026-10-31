// Control de Cambios #32: operaciones de cierre (propias y externas) y su revisión documental.
import { apiClient, uploadFiles } from "@/lib/api/client";

export type OperationOrigin = "OWN" | "EXTERNAL";
export type OperationType = "SALE" | "RENT";
export type RepresentedSide = "SELLER" | "BUYER" | "BOTH";
export type OperationStatus = "IN_PROCESS" | "CLOSING_REVIEW" | "CLOSED" | "CANCELLED";
export type DocStatus = "PENDING" | "IN_REVIEW" | "COMPLETE_VALIDATED";
export type RequirementSection = "OPERATION" | "PLD" | "PROFECO";
export type PartySide = "SELLER" | "BUYER";
export type RequirementStatus = "PENDING" | "RECEIVED" | "IN_REVIEW" | "VALIDATED" | "NEEDS_CORRECTION" | "NOT_APPLICABLE";
export type RequestedFrom = "CLIENT" | "COUNTERPARTY" | "OFFICE" | "THIRD_PARTY" | "INTERNAL";

export interface OperationItem {
  id: string;
  folio: string;
  origin: OperationOrigin;
  expedienteId: string | null;
  operationType: OperationType;
  propertyDescription: string;
  representedSide: RepresentedSide;
  advisorId: string | null;
  advisorName: string | null;
  status: OperationStatus;
  docStatus: DocStatus;
  displayStatus: string;
  closedOn: string | null;
  closedByName: string | null;
  amount: number | null;
  currency: string | null;
  sellerName: string | null;
  buyerName: string | null;
  requirementsTotal: number;
  requirementsSatisfied: number;
  requirementsMissing: number;
  docDueDate: string | null;
  docFollowUpOwner: string | null;
  updatedAt: string;
}

export interface OperationList {
  items: OperationItem[];
  totals: { total: number; closed: number; closedPendingDocs: number; inReview: number; external: number };
  advisors: Array<{ id: string; name: string }>;
}

export interface PartyInput {
  fullName: string;
  personType?: string | null;
  representativeName?: string | null;
  representativeRole?: string | null;
  email?: string | null;
  phone?: string | null;
  counterpartContact?: string | null;
  notes?: string | null;
}

export interface PartyView extends PartyInput {
  side: PartySide;
  representedByGenera: boolean;
}

export interface EvidenceView {
  id: string;
  versionNumber: number;
  kind: "FILE" | "EXPEDIENTE_DOCUMENT" | "NOTE";
  filename: string | null;
  sizeBytes: number | null;
  documentId: string | null;
  documentVersionId: string | null;
  documentName: string | null;
  note: string | null;
  documentDate: string | null;
  expiresOn: string | null;
  attachedByName: string | null;
  attachedAt: string;
}

export interface RequirementView {
  id: string;
  section: RequirementSection;
  partySide: PartySide | null;
  label: string;
  description: string | null;
  mandatory: boolean;
  validityDays: number | null;
  documentTypeHint: string | null;
  status: RequirementStatus;
  requestedFrom: RequestedFrom | null;
  requestedAt: string | null;
  followUpOwner: string | null;
  dueDate: string | null;
  observation: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  naJustification: string | null;
  naRequestedByName: string | null;
  naRequestedAt: string | null;
  naApprovedByName: string | null;
  naApprovedAt: string | null;
  satisfied: boolean;
  expired: boolean;
  dateMissing: boolean;
  currentEvidence: EvidenceView | null;
  previousEvidence: EvidenceView[];
}

export interface OperationDetail {
  operation: OperationItem;
  propertyAddress: string | null;
  externalSource: string | null;
  externalOffice: string | null;
  externalContact: string | null;
  generaParticipation: string | null;
  cancelReason: string | null;
  docAuthorizedByName: string | null;
  docAuthorizedAt: string | null;
  createdByName: string | null;
  createdAt: string;
  expedienteFolio: string | null;
  parties: PartyView[];
  requirements: RequirementView[];
  summary: {
    missingBySide: Record<string, number>;
    missingBySection: Record<string, number>;
    needsUpdate: number;
    needsCorrection: number;
    pendingNaApprovals: number;
    satisfied: number;
    total: number;
    blockers: string[];
  };
  history: Array<{ id: string; requirementId: string | null; type: string; detail: string | null; actorName: string | null; occurredAt: string }>;
  expedienteDocuments: Array<{ documentId: string; versionId: string; type: string | null; name: string | null; status: string }>;
  matrixApproved: boolean;
  newMatrixRequirements: number;
}

export interface CreateOperation {
  origin: OperationOrigin;
  expedienteId?: string | null;
  operationType: OperationType;
  representedSide: RepresentedSide;
  propertyDescription?: string | null;
  propertyAddress?: string | null;
  externalSource?: string | null;
  externalOffice?: string | null;
  externalContact?: string | null;
  generaParticipation?: string | null;
  seller?: PartyInput | null;
  buyer?: PartyInput | null;
}

export interface UpdateOperation {
  representedSide?: RepresentedSide;
  propertyDescription?: string | null;
  propertyAddress?: string | null;
  externalSource?: string | null;
  externalOffice?: string | null;
  externalContact?: string | null;
  generaParticipation?: string | null;
  amount?: number | null;
  currency?: string | null;
  seller?: PartyInput | null;
  buyer?: PartyInput | null;
}

export interface OperationFilters {
  status?: string;
  docStatus?: string;
  origin?: string;
  advisorId?: string;
  closedFrom?: string;
  closedTo?: string;
  q?: string;
  pendingDocs?: boolean;
}

const base = "/internal/operations";

export function listOperations(f: OperationFilters) {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(f)) if (v !== undefined && v !== "" && v !== false) params.set(k, String(v));
  const qs = params.toString();
  return apiClient.get<OperationList>(`${base}${qs ? `?${qs}` : ""}`);
}

export const getOperation = (id: string) => apiClient.get<OperationDetail>(`${base}/${id}`);
export const operationsOfExpediente = (expedienteId: string) => apiClient.get<OperationItem[]>(`${base}/by-expediente/${expedienteId}`);
export const createOperation = (body: CreateOperation) => apiClient.post<OperationDetail>(base, body);
export const updateOperation = (id: string, body: UpdateOperation) => apiClient.put<OperationDetail>(`${base}/${id}`, body);
export const moveToClosingReview = (id: string) => apiClient.post<OperationDetail>(`${base}/${id}/closing-review`, {});
export const closeOperation = (id: string, body: { closedOn?: string | null; amount?: number | null; currency?: string | null; docDueDate?: string | null; docFollowUpOwner?: string | null }) =>
  apiClient.post<OperationDetail>(`${base}/${id}/close`, body);
export const cancelOperation = (id: string, reason: string) => apiClient.post<OperationDetail>(`${base}/${id}/cancel`, { reason });
export const reopenOperation = (id: string, reason: string) => apiClient.post<OperationDetail>(`${base}/${id}/reopen`, { reason });
export const operationFollowUp = (id: string, dueDate: string | null, owner: string | null) => apiClient.post<OperationDetail>(`${base}/${id}/follow-up`, { dueDate, owner });
export const applyCurrentMatrix = (id: string) => apiClient.post<OperationDetail>(`${base}/${id}/apply-matrix`, {});
export const authorizeOperation = (id: string) => apiClient.post<OperationDetail>(`${base}/${id}/authorize`, {});
export const reopenDocReview = (id: string, reason: string) => apiClient.post<OperationDetail>(`${base}/${id}/reopen-review`, { reason });

export function attachEvidenceFile(id: string, requirementId: string, file: File, documentDate?: string, note?: string) {
  const fields: Record<string, string> = {};
  if (documentDate) fields.documentDate = documentDate;
  if (note) fields.note = note;
  return uploadFiles<OperationDetail>(`${base}/${id}/requirements/${requirementId}/evidence/file`, "file", [file], undefined, fields);
}

export const linkEvidenceDocument = (id: string, requirementId: string, documentId: string, documentDate?: string | null, note?: string | null) =>
  apiClient.post<OperationDetail>(`${base}/${id}/requirements/${requirementId}/evidence/document`, { documentId, documentDate: documentDate || null, note: note || null });
export const addEvidenceNote = (id: string, requirementId: string, note: string, documentDate?: string | null) =>
  apiClient.post<OperationDetail>(`${base}/${id}/requirements/${requirementId}/evidence/note`, { note, documentDate: documentDate || null });
export const requirementFollowUp = (id: string, requirementId: string, body: { requestedFrom: RequestedFrom | null; owner: string | null; dueDate: string | null }) =>
  apiClient.post<OperationDetail>(`${base}/${id}/requirements/${requirementId}/follow-up`, body);
export const reviewRequirement = (id: string, requirementId: string, status: RequirementStatus, observation?: string | null) =>
  apiClient.post<OperationDetail>(`${base}/${id}/requirements/${requirementId}/review`, { status, observation: observation || null });
export const requestNotApplicable = (id: string, requirementId: string, justification: string) =>
  apiClient.post<OperationDetail>(`${base}/${id}/requirements/${requirementId}/not-applicable`, { justification });
export const decideNotApplicable = (id: string, requirementId: string, approve: boolean, observation?: string | null) =>
  apiClient.post<OperationDetail>(`${base}/${id}/requirements/${requirementId}/not-applicable/decision`, { approve, observation: observation || null });
export const evidenceDownload = (id: string, evidenceId: string) => apiClient.get<{ url: string }>(`${base}/${id}/evidence/${evidenceId}/download`);

// --- Matriz documental ---

export interface MatrixItem {
  id: string;
  section: RequirementSection;
  partySide: PartySide | null;
  operationType: OperationType | null;
  origin: OperationOrigin | null;
  label: string;
  description: string | null;
  mandatory: boolean;
  validityDays: number | null;
  documentTypeHint: string | null;
  sortOrder: number;
  active: boolean;
}

export interface MatrixView {
  approved: boolean;
  approvedByName: string | null;
  approvedAt: string | null;
  revision: number;
  updatedByName: string | null;
  updatedAt: string;
  items: MatrixItem[];
}

export type MatrixInput = Omit<MatrixItem, "id">;

export const getMatrix = () => apiClient.get<MatrixView>(`${base}/matrix`);
export const addMatrixItem = (body: MatrixInput) => apiClient.post<MatrixView>(`${base}/matrix/items`, body);
export const updateMatrixItem = (itemId: string, body: MatrixInput) => apiClient.put<MatrixView>(`${base}/matrix/items/${itemId}`, body);
export const approveMatrix = (approved: boolean) => apiClient.post<MatrixView>(`${base}/matrix/approval`, { approved });

// --- Etiquetas ---

export const operationStatusLabels: Record<OperationStatus, string> = {
  IN_PROCESS: "En proceso",
  CLOSING_REVIEW: "En revisión de cierre",
  CLOSED: "Cerrada",
  CANCELLED: "Cancelada",
};
export const docStatusLabels: Record<DocStatus, string> = { PENDING: "Pendiente", IN_REVIEW: "En revisión", COMPLETE_VALIDATED: "Completa y validada" };
export const requirementStatusLabels: Record<RequirementStatus, string> = {
  PENDING: "Pendiente",
  RECEIVED: "Recibido",
  IN_REVIEW: "En revisión",
  VALIDATED: "Validado",
  NEEDS_CORRECTION: "Requiere corrección",
  NOT_APPLICABLE: "No aplica",
};
export const requirementStatusTone: Record<RequirementStatus, "neutral" | "info" | "warning" | "success" | "danger"> = {
  PENDING: "neutral",
  RECEIVED: "info",
  IN_REVIEW: "info",
  VALIDATED: "success",
  NEEDS_CORRECTION: "danger",
  NOT_APPLICABLE: "warning",
};
export const sectionLabels: Record<RequirementSection, string> = { OPERATION: "Documentación de la operación", PLD: "PLD", PROFECO: "PROFECO" };
export const originLabels: Record<OperationOrigin, string> = { OWN: "Cartera propia", EXTERNAL: "Propiedad externa" };
export const operationTypeLabels: Record<OperationType, string> = { SALE: "Compraventa", RENT: "Arrendamiento" };
export const requestedFromLabels: Record<RequestedFrom, string> = {
  CLIENT: "Cliente de C21 Genera",
  COUNTERPARTY: "Contraparte (por su asesor)",
  OFFICE: "Oficina participante",
  THIRD_PARTY: "Tercero",
  INTERNAL: "Equipo C21 Genera",
};

export function partyLabel(type: OperationType, side: PartySide): string {
  if (type === "RENT") return side === "SELLER" ? "Arrendador" : "Arrendatario";
  return side === "SELLER" ? "Vendedor" : "Comprador";
}

export function representedLabel(type: OperationType, side: RepresentedSide): string {
  if (side === "BOTH") return "Ambas partes";
  return partyLabel(type, side);
}

export function displayTone(item: Pick<OperationItem, "status" | "docStatus">): "neutral" | "info" | "warning" | "success" | "danger" {
  if (item.status === "CANCELLED") return "danger";
  if (item.status === "CLOSED") return item.docStatus === "COMPLETE_VALIDATED" ? "success" : "warning";
  if (item.status === "CLOSING_REVIEW") return "info";
  return "neutral";
}
