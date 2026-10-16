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

/** Control de Cambios #20: contrato firmado fuera del sistema (DocuSign o escaneo de la firma física). */
export type SignatureMethod = "DOCUSIGN" | "PHYSICAL";

export interface SignedContractUploadResponse {
  id: string;
  documentId: string;
  signatureMethod: SignatureMethod;
  historical: boolean;
  note: string | null;
  uploadedByName: string | null;
  uploadedAt: string;
}

export function listSignedContracts(expedienteId: string) {
  return apiClient.get<SignedContractUploadResponse[]>(`/internal/expedientes/${expedienteId}/signed-contracts`);
}

export function uploadSignedContract(expedienteId: string, files: File[], signatureMethod: SignatureMethod, historical: boolean, note: string) {
  const query = new URLSearchParams({ signatureMethod, historical: String(historical) });
  if (note.trim()) query.set("note", note.trim());
  return uploadFiles<SignedContractUploadResponse>(`/internal/expedientes/${expedienteId}/signed-contracts?${query.toString()}`, "files", files);
}

/** Control de Cambios #21: texto estándar de "Instrucciones para visitas" (lo edita el administrador). */
export interface VisitInstructionsResponse {
  text: string;
  updatedAt: string | null;
  updatedByName: string | null;
}

export function getVisitInstructions() {
  return apiClient.get<VisitInstructionsResponse>(`/internal/settings/visit-instructions`);
}

export function updateVisitInstructions(text: string) {
  return apiClient.put<VisitInstructionsResponse>(`/internal/settings/visit-instructions`, { text });
}
