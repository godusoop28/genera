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

export type FeasibilityOperation = "SALE" | "RENT";

export interface FeasibilityResponse {
  id: string;
  expedienteId: string;
  versionNumber: number;
  /** Venta o renta: en renta los montos son rentas mensuales. */
  operation: FeasibilityOperation;
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
  input: { operation: FeasibilityOperation; askingPrice?: string; notes?: string; photos: File[] },
  onProgress?: (progress: UploadProgress) => void,
) {
  const fields: Record<string, string> = { operation: input.operation };
  if (input.askingPrice) fields.askingPrice = input.askingPrice;
  if (input.notes?.trim()) fields.notes = input.notes.trim();
  return uploadFiles<FeasibilityResponse>(`/internal/expedientes/${expedienteId}/feasibility`, "photos", input.photos, onProgress, fields);
}

export function getFeasibilityDownload(reportId: string, file: "docx" | "pptx") {
  return apiClient.get<DownloadResponse>(`/internal/feasibility/${reportId}/download?file=${file}`);
}

// ------------------------------------------------------------------ prompts maestros (Configuración)

export type FeasibilityPromptKey = "ANALYST" | "PRESENTATION" | "CHAT";

export interface FeasibilityPrompt {
  key: FeasibilityPromptKey;
  title: string;
  content: string;
  /** 0 = texto original del cliente. */
  version: number;
  original: boolean;
  updatedAt: string | null;
  updatedByName: string | null;
  /** Reglas fijas que el sistema agrega al final (formato para armar el Word y el PowerPoint). */
  systemRules: string;
}

export interface FeasibilityPromptVersion {
  version: number;
  createdAt: string | null;
  createdByName: string | null;
  note: string | null;
  length: number;
  current: boolean;
}

const PROMPTS = "/internal/settings/feasibility-prompts";

export function getFeasibilityPrompts() {
  return apiClient.get<FeasibilityPrompt[]>(PROMPTS);
}

export function updateFeasibilityPrompt(key: FeasibilityPromptKey, content: string, note?: string) {
  return apiClient.put<FeasibilityPrompt>(`${PROMPTS}/${key.toLowerCase()}`, { content, note: note || null });
}

export function getFeasibilityPromptVersions(key: FeasibilityPromptKey) {
  return apiClient.get<FeasibilityPromptVersion[]>(`${PROMPTS}/${key.toLowerCase()}/versions`);
}

export function getFeasibilityPromptVersion(key: FeasibilityPromptKey, version: number) {
  return apiClient.get<{ version: number; content: string }>(`${PROMPTS}/${key.toLowerCase()}/versions/${version}`);
}

export function restoreFeasibilityPrompt(key: FeasibilityPromptKey, version: number) {
  return apiClient.post<FeasibilityPrompt>(`${PROMPTS}/${key.toLowerCase()}/restore`, { version });
}

// ------------------------------------------------------------------ chat de consultas

export interface FeasibilityChatMessage {
  id: string;
  role: "USER" | "ASSISTANT";
  /** PENDING = la IA está respondiendo. */
  status: "PENDING" | "DONE" | "FAILED";
  content: string | null;
  sources: { title: string; url: string }[];
  error: string | null;
  authorName: string | null;
  createdAt: string;
}

export function getFeasibilityChat(reportId: string) {
  return apiClient.get<FeasibilityChatMessage[]>(`/internal/feasibility/${reportId}/chat`);
}

export function askFeasibilityChat(reportId: string, message: string) {
  return apiClient.post<FeasibilityChatMessage[]>(`/internal/feasibility/${reportId}/chat`, { message });
}
