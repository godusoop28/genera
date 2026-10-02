/**
 * El backend arma la liga con su propia configuración (PUBLIC_LINK_BASE_URL),
 * que puede apuntar a otro dominio o ruta. El token es siempre el último
 * segmento, así que la reconstruimos con el dominio desde el que se usa el
 * sistema y la ruta real del frontend.
 */
export function frontendLink(backendUrl: string, route: "carga" | "firma"): string {
  const token = backendUrl.split(/[?#]/)[0].replace(/\/+$/, "").split("/").pop() ?? "";
  if (!token || typeof window === "undefined") return backendUrl;
  return `${window.location.origin}/${route}/${token}`;
}
