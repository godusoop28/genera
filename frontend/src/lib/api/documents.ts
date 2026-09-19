import { apiClient, uploadFiles } from "@/lib/api/client";
import type { DocumentResponse, DocumentVersionResponse, DownloadResponse, ReturnReasonCode } from "@/lib/api/types";

export function listDocuments(expedienteId: string) {
  return apiClient.get<DocumentResponse[]>(`/internal/expedientes/${expedienteId}/documents`);
}

export function listVersions(documentId: string) {
  return apiClient.get<DocumentVersionResponse[]>(`/internal/documents/${documentId}/versions`);
}

export function uploadVersion(documentId: string, files: File[]) {
  return uploadFiles<DocumentVersionResponse>(`/internal/documents/${documentId}/versions`, "files", files);
}

export function acceptDocument(documentId: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/accept`);
}

export function returnDocument(documentId: string, reasonCode: ReturnReasonCode, comment: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/return`, { reasonCode, comment });
}

export function rejectDocument(documentId: string, reasonCode: ReturnReasonCode, comment: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/reject`, { reasonCode, comment });
}

export function getProcessingStatus(versionId: string) {
  return apiClient.get<DocumentVersionResponse>(`/internal/document-versions/${versionId}/processing`);
}

export function getDownloadUrl(versionId: string) {
  return apiClient.get<DownloadResponse>(`/internal/document-versions/${versionId}/download`);
}

export function signReception(expedienteId: string) {
  return apiClient.post<void>(`/internal/expedientes/${expedienteId}/reception/sign`);
}
