import { apiClient } from "@/lib/api/client";
import { DEMO_MODE, demoGetComplianceChecklist } from "@/lib/api/demo-data";
import type { ComplianceChecklistResponse } from "@/lib/api/types";

export function getComplianceChecklist(expedienteId: string) {
  if (DEMO_MODE) return demoGetComplianceChecklist(expedienteId);
  return apiClient.get<ComplianceChecklistResponse>(`/internal/expedientes/${expedienteId}/compliance-checklist`);
}
