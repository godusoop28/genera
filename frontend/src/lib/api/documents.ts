import { apiClient, uploadFiles, type UploadProgress } from "@/lib/api/client";
import { prepareUploads } from "@/lib/upload-limits";
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

export async function uploadVersion(documentId: string, files: File[], onProgress?: (progress: UploadProgress) => void) {
  return uploadFiles<DocumentVersionResponse>(`/internal/documents/${documentId}/versions`, "files", await prepareUploads(files), onProgress);
}

/** "Reprocesar con IA" sin que el cliente vuelva a subir el archivo. full: también calidad y PDF. */
export function reprocessDocument(documentId: string, full = false) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/reprocess`, { full });
}

export function reprocessAllDocuments(expedienteId: string) {
  return apiClient.post<{ reprocessed: number }>(`/internal/expedientes/${expedienteId}/documents/reprocess`);
}

/** "Cambiar tipo de documento": mueve el archivo vigente a otro requisito del expediente. */
export function moveDocumentFile(documentId: string, targetDocumentId: string) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/move`, { targetDocumentId });
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

/** deferred=true: el cliente puede subirlo después; false: se pide en la primera entrega. */
export function setDocumentDeferred(documentId: string, deferred: boolean) {
  return apiClient.put<DocumentResponse>(`/internal/documents/${documentId}/deferral`, { deferred });
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
