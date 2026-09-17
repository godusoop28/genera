import { apiClient } from "@/lib/api/client";
import type {
  BackendAccreditationType,
  BackendParticipantRole,
  BackendPersonType,
  BackendPropertyCaseType,
  BackendPropertyLegalStatus,
  BackendSignerCharacter,
  ExpedienteResponse,
  PageResponse,
  ParticipantResponse,
  RequirementResponse,
} from "@/lib/api/types";

export interface CreateExpedienteParticipant {
  role: BackendParticipantRole;
  fullName: string;
}

export interface CreateExpedienteRequest {
  ownerDisplayName: string;
  personType: BackendPersonType;
  signerCharacter: BackendSignerCharacter;
  accreditationType: BackendAccreditationType;
  condominiumRegime: boolean;
  propertyCaseType: BackendPropertyCaseType;
  declaredLegalStatus: BackendPropertyLegalStatus;
  propertyAddress?: string;
  participants: CreateExpedienteParticipant[];
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

export function getParticipants(id: string) {
  return apiClient.get<ParticipantResponse[]>(`/internal/expedientes/${id}/participants`);
}

export function getRequirements(id: string) {
  return apiClient.get<RequirementResponse[]>(`/internal/expedientes/${id}/requirements`);
}
