import { apiClient } from "@/lib/api/client";
import { DEMO_MODE, demoGeneratePublicLink } from "@/lib/api/demo-data";

export interface PublicLinkResponse {
  url: string;
  expiresAt: string;
}

export function generatePublicLink(expedienteId: string) {
  if (DEMO_MODE) return demoGeneratePublicLink(expedienteId);
  return apiClient.post<PublicLinkResponse>(`/internal/expedientes/${expedienteId}/public-link`);
}

export function regeneratePublicLink(expedienteId: string) {
  return apiClient.post<PublicLinkResponse>(`/internal/expedientes/${expedienteId}/public-link/regenerate`);
}

export function revokePublicLink(expedienteId: string) {
  return apiClient.delete<void>(`/internal/expedientes/${expedienteId}/public-link`);
}
