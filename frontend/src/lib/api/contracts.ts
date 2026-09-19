import { apiClient } from "@/lib/api/client";
import type { ContractCalculationsResponse, ContractGenerationResponse } from "@/lib/api/types";

export function getContractData(expedienteId: string) {
  return apiClient.get<ContractCalculationsResponse>(`/internal/expedientes/${expedienteId}/contract/data`);
}

export function generateContract(expedienteId: string) {
  return apiClient.post<ContractGenerationResponse>(`/internal/expedientes/${expedienteId}/contracts/generate`);
}

export function listContracts(expedienteId: string) {
  return apiClient.get<ContractGenerationResponse[]>(`/internal/expedientes/${expedienteId}/contracts`);
}

export function markContractSigned(contractId: string) {
  return apiClient.post<ContractGenerationResponse>(`/internal/contracts/${contractId}/mark-signed`);
}

export function markContractDelivered(contractId: string, method: string) {
  return apiClient.post<ContractGenerationResponse>(`/internal/contracts/${contractId}/mark-delivered`, { method });
}
