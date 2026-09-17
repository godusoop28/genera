import { apiClient } from "@/lib/api/client";
import type { AuthResponse, MeResponse } from "@/lib/api/types";

export function login(email: string, password: string) {
  return apiClient.post<AuthResponse>("/auth/login", { email, password });
}

export function refresh(refreshToken: string) {
  return apiClient.post<AuthResponse>("/auth/refresh", { refreshToken });
}

export function logout(refreshToken: string) {
  return apiClient.post<void>("/auth/logout", { refreshToken });
}

export function me(accessToken?: string) {
  return apiClient.get<MeResponse>("/internal/me", accessToken ? { accessToken } : undefined);
}
