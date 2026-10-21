import { apiClient } from "@/lib/api/client";
import type { BackendPropertyCaseType } from "@/lib/api/types";

/** Control de Cambios #26: catálogo de inmuebles (categorías con todas sus subcategorías). */
export interface CatalogSubtype {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  sortOrder: number;
  active: boolean;
  /** "Otro — especificar": pide describir el inmueble. */
  requiresDetail: boolean;
  /** Tratamiento de documentos y contrato. */
  caseType: BackendPropertyCaseType;
  online21Type: "habitacional" | "comercial";
  online21Subtype: string;
}

export interface CatalogCategory {
  id: string;
  name: string;
  sortOrder: number;
  active: boolean;
  subtypes: CatalogSubtype[];
}

export function listPropertyCatalog(includeInactive = false) {
  return apiClient.get<CatalogCategory[]>(`/internal/property-catalog${includeInactive ? "?includeInactive=true" : ""}`);
}

export function createCatalogCategory(body: { name: string; sortOrder: number; active: boolean }) {
  return apiClient.post<CatalogCategory>("/internal/property-catalog/categories", body);
}

export function updateCatalogCategory(id: string, body: { name: string; sortOrder: number; active: boolean }) {
  return apiClient.put<CatalogCategory>(`/internal/property-catalog/categories/${id}`, body);
}

export type CatalogSubtypeInput = Omit<CatalogSubtype, "id" | "categoryName">;

export function createCatalogSubtype(body: CatalogSubtypeInput) {
  return apiClient.post<CatalogSubtype>("/internal/property-catalog/subtypes", body);
}

export function updateCatalogSubtype(id: string, body: CatalogSubtypeInput) {
  return apiClient.put<CatalogSubtype>(`/internal/property-catalog/subtypes/${id}`, body);
}

export const caseTypeLabels: Record<BackendPropertyCaseType, string> = {
  HOUSING: "Casa habitación",
  DEPARTMENT: "Departamento habitacional",
  RESIDENTIAL_LAND: "Terreno habitacional (sin servicios)",
  COMMERCIAL: "Otro uso (contrato distinto al de PROFECO)",
};

/** "Habitacional → Casa en condominio" o "Especiales → Otro: Capilla". */
export function subtypeLabel(subtype: Pick<CatalogSubtype, "categoryName" | "name" | "requiresDetail">, detail?: string | null): string {
  if (subtype.requiresDetail && detail?.trim()) {
    return `${subtype.categoryName} → ${subtype.name.replace(/\s*—.*$/, "")}: ${detail.trim()}`;
  }
  return `${subtype.categoryName} → ${subtype.name}`;
}

export function findSubtype(catalog: CatalogCategory[] | null, id: string | null | undefined): CatalogSubtype | null {
  if (!catalog || !id) return null;
  for (const category of catalog) {
    const found = category.subtypes.find((s) => s.id === id);
    if (found) return found;
  }
  return null;
}
