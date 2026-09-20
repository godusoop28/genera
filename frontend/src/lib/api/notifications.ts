import { apiClient } from "@/lib/api/client";
import type { EmailPreviewResponse } from "@/lib/api/types";

// El envío real a notaría es manual (ver AGENTS del backend): esto solo arma
// el contenido del correo, nunca lo envía.
export function getDocumentsEmailPreview(expedienteId: string) {
  return apiClient.get<EmailPreviewResponse>(`/internal/expedientes/${expedienteId}/notifications/documents-email-preview`);
}
