import { apiClient } from "@/lib/api/client";
import type { BackendRoleCode, PageResponse, UserResponse } from "@/lib/api/types";

export function listUsers(page = 0, size = 50) {
  return apiClient.get<PageResponse<UserResponse>>(`/internal/users?page=${page}&size=${size}`);
}

export function createUser(name: string, email: string, password: string, roleCode: BackendRoleCode) {
  return apiClient.post<UserResponse>("/internal/users", { name, email, password, roleCode });
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
