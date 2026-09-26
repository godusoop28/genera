import { apiClient } from "@/lib/api/client";
import type { PublicLinkStatusResponse } from "@/lib/api/types";

export interface PublicLinkResponse {
  url: string;
  expiresAt: string;
}

/** Siempre genera una liga nueva: la anterior deja de funcionar de inmediato. */
export function generatePublicLink(expedienteId: string) {
  return apiClient.post<PublicLinkResponse>(`/internal/expedientes/${expedienteId}/public-link`);
}

/** null si nunca se ha generado una liga (el backend responde 204). */
export function getPublicLinkStatus(expedienteId: string) {
  return apiClient.get<PublicLinkStatusResponse | undefined>(`/internal/expedientes/${expedienteId}/public-link`);
}

export function revokePublicLink(expedienteId: string) {
  return apiClient.delete<void>(`/internal/expedientes/${expedienteId}/public-link`);
}
