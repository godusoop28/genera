// Control de Cambios #42: apartado «Propiedad Resuelta». El backend valida el acceso en cada
// petición (también al abrir o descargar archivos): ocultar opciones aquí es solo comodidad.
import { apiClient, getBlob, uploadFiles } from "@/lib/api/client";

export interface ResolvedAccess {
  admin: boolean;
  view: boolean;
  download: boolean;
  upload: boolean;
}

export type ApprovalStatus = "PENDING" | "APPROVED";
export type CommercializationStatus = "NOT_INCORPORATED" | "INCORPORATED";

export interface ResolvedProject {
  id: string;
  name: string;
  description: string | null;
  location: string | null;
  estimatedInvestment: number | null;
  estimatedValue: number | null;
  approvalStatus: ApprovalStatus;
  approvedByName: string | null;
  approvedAt: string | null;
  approvalNote: string | null;
  commercializationStatus: CommercializationStatus;
  incorporatedByName: string | null;
  incorporatedAt: string | null;
  incorporationNote: string | null;
  linkedReference: string | null;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
  documentCount: number;
  access: ResolvedAccess;
}

export interface ResolvedMetrics {
  projects: number;
  pendingApproval: number;
  approved: number;
  incorporated: number;
  documents: number;
  estimatedInvestment: number;
  estimatedValue: number;
}

export interface ResolvedDocument {
  id: string;
  name: string;
  category: string | null;
  contentType: string;
  sizeBytes: number;
  originalFilename: string | null;
  uploadedByName: string | null;
  uploadedAt: string;
}

export interface ResolvedGrant {
  id: string;
  userId: string;
  userName: string | null;
  canDownload: boolean;
  canUpload: boolean;
  grantedByName: string | null;
  grantedAt: string;
  revokedByName: string | null;
  revokedAt: string | null;
}

export interface ResolvedActivity {
  action: string;
  detail: string | null;
  actorName: string | null;
  occurredAt: string;
}

export interface ResolvedProjectDetail {
  project: ResolvedProject;
  documents: ResolvedDocument[];
  /** Solo para el superusuario. */
  grants: ResolvedGrant[] | null;
  activity: ResolvedActivity[] | null;
}

export interface ProjectInput {
  name: string;
  description?: string | null;
  location?: string | null;
  estimatedInvestment?: number | null;
  estimatedValue?: number | null;
}

const base = "/internal/propiedad-resuelta";

export const getResolvedAccess = () =>
  apiClient.get<ResolvedAccess>(`${base}/access`);
export const listResolvedProjects = () =>
  apiClient.get<{
    admin: boolean;
    metrics: ResolvedMetrics;
    projects: ResolvedProject[];
  }>(`${base}/projects`);
export const getResolvedProject = (id: string) =>
  apiClient.get<ResolvedProjectDetail>(`${base}/projects/${id}`);
export const createResolvedProject = (input: ProjectInput) =>
  apiClient.post<ResolvedProjectDetail>(`${base}/projects`, input);
export const updateResolvedProject = (id: string, input: ProjectInput) =>
  apiClient.put<ResolvedProjectDetail>(`${base}/projects/${id}`, input);
export const approveResolvedProject = (id: string, note: string) =>
  apiClient.post<ResolvedProjectDetail>(`${base}/projects/${id}/approve`, {
    note,
  });
export const withdrawResolvedApproval = (id: string, note: string) =>
  apiClient.post<ResolvedProjectDetail>(
    `${base}/projects/${id}/withdraw-approval`,
    { note },
  );
export const incorporateResolvedProject = (
  id: string,
  note: string,
  linkedReference: string,
) =>
  apiClient.post<ResolvedProjectDetail>(`${base}/projects/${id}/incorporate`, {
    note,
    linkedReference,
  });
export const archiveResolvedProject = (id: string) =>
  apiClient.delete<void>(`${base}/projects/${id}`);
export const grantResolvedAccess = (
  id: string,
  userId: string,
  canDownload: boolean,
  canUpload: boolean,
) =>
  apiClient.post<ResolvedProjectDetail>(`${base}/projects/${id}/grants`, {
    userId,
    canDownload,
    canUpload,
  });
export const revokeResolvedAccess = (id: string, grantId: string) =>
  apiClient.delete<ResolvedProjectDetail>(
    `${base}/projects/${id}/grants/${grantId}`,
  );
export const listResolvedStaff = () =>
  apiClient.get<{ id: string; name: string; roleCode: string }[]>(
    `${base}/staff`,
  );
export const removeResolvedDocument = (documentId: string) =>
  apiClient.delete<void>(`${base}/documents/${documentId}`);

export function uploadResolvedDocument(
  projectId: string,
  file: File,
  name: string,
  category: string,
) {
  const params = new URLSearchParams();
  if (name.trim()) params.set("name", name.trim());
  if (category.trim()) params.set("category", category.trim());
  const query = params.toString();
  return uploadFiles<ResolvedDocument>(
    `${base}/projects/${projectId}/documents${query ? `?${query}` : ""}`,
    "file",
    [file],
  );
}

/** Abre o descarga el archivo con la sesión (el backend vuelve a validar el acceso). */
export function fetchResolvedFile(documentId: string, download: boolean) {
  return getBlob(`${base}/documents/${documentId}/file?download=${download}`);
}

export const approvalLabels: Record<ApprovalStatus, string> = {
  PENDING: "Pendiente de aprobación",
  APPROVED: "Aprobado para comercialización",
};

export const commercializationLabels: Record<CommercializationStatus, string> =
  {
    NOT_INCORPORATED: "Sin incorporar a C21 Genera",
    INCORPORATED: "Incorporado a comercialización C21 Genera",
  };
