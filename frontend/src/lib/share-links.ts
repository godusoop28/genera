/**
 * El backend arma la liga con su propia configuración (PUBLIC_LINK_BASE_URL),
 * que puede apuntar a otro dominio o ruta. El token es siempre el último
 * segmento, así que la reconstruimos con el dominio desde el que se usa el
 * sistema y la ruta real del frontend.
 */
export function frontendLink(backendUrl: string, route: "carga" | "firma" | "aviso"): string {
  const token = backendUrl.split(/[?#]/)[0].replace(/\/+$/, "").split("/").pop() ?? "";
  if (!token || typeof window === "undefined") return backendUrl;
  return `${window.location.origin}/${route}/${token}`;
}

/**
 * CC#35: cómo se identifica el expediente al compartir su liga. Vendedor (operación de venta):
 * "Casa Las Flores — María Pérez". Otro tipo de cliente (p. ej. arrendador en renta): solo el
 * nombre del propietario, "María Pérez". El nombre del inmueble es la referencia que capturó el
 * asesor; si no hay, el tipo de inmueble con su clave (o el folio). Todo sale del mismo expediente.
 */
export function uploadLinkIdentifier(e: {
  operationType?: string | null;
  propertyReference: string | null;
  propertyTypeLabel: string | null;
  propertyKey: string | null;
  folio: string;
  ownerDisplayName: string;
}): string {
  const reference = e.propertyReference?.trim();
  const property = reference || [e.propertyTypeLabel?.trim(), e.propertyKey ?? e.folio].filter(Boolean).join(" ");
  const owner = e.ownerDisplayName?.trim();
  if (e.operationType && e.operationType !== "SALE" && owner) return owner;
  return owner ? `${property} — ${owner}` : property;
}

/** CC#35: mensaje para WhatsApp con el identificador, el texto habitual de acceso y la liga. */
export function uploadLinkMessage(identifier: string, url: string): string {
  return [
    identifier,
    "",
    "Hola, te compartimos la liga de CENTURY 21 Genera para capturar tus datos y cargar los documentos de tu inmueble.",
    "No necesitas crear una cuenta. La liga es personal: no la compartas.",
    "",
    url,
  ].join("\n");
}

/** Liga de WhatsApp con el mensaje; con teléfono de 10 dígitos se abre el chat del cliente (+52). */
export function whatsappShareUrl(message: string, phone?: string | null): string {
  const digits = (phone ?? "").replace(/\D/g, "").replace(/^(\d{10})$/, "52$1");
  return `https://wa.me/${digits.length >= 11 ? digits : ""}?text=${encodeURIComponent(message)}`;
}
