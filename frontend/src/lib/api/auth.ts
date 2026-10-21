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

/**
 * Control de Cambios #25: "Mi perfil" → cambiar contraseña (pide la actual). Cierra las demás
 * sesiones y devuelve una nueva para este dispositivo.
 */
export function changeOwnPassword(currentPassword: string, newPassword: string) {
  return apiClient.post<AuthResponse>("/internal/me/password", { currentPassword, newPassword });
}

export interface AccountLinkInfo {
  name: string;
  email: string;
  purpose: "INVITE" | "RESET";
  expiresAt: string;
}

/** Enlace de un solo uso (alta o restablecimiento): ¿sigue vigente y para quién es? */
export function getAccountLink(token: string) {
  return apiClient.get<AccountLinkInfo>(`/auth/account-link/${encodeURIComponent(token)}`);
}

export function completeAccountLink(token: string, password: string) {
  return apiClient.post<void>("/auth/account-link", { token, password });
}

/** Mismo mínimo que el backend (PasswordPolicy); no se exigen símbolos ni mayúsculas. */
export const PASSWORD_MIN_LENGTH = 10;
