import { apiClient } from "@/lib/api/client";
import type {
  BackendExpedienteStatus,
  BackendOperationType,
  BackendAccreditationType,
  BackendCivilStatus,
  BackendPersonType,
  BackendPropertyCaseType,
  BackendPropertyLegalStatus,
  ChangeResponse,
  ExpedienteResponse,
  LegalDetails,
  ManualClientDataResponse,
  PageResponse,
  ParticipantRequest,
  ParticipantResponse,
  RequirementResponse,
} from "@/lib/api/types";

/**
 * El nombre del expediente y el carácter con el que se firma los deriva el
 * backend de los participantes y del tipo de persona.
 */
export interface ExpedienteConfiguration {
  personType: BackendPersonType;
  signedByAttorney: boolean;
  accreditationType: BackendAccreditationType;
  condominiumRegime: boolean;
  propertyCaseType: BackendPropertyCaseType;
  operationType: BackendOperationType;
  declaredLegalStatus: BackendPropertyLegalStatus;
  propertyAddress: string;
}

/** Al crear, el domicilio y la situación jurídica se omiten: el backend los toma de los documentos del inmueble. */
export interface CreateExpedienteRequest extends Omit<ExpedienteConfiguration, "declaredLegalStatus" | "propertyAddress"> {
  declaredLegalStatus?: BackendPropertyLegalStatus;
  propertyAddress?: string;
  /** Referencia corta para que el cliente reconozca su liga (no es el domicilio legal). */
  propertyReference?: string;
  /** Control de Cambios #24: obligatorio desde el alta. */
  assignedAdvisorId: string;
  participants: ParticipantRequest[];
  legalDetails?: LegalDetails;
  /** Solo si el cliente ya tiene otro expediente en curso y quien crea puede autorizarlo. */
  duplicateAuthorizationReason?: string;
}

/** Expediente en curso del mismo cliente (para no duplicarlo). */
export interface DuplicateMatchResponse {
  id: string;
  folio: string;
  ownerDisplayName: string;
  matchedName: string;
  status: BackendExpedienteStatus;
  property: string | null;
  advisorName: string | null;
  createdAt: string;
}

export function checkClientDuplicates(holderNames: string[]) {
  return apiClient.post<DuplicateMatchResponse[]>("/internal/expedientes/duplicate-check", { holderNames });
}

export function listExpedientes(page = 0, size = 20) {
  return apiClient.get<PageResponse<ExpedienteResponse>>(`/internal/expedientes?page=${page}&size=${size}&sort=createdAt,desc`);
}

export function createExpediente(request: CreateExpedienteRequest) {
  return apiClient.post<ExpedienteResponse>("/internal/expedientes", request);
}

/** Asesor activo que se puede asignar a un expediente (Control de Cambios #24). */
export interface AdvisorOption {
  id: string;
  name: string;
  roleCode: string;
}

export function listAssignableAdvisors() {
  return apiClient.get<AdvisorOption[]>("/internal/expedientes/advisors");
}

/** Solo administrador o directivo; queda en el historial del expediente. */
export function assignAdvisor(id: string, advisorId: string, reason: string) {
  return apiClient.put<ExpedienteResponse>(`/internal/expedientes/${id}/advisor`, { advisorId, reason });
}

export function getExpediente(id: string) {
  return apiClient.get<ExpedienteResponse>(`/internal/expedientes/${id}`);
}

export function correctExpediente(id: string, configuration: ExpedienteConfiguration, reason: string) {
  return apiClient.put<ExpedienteResponse>(`/internal/expedientes/${id}`, { ...configuration, reason });
}

export function getParticipants(id: string) {
  return apiClient.get<ParticipantResponse[]>(`/internal/expedientes/${id}/participants`);
}

export function addParticipant(id: string, participant: ParticipantRequest, reason: string) {
  return apiClient.post<ParticipantResponse>(`/internal/expedientes/${id}/participants`, { participant, reason });
}

export function updateParticipant(id: string, participantId: string, participant: ParticipantRequest, reason: string) {
  return apiClient.put<ParticipantResponse>(`/internal/expedientes/${id}/participants/${participantId}`, { participant, reason });
}

export function removeParticipant(id: string, participantId: string, reason: string) {
  return apiClient.delete<void>(`/internal/expedientes/${id}/participants/${participantId}?reason=${encodeURIComponent(reason)}`);
}

