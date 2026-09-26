import { ApiError } from "@/lib/api/client";

/** Mensaje para el usuario: el que explica el backend, o uno genérico si no hubo respuesta. */
export function errorText(err: unknown, fallback = "No se pudo completar la acción. Revisa tu conexión e intenta de nuevo."): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return "No tienes permiso para esta acción.";
    return err.message || fallback;
  }
  return fallback;
}
