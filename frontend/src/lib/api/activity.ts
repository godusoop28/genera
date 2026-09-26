import { apiClient } from "@/lib/api/client";
import type { ActivityResponse, NotificationResponse } from "@/lib/api/types";

export function getActivity(expedienteId: string) {
  return apiClient.get<ActivityResponse[]>(`/internal/expedientes/${expedienteId}/activity`);
}

export function getNotifications(expedienteId: string) {
  return apiClient.get<NotificationResponse[]>(`/internal/expedientes/${expedienteId}/notifications`);
}
