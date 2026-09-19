import { apiClient } from "@/lib/api/client";
import type { DataConflictResponse, ExtractedFieldObservationResponse } from "@/lib/api/types";

export function getExtractedFields(documentId: string) {
  return apiClient.get<ExtractedFieldObservationResponse[]>(`/internal/documents/${documentId}/extracted-fields`);
}

export function confirmField(observationId: string, confirmedValue: string) {
  return apiClient.post<ExtractedFieldObservationResponse>(`/internal/extracted-fields/${observationId}/confirm`, {
    confirmedValue,
  });
}

export function getDataConflicts(expedienteId: string) {
  return apiClient.get<DataConflictResponse[]>(`/internal/expedientes/${expedienteId}/data-conflicts`);
}
