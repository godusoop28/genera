import { apiClient } from "@/lib/api/client";
import { DEMO_MODE, demoGetActivity } from "@/lib/api/demo-data";
import type { ActivityResponse } from "@/lib/api/types";

export function getActivity(expedienteId: string) {
  if (DEMO_MODE) return demoGetActivity(expedienteId);
  return apiClient.get<ActivityResponse[]>(`/internal/expedientes/${expedienteId}/activity`);
}
