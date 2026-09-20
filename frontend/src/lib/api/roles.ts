import { apiClient } from "@/lib/api/client";
import type { PermissionResponse, RoleResponse } from "@/lib/api/types";

export function listRoles() {
  return apiClient.get<RoleResponse[]>("/internal/roles");
}

export function listPermissions() {
  return apiClient.get<PermissionResponse[]>("/internal/permissions");
}
