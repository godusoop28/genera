import { apiClient } from "@/lib/api/client";
import {
  DEMO_MODE,
  demoCreateExpediente,
  demoGetExpediente,
  demoGetParticipants,
  demoGetRequirements,
  demoListExpedientes,
} from "@/lib/api/demo-data";
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
  if (DEMO_MODE) return demoListExpedientes(page, size);
  return apiClient.get<PageResponse<ExpedienteResponse>>(`/internal/expedientes?page=${page}&size=${size}`);
}

export function createExpediente(request: CreateExpedienteRequest) {
  if (DEMO_MODE) return demoCreateExpediente(request);
  return apiClient.post<ExpedienteResponse>("/internal/expedientes", request);
}

export function getExpediente(id: string) {
  if (DEMO_MODE) return demoGetExpediente(id);
  return apiClient.get<ExpedienteResponse>(`/internal/expedientes/${id}`);
}

export function getParticipants(id: string) {
  if (DEMO_MODE) return demoGetParticipants(id);
  return apiClient.get<ParticipantResponse[]>(`/internal/expedientes/${id}/participants`);
}

export function getRequirements(id: string) {
  if (DEMO_MODE) return demoGetRequirements(id);
  return apiClient.get<RequirementResponse[]>(`/internal/expedientes/${id}/requirements`);
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
