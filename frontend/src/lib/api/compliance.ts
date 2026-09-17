import { apiClient } from "@/lib/api/client";
import type { ComplianceChecklistResponse } from "@/lib/api/types";

export function getComplianceChecklist(expedienteId: string) {
  return apiClient.get<ComplianceChecklistResponse>(`/internal/expedientes/${expedienteId}/compliance-checklist`);
}
