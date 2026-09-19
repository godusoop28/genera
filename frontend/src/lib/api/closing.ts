import { apiClient } from "@/lib/api/client";
import { DEMO_MODE, demoAddClosingNote, demoGetClosingCase, demoGetClosingNotes } from "@/lib/api/demo-data";
import type { ClosingCaseResponse, ClosingNoteResponse } from "@/lib/api/types";

export function getClosingCase(expedienteId: string) {
  if (DEMO_MODE) return demoGetClosingCase(expedienteId);
  return apiClient.get<ClosingCaseResponse>(`/internal/expedientes/${expedienteId}/closing`);
}

export function getClosingNotes(expedienteId: string) {
  if (DEMO_MODE) return demoGetClosingNotes(expedienteId);
  return apiClient.get<ClosingNoteResponse[]>(`/internal/expedientes/${expedienteId}/closing/notes`);
}

export function addClosingNote(expedienteId: string, note: string) {
  if (DEMO_MODE) return demoAddClosingNote(expedienteId, note);
  return apiClient.post<ClosingNoteResponse>(`/internal/expedientes/${expedienteId}/closing/notes`, { note });
}
