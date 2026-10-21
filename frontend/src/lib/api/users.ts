import { apiClient } from "@/lib/api/client";
import type { AuditEventResponse, BackendRoleCode, CreatedUserResponse, PageResponse, SetupLinkResponse, UserResponse, WorkloadItem } from "@/lib/api/types";

export function listUsers(page = 0, size = 50) {
  return apiClient.get<PageResponse<UserResponse>>(`/internal/users?page=${page}&size=${size}`);
}

/** Control de Cambios #25: sin contraseña; la respuesta trae el enlace para que la persona defina la suya. */
export function createUser(name: string, email: string, roleCode: BackendRoleCode) {
  return apiClient.post<CreatedUserResponse>("/internal/users", { name, email, roleCode });
}

export function updateUser(id: string, name: string, email: string, roleCode: BackendRoleCode) {
  return apiClient.patch<UserResponse>(`/internal/users/${id}`, { name, email, roleCode });
}

export function activateUser(id: string) {
  return apiClient.post<UserResponse>(`/internal/users/${id}/activate`);
}

export function deactivateUser(id: string) {
  return apiClient.post<UserResponse>(`/internal/users/${id}/deactivate`);
}

/** Baja definitiva (conserva el historial). */
export function offboardUser(id: string, reason: string) {
  return apiClient.post<UserResponse>(`/internal/users/${id}/offboard`, { reason });
}

/** Enlace de restablecimiento (o de alta si aún no define su contraseña). El administrador nunca ve la contraseña. */
export function issuePasswordLink(id: string) {
  return apiClient.post<SetupLinkResponse>(`/internal/users/${id}/password-link`);
}

export function unlockUser(id: string) {
  return apiClient.post<UserResponse>(`/internal/users/${id}/unlock`);
}

/** Lo que la persona tiene a su cargo: hay que reasignarlo antes de darle de baja. */
export function getUserWorkload(id: string) {
  return apiClient.get<WorkloadItem[]>(`/internal/users/${id}/workload`);
}

/** Bitácora de cuentas (más reciente primero). Requiere AUDIT_VIEW. */
export function listAccountEvents(page = 0, size = 50) {
  return apiClient.get<PageResponse<AuditEventResponse>>(`/internal/audit-events/accounts?page=${page}&size=${size}`);
}
