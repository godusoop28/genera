import { apiClient } from "@/lib/api/client";
import type { ClosingCaseResponse, ClosingNoteResponse } from "@/lib/api/types";

export function getClosingCase(expedienteId: string) {
  return apiClient.get<ClosingCaseResponse>(`/internal/expedientes/${expedienteId}/closing`);
}

export function getClosingNotes(expedienteId: string) {
  return apiClient.get<ClosingNoteResponse[]>(`/internal/expedientes/${expedienteId}/closing/notes`);
}

export function addClosingNote(expedienteId: string, note: string) {
  return apiClient.post<ClosingNoteResponse>(`/internal/expedientes/${expedienteId}/closing/notes`, { note });
}
