"use client";

import { useAuth } from "@/context/AuthProvider";

/**
 * Permisos del usuario actual (vienen en su sesión). La UI solo oculta lo
 * que el usuario no puede hacer; el backend vuelve a validar cada acción.
 */
export function useCan() {
  const { user } = useAuth();
  const permissions = user?.permissions ?? [];
  return (permission: string) => permissions.includes("*") || permissions.includes(permission);
}
