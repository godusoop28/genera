import { apiClient } from "@/lib/api/client";
import type {
  BackendExpedienteStatus,
  BackendOperationType,
  BackendAccreditationType,
  BackendCivilStatus,
  BackendPersonType,
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
  /**
   * Control de Cambios #26: subcategoría del catálogo (la categoría sale de ella). En una corrección,
   * null solo conserva un expediente que sigue pendiente de clasificación.
   */
  propertySubtypeId: string | null;
  /** Solo para "Otro — especificar". */
  propertyTypeDetail?: string;
  operationType: BackendOperationType;
  declaredLegalStatus: BackendPropertyLegalStatus;
  propertyAddress: string;
  /** CC#39: referencia que ve el cliente; undefined = la conserva, "" = la quita. */
  propertyReference?: string;
}

/** Al crear, el domicilio y la situación jurídica se omiten: el backend los toma de los documentos del inmueble. */
export interface CreateExpedienteRequest extends Omit<ExpedienteConfiguration, "declaredLegalStatus" | "propertyAddress" | "propertySubtypeId"> {
  propertySubtypeId: string;
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

/** Saca el expediente del listado de trabajo (no cambia su estatus); revoca la liga del cliente. */
export function archiveExpediente(id: string, reason?: string) {
  return apiClient.post<ExpedienteResponse>(`/internal/expedientes/${id}/archive`, { reason: reason?.trim() || null });
}

export function unarchiveExpediente(id: string) {
  return apiClient.post<ExpedienteResponse>(`/internal/expedientes/${id}/unarchive`);
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
  /** Cuántos archivados puede ver el usuario (no entran en counts). */
  archived: number;
  /** CC#32: cuántos activos tienen el cierre completado. */
  closed?: number;
}

/**
 * Control de Cambios #24/#26: filtros de cartera. advisorId "none" = sin asesor asignado;
 * subtypeId "none" = pendientes de clasificación (de la categoría elegida, si hay).
 */
export interface PortfolioFilters {
  categoryId?: string | null;
  subtypeId?: string | null;
  operationType?: BackendOperationType | null;
  advisorId?: string | null;
}

export const UNASSIGNED_ADVISOR = "none";
export const UNCLASSIFIED_SUBTYPE = "none";

function setPortfolioParams(search: URLSearchParams, filters: PortfolioFilters) {
  if (filters.categoryId) search.set("categoryId", filters.categoryId);
  if (filters.subtypeId === UNCLASSIFIED_SUBTYPE) search.set("unclassified", "true");
  else if (filters.subtypeId) search.set("subtypeId", filters.subtypeId);
  if (filters.operationType) search.set("operationType", filters.operationType);
  if (filters.advisorId === UNASSIGNED_ADVISOR) search.set("unassigned", "true");
  else if (filters.advisorId) search.set("advisorId", filters.advisorId);
}

export function getExpedienteOverview(
  params: { q?: string; group?: ExpedienteStatusGroup | null; archived?: boolean; closed?: boolean; page?: number; size?: number } & PortfolioFilters,
) {
  const search = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 18), sort: "updatedAt,desc" });
  if (params.q?.trim()) search.set("q", params.q.trim());
  if (params.group) search.set("group", params.group);
  if (params.archived) search.set("archived", "true");
  // CC#32: solo los que tienen el cierre completado.
  if (params.closed) search.set("closed", "true");
  setPortfolioParams(search, params);
  return apiClient.get<ExpedienteOverview>(`/internal/expedientes/overview?${search.toString()}`);
}

export interface PortfolioReport {
  /** Con todos los filtros. */
  total: GroupCounts;
  /**
   * Nivel 1: categoría → subcategoría + operación (respeta solo el filtro de asesor). subtypeId null =
   * pendientes de clasificación. Solo vienen las combinaciones con expedientes, en orden del catálogo.
   */
  byTypeOperation: {
    categoryId: string | null;
    categoryName: string;
    subtypeId: string | null;
    subtypeName: string;
    operationType: BackendOperationType;
    counts: GroupCounts;
  }[];
  /** Nivel 2: asesor asignado (respeta solo el tipo y la operación); advisorId null = sin asignar. */
  byAdvisor: { advisorId: string | null; name: string; counts: GroupCounts }[];
}

export function getPortfolioReport(filters: PortfolioFilters) {
  const search = new URLSearchParams();
  setPortfolioParams(search, filters);
  return apiClient.get<PortfolioReport>(`/internal/expedientes/overview/portfolio?${search.toString()}`);
}

/** Control de Cambios #26: clasificar el tipo de inmueble sin la corrección completa (no puede cambiar documentos ni contrato). */
export function classifyExpedienteProperty(id: string, propertySubtypeId: string, propertyTypeDetail?: string, reason?: string) {
  return apiClient.put<ExpedienteResponse>(`/internal/expedientes/${id}/property-type`, { propertySubtypeId, propertyTypeDetail, reason });
}

/** Control de Cambios #27 (adenda): validación en tiempo real de una clave capturada a mano. */
export interface PropertyKeyCheck {
  /** Normalizada (mayúsculas, consecutivo a dos dígitos). */
  key: string;
  valid: boolean;
  available: boolean;
  message: string;
  assignedTo: { expedienteId: string; folio: string; ownerDisplayName: string; property: string | null } | null;
}

export function checkPropertyKey(key: string, expedienteId?: string) {
  const search = new URLSearchParams({ key });
  if (expedienteId) search.set("expedienteId", expedienteId);
  return apiClient.get<PropertyKeyCheck>(`/internal/expedientes/property-keys/check?${search.toString()}`);
}

/** Asignación manual: se respeta la clave capturada (no genera consecutivo automático). */
export function assignPropertyKey(id: string, key: string, reason?: string) {
  return apiClient.put<ExpedienteResponse>(`/internal/expedientes/${id}/property-key`, { key, reason });
}

export interface PropertyKeyOverview {
  assigned: {
    expedienteId: string;
    folio: string;
    key: string;
    prefix: string;
    number: number;
    ownerDisplayName: string;
    property: string | null;
    propertyTypeLabel: string | null;
    operationType: BackendOperationType;
    advisorId: string | null;
    advisorName: string | null;
    archivedAt: string | null;
  }[];
  /** Por combinación (tipo + operación + asesor): consecutivos en uso, huecos libres y el siguiente automático. */
  combinations: { prefix: string; used: number[]; gaps: number[]; nextKey: string | null }[];
  /** Expedientes sin clave, con la que les tocaría (null si su tipo no tiene letras o falta el asesor). */
  withoutKey: {
    expedienteId: string;
    folio: string;
    ownerDisplayName: string;
    property: string | null;
    propertyTypeLabel: string | null;
    operationType: BackendOperationType;
    advisorName: string | null;
    suggestedPrefix: string | null;
    suggestedKey: string | null;
    archivedAt: string | null;
  }[];
}

export function getPropertyKeys(filters: Omit<PortfolioFilters, "advisorId"> & { advisorId?: string | null; prefix?: string }) {
  const search = new URLSearchParams();
  if (filters.categoryId) search.set("categoryId", filters.categoryId);
  if (filters.subtypeId === UNCLASSIFIED_SUBTYPE) search.set("unclassified", "true");
  else if (filters.subtypeId) search.set("subtypeId", filters.subtypeId);
  if (filters.operationType) search.set("operationType", filters.operationType);
  if (filters.advisorId) search.set("advisorId", filters.advisorId);
  if (filters.prefix?.trim()) search.set("prefix", filters.prefix.trim());
  return apiClient.get<PropertyKeyOverview>(`/internal/expedientes/property-keys?${search.toString()}`);
}
