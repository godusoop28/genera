import { apiClient, uploadFiles } from "@/lib/api/client";
import type {
  ContractCalculationsResponse,
  ContractGenerationResponse,
  ContractReadinessResponse,
  DownloadResponse,
  GenerateContractResponse,
  SigningLinkResponse,
} from "@/lib/api/types";

export function getContractData(expedienteId: string) {
  return apiClient.get<ContractCalculationsResponse>(`/internal/expedientes/${expedienteId}/contract/data`);
}

export function getContractReadiness(expedienteId: string) {
  return apiClient.get<ContractReadinessResponse>(`/internal/expedientes/${expedienteId}/contract/readiness`);
}

/** mode "final" exige que no falte nada; "draft" genera un borrador INCOMPLETO que no se puede firmar. */
export function generateContract(expedienteId: string, mode: "final" | "draft") {
  return apiClient.post<GenerateContractResponse>(`/internal/expedientes/${expedienteId}/contracts/generate?mode=${mode}`);
}

export function listContracts(expedienteId: string) {
  return apiClient.get<ContractGenerationResponse[]>(`/internal/expedientes/${expedienteId}/contracts`);
}

export function getContractDownload(contractId: string, file: "pdf" | "docx" | "signed") {
  return apiClient.get<DownloadResponse>(`/internal/contracts/${contractId}/download?file=${file}`);
}

export interface SignRequest {
  typedName: string;
  signatureImageBase64: string;
  documentSha256: string;
  accepted: boolean;
}

export function signAsIntermediary(contractId: string, request: SignRequest) {
  return apiClient.post<ContractGenerationResponse>(`/internal/contracts/${contractId}/signatures/intermediary`, request);
}

export function registerAutographSignature(contractId: string, file: File) {
  return uploadFiles<ContractGenerationResponse>(`/internal/contracts/${contractId}/signatures/autograph`, "file", [file]);
}

export function reissueSigningLink(signatureId: string) {
  return apiClient.post<SigningLinkResponse>(`/internal/contract-signatures/${signatureId}/reissue-link`);
}

export function markContractDelivered(contractId: string, method: string) {
  return apiClient.post<ContractGenerationResponse>(`/internal/contracts/${contractId}/mark-delivered`, { method });
}
