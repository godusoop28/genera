import { apiClient } from "@/lib/api/client";
import type { ActivityResponse } from "@/lib/api/types";

export function getActivity(expedienteId: string) {
  return apiClient.get<ActivityResponse[]>(`/internal/expedientes/${expedienteId}/activity`);
}