export function getLegalDetails(id: string) {
  return apiClient.get<LegalDetails>(`/internal/expedientes/${id}/legal-details`);
}

export function updateLegalDetails(id: string, legalDetails: LegalDetails, reason: string) {
  return apiClient.put<LegalDetails>(`/internal/expedientes/${id}/legal-details`, { legalDetails, reason });
}

export function getClientData(id: string) {
  return apiClient.get<ManualClientDataResponse>(`/internal/expedientes/${id}/client-data`);
}

export type ClientDataUpdate = Partial<Omit<ManualClientDataResponse, "civilStatus">> & { civilStatus?: BackendCivilStatus | null; reason?: string };

export function updateClientData(id: string, data: ClientDataUpdate) {
  return apiClient.put<ManualClientDataResponse>(`/internal/expedientes/${id}/client-data`, data);
}

export function getRequirements(id: string) {
  return apiClient.get<RequirementResponse[]>(`/internal/expedientes/${id}/requirements`);
}

export function getChanges(id: string) {
  return apiClient.get<ChangeResponse[]>(`/internal/expedientes/${id}/changes`);
}

export function signReception(id: string) {
  return apiClient.post<void>(`/internal/expedientes/${id}/reception/sign`);
}

export function acceptProperty(id: string) {
  return apiClient.post<ExpedienteResponse>(`/internal/expedientes/${id}/accept-property`);
}

export function rejectProperty(id: string, reason: string) {
  return apiClient.post<ExpedienteResponse>(`/internal/expedientes/${id}/reject-property`, { reason });
}

/** Grupos de estatus del listado (ver ExpedienteOverviewController.StatusGroup en el backend). */
export type ExpedienteStatusGroup = "PENDING" | "IN_REVIEW" | "COMPLETE";

/** Documentos obligatorios que aplican: con archivo vigente (recibidos) y aceptados por el revisor. */
export interface DocumentProgress {
  required: number;
  received: number;
  accepted: number;
}

export interface ExpedienteOverviewItem {
  expediente: ExpedienteResponse;
  documents: DocumentProgress;
}

export interface GroupCounts {
  total: number;
  pending: number;
  inReview: number;
  complete: number;
}

export interface ExpedienteOverview {
  page: PageResponse<ExpedienteOverviewItem>;
  /** Totales sobre todos los expedientes visibles para el usuario con los filtros de cartera (no solo la página). */
  counts: GroupCounts;
}

/** Control de Cambios #24: filtros de cartera. advisorId "none" = sin asesor asignado. */
export interface PortfolioFilters {
  propertyCaseType?: BackendPropertyCaseType | null;
  operationType?: BackendOperationType | null;
  advisorId?: string | null;
}

export const UNASSIGNED_ADVISOR = "none";

function setPortfolioParams(search: URLSearchParams, filters: PortfolioFilters) {
  if (filters.propertyCaseType) search.set("propertyCaseType", filters.propertyCaseType);
  if (filters.operationType) search.set("operationType", filters.operationType);
  if (filters.advisorId === UNASSIGNED_ADVISOR) search.set("unassigned", "true");
  else if (filters.advisorId) search.set("advisorId", filters.advisorId);
}

export function getExpedienteOverview(params: { q?: string; group?: ExpedienteStatusGroup | null; page?: number; size?: number } & PortfolioFilters) {
  const search = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 18), sort: "updatedAt,desc" });
  if (params.q?.trim()) search.set("q", params.q.trim());
  if (params.group) search.set("group", params.group);
  setPortfolioParams(search, params);
  return apiClient.get<ExpedienteOverview>(`/internal/expedientes/overview?${search.toString()}`);
}

export interface PortfolioReport {
  /** Con todos los filtros. */
  total: GroupCounts;
  /** Nivel 1: tipo de propiedad + operación (respeta solo el filtro de asesor). */
  byTypeOperation: { propertyCaseType: BackendPropertyCaseType; operationType: BackendOperationType; counts: GroupCounts }[];
  /** Nivel 2: asesor asignado (respeta solo el tipo y la operación); advisorId null = sin asignar. */
  byAdvisor: { advisorId: string | null; name: string; counts: GroupCounts }[];
}

export function getPortfolioReport(filters: PortfolioFilters) {
  const search = new URLSearchParams();
  setPortfolioParams(search, filters);
  return apiClient.get<PortfolioReport>(`/internal/expedientes/overview/portfolio?${search.toString()}`);
}
