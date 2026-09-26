import { apiClient, uploadFiles } from "@/lib/api/client";
import type {
  DocumentResponse,
  DocumentVersionResponse,
  DownloadResponse,
  ReturnReasonCode,
  ReviewHistoryResponse,
} from "@/lib/api/types";

export function listDocuments(expedienteId: string) {
  return apiClient.get<DocumentResponse[]>(`/internal/expedientes/${expedienteId}/documents`);
}

export function listVersions(documentId: string) {
  return apiClient.get<DocumentVersionResponse[]>(`/internal/documents/${documentId}/versions`);
}

export function listReviews(documentId: string) {
  return apiClient.get<ReviewHistoryResponse[]>(`/internal/documents/${documentId}/reviews`);
}

export function uploadVersion(documentId: string, files: File[]) {
  return uploadFiles<DocumentVersionResponse>(`/internal/documents/${documentId}/versions`, "files", files);
}

/** overrideJustification solo aplica para aceptar por excepción un archivo con alertas. */
export function acceptDocument(documentId: string, overrideJustification?: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/accept`, { overrideJustification });
}

export function returnDocument(documentId: string, reasonCode: ReturnReasonCode, comment: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/return`, { reasonCode, comment });
}

export function rejectDocument(documentId: string, reasonCode: ReturnReasonCode, comment: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/reject`, { reasonCode, comment });
}

export function markNotApplicable(documentId: string, justification: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/not-applicable`, { justification });
}

export function requestAgain(documentId: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/request-again`);
}

export function getProcessingStatus(versionId: string) {
  return apiClient.get<DocumentVersionResponse>(`/internal/document-versions/${versionId}/processing`);
}

export function getDownloadUrl(versionId: string) {
  return apiClient.get<DownloadResponse>(`/internal/document-versions/${versionId}/download`);
}
