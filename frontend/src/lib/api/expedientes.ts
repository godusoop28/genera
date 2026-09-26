import { apiClient } from "@/lib/api/client";
import type {
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
  declaredLegalStatus: BackendPropertyLegalStatus;
  propertyAddress: string;
}

export interface CreateExpedienteRequest extends ExpedienteConfiguration {
  participants: ParticipantRequest[];
  legalDetails?: LegalDetails;
}

export function listExpedientes(page = 0, size = 20) {
  return apiClient.get<PageResponse<ExpedienteResponse>>(`/internal/expedientes?page=${page}&size=${size}`);
}

export function createExpediente(request: CreateExpedienteRequest) {
  return apiClient.post<ExpedienteResponse>("/internal/expedientes", request);
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
