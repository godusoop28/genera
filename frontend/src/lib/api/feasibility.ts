import { apiClient, uploadFiles, type UploadProgress } from "@/lib/api/client";
import type { DownloadResponse } from "@/lib/api/types";

export type FeasibilityStatus = "ANALYZING" | "PRESENTING" | "READY" | "FAILED";
export type ComparableGroup = "ARRIBA" | "SIMILAR" | "DEBAJO";

export interface FeasibilityComparable {
  id: string;
  group: ComparableGroup;
  zone: string | null;
  price: number | null;
  url: string | null;
  urlVerified: boolean;
  locationPrecision: string | null;
}

export interface FeasibilitySummary {
  executiveSummary: string | null;
  ownerPrice: number | null;
  rangeMin: number | null;
  rangeMax: number | null;
  recommendedPrice: number | null;
  /** ALINEADO, LIMITE_SUPERIOR, FUERA_DE_RANGO o SIN_DATOS. */
  classification: string | null;
  strategy: string | null;
  comparables: FeasibilityComparable[];
  pendingItems: string[];
}

export interface FeasibilityResponse {
  id: string;
  expedienteId: string;
  versionNumber: number;
  status: FeasibilityStatus;
  requestedAt: string;
  requestedByName: string | null;
  stageStartedAt: string;
  completedAt: string | null;
  error: string | null;
  hasDocx: boolean;
  hasPptx: boolean;
  summary: FeasibilitySummary | null;
}

export interface FeasibilityListResponse {
  /** false = la IA no está configurada en el servidor. */
  available: boolean;
  reports: FeasibilityResponse[];
}

export function listFeasibility(expedienteId: string) {
  return apiClient.get<FeasibilityListResponse>(`/internal/expedientes/${expedienteId}/feasibility`);
}

export function requestFeasibility(
  expedienteId: string,
  input: { askingPrice?: string; notes?: string; photos: File[] },
  onProgress?: (progress: UploadProgress) => void,
) {
  const fields: Record<string, string> = {};
  if (input.askingPrice) fields.askingPrice = input.askingPrice;
  if (input.notes?.trim()) fields.notes = input.notes.trim();
  return uploadFiles<FeasibilityResponse>(`/internal/expedientes/${expedienteId}/feasibility`, "photos", input.photos, onProgress, fields);
}

export function getFeasibilityDownload(reportId: string, file: "docx" | "pptx") {
  return apiClient.get<DownloadResponse>(`/internal/feasibility/${reportId}/download?file=${file}`);
}
