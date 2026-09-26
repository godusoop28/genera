import { apiClient } from "@/lib/api/client";
import type { DataConflictResponse, ExpedienteObservationResponse, ExtractedFieldObservationResponse } from "@/lib/api/types";

export function getExtractedFields(documentId: string) {
  return apiClient.get<ExtractedFieldObservationResponse[]>(`/internal/documents/${documentId}/extracted-fields`);
}

/** Lo detectado en todos los documentos vigentes del expediente, para prellenar el contrato. */
export function getExpedienteExtractedFields(expedienteId: string) {
  return apiClient.get<ExpedienteObservationResponse[]>(`/internal/expedientes/${expedienteId}/extracted-fields`);
}

export function confirmField(observationId: string, confirmedValue: string) {
  return apiClient.post<ExtractedFieldObservationResponse>(`/internal/extracted-fields/${observationId}/confirm`, {
    confirmedValue,
  });
}

export function getDataConflicts(expedienteId: string) {
  return apiClient.get<DataConflictResponse[]>(`/internal/expedientes/${expedienteId}/data-conflicts`);
}

export function runConsistencyCheck(expedienteId: string) {
  return apiClient.post<DataConflictResponse[]>(`/internal/expedientes/${expedienteId}/consistency-check`);
}

export function resolveConflict(conflictId: string, note: string) {
  return apiClient.post<DataConflictResponse>(`/internal/data-conflicts/${conflictId}/resolve`, { note });
}
