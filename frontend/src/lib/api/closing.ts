import { apiClient } from "@/lib/api/client";
import type { ClosingCaseResponse, ClosingNoteResponse, ClosingTaskResponse } from "@/lib/api/types";

export function getClosingCase(expedienteId: string) {
  return apiClient.get<ClosingCaseResponse>(`/internal/expedientes/${expedienteId}/closing`);
}

export function getClosingTasks(expedienteId: string) {
  return apiClient.get<ClosingTaskResponse[]>(`/internal/expedientes/${expedienteId}/closing/tasks`);
}

export function completeClosingTask(expedienteId: string, taskId: string, note?: string) {
  return apiClient.post<ClosingTaskResponse>(`/internal/expedientes/${expedienteId}/closing/tasks/${taskId}/complete`, { note });
}

export function getClosingNotes(expedienteId: string) {
  return apiClient.get<ClosingNoteResponse[]>(`/internal/expedientes/${expedienteId}/closing/notes`);
}

export function addClosingNote(expedienteId: string, note: string) {
  return apiClient.post<ClosingNoteResponse>(`/internal/expedientes/${expedienteId}/closing/notes`, { note });
}
