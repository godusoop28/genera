import { apiClient, getBlob, uploadFiles, type UploadProgress } from "@/lib/api/client";
import { prepareUploads } from "@/lib/upload-limits";
import type {
  AddressProofKind,
  DocumentResponse,
  DocumentVersionResponse,
  DownloadResponse,
  ReturnReasonCode,
  ReviewHistoryResponse,
  ViewerResponse,
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

export async function uploadVersion(
  documentId: string,
  files: File[],
  onProgress?: (progress: UploadProgress) => void,
  addressProofKind?: AddressProofKind | null,
) {
  const query = addressProofKind ? `?addressProofKind=${addressProofKind}` : "";
  return uploadFiles<DocumentVersionResponse>(
    `/internal/documents/${documentId}/versions${query}`,
    "files",
    await prepareUploads(files),
    onProgress,
  );
}

/** Control de Cambios #14: "Marcar como pendiente" (nota y fecha estimada opcionales). */
export function markDocumentPending(documentId: string, note: string, dueDate: string | null) {
  return apiClient.put<DocumentResponse>(`/internal/documents/${documentId}/pending`, { note, dueDate: dueDate || null });
}

export function clearDocumentPending(documentId: string) {
  return apiClient.delete<DocumentResponse>(`/internal/documents/${documentId}/pending`);
}

/** Control de Cambios #18: corrige el nombre de un documento agregado a mano (el archivo no cambia). */
export function renameDocument(documentId: string, name: string) {
  return apiClient.put<DocumentResponse>(`/internal/documents/${documentId}/name`, { name });
}

/** Control de Cambios #13: qué comprobante de domicilio se entregó. */
export function setAddressProofKind(documentId: string, kind: AddressProofKind | null) {
  return apiClient.put<DocumentResponse>(`/internal/documents/${documentId}/address-proof-kind`, { kind });
}

/** "Reprocesar con IA" sin que el cliente vuelva a subir el archivo. full: también calidad y PDF. */
export function reprocessDocument(documentId: string, full = false) {
  return apiClient.post<DocumentResponse>(`/internal/documents/${documentId}/reprocess`, { full });
}

export function reprocessAllDocuments(expedienteId: string) {
  return apiClient.post<{ reprocessed: number }>(`/internal/expedientes/${expedienteId}/documents/reprocess`);
}

/** "Cambiar tipo de documento": mueve el archivo vigente a otro requisito del expediente. */
/** "Eliminar archivo": vuelve a pendiente (aunque estuviera aceptado) para subirlo de nuevo; el historial se conserva. */
export function removeDocumentFile(documentId: string) {
  return apiClient.delete<DocumentResponse>(`/internal/documents/${documentId}/file`);
}

/** Quita un documento agregado a mano. */
export function deleteCustomDocument(documentId: string) {
  return apiClient.delete<void>(`/internal/documents/${documentId}`);
}

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

/** deferred=true: el cliente puede subirlo después (con la razón de la excepción); false: se pide en la primera entrega. */
export function setDocumentDeferred(documentId: string, deferred: boolean, reason?: string) {
  return apiClient.put<DocumentResponse>(`/internal/documents/${documentId}/deferral`, { deferred, reason });
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

/** Documento que no está en el listado, con el nombre que se le dé (Control de Cambios #1). */
export function addCustomDocument(expedienteId: string, name: string, participantId: string | null) {
  return apiClient.post<DocumentResponse>(`/internal/expedientes/${expedienteId}/documents`, { name, participantId });
}

/** Falta, pero se aprueba seguir sin él por ahora (Control de Cambios #5). */
export function approveException(documentId: string, note: string) {
  return apiClient.put<DocumentResponse>(`/internal/documents/${documentId}/exception`, { note });
}

export function revokeException(documentId: string) {
  return apiClient.delete<DocumentResponse>(`/internal/documents/${documentId}/exception`);
}

/** Pide al cliente una versión actualizada; la vigente se conserva (Control de Cambios #10). */
export function requestDocumentUpdate(documentId: string, note: string) {
  return apiClient.put<DocumentResponse>(`/internal/documents/${documentId}/update-request`, { note });
}

export function cancelDocumentUpdate(documentId: string) {
  return apiClient.delete<DocumentResponse>(`/internal/documents/${documentId}/update-request`);
}

/** Visor sin descarga (Control de Cambios #6): cuántas páginas tiene y si se puede descargar. */
export function getViewer(versionId: string) {
  return apiClient.get<ViewerResponse>(`/internal/document-versions/${versionId}/view`);
}

/** Imagen de la página (1 = la primera) con la marca de agua de quien consulta. */
export function getViewerPage(versionId: string, pageNumber: number) {
  return getBlob(`/internal/document-versions/${versionId}/view/pages/${pageNumber}`);
}
