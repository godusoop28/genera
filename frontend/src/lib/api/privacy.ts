import { apiClient, uploadFiles } from "@/lib/api/client";
import { prepareUploads } from "@/lib/upload-limits";

/** Control de Cambios #44: la liga exclusiva más reciente para firmar el aviso. */
export interface PrivacySignLinkStatus {
  createdAt: string;
  createdByName: string | null;
  expiresAt: string;
  revokedAt: string | null;
  usedAt: string | null;
  lastOpenedAt: string | null;
  usable: boolean;
}

/**
 * Control de Cambios #2/#45: el aviso no bloquea el alta. PENDING_VERIFICATION: se cargó un escaneo
 * pero aún no se acepta en Documentos (cargar no equivale a firma confirmada).
 */
export interface PrivacyStatusResponse {
  status: "SIGNED" | "PENDING" | "PENDING_VERIFICATION";
  /** DIGITAL: en una liga; PHYSICAL: en papel en la oficina. */
  signatureMethod?: "DIGITAL" | "PHYSICAL" | null;
  signedAt?: string | null;
  secondaryPurposesAccepted?: boolean | null;
  /** Documento «Aviso de Privacidad» del expediente (escaneo o copia firmada digitalmente). */
  signedDocumentId?: string | null;
  /** PORTAL: liga del expediente; SIGN_LINK: liga exclusiva del aviso. */
  signatureChannel?: "PORTAL" | "SIGN_LINK" | null;
  signLink?: PrivacySignLinkStatus | null;
}

export function getPrivacyStatus(expedienteId: string) {
  return apiClient.get<PrivacyStatusResponse>(`/internal/expedientes/${expedienteId}/privacy`);
}

/** El titular firmó el aviso impreso: se carga el escaneo o la foto y queda registrado. */
export async function uploadPhysicalPrivacySignature(expedienteId: string, files: File[], secondaryPurposesAccepted: boolean) {
  return uploadFiles<PrivacyStatusResponse>(
    `/internal/expedientes/${expedienteId}/privacy/physical-signature?secondaryPurposesAccepted=${secondaryPurposesAccepted}`,
    "files",
    await prepareUploads(files),
  );
}

/** El alta sigue sin el aviso firmado (p. ej. presencial); se firma después. */
export function deferPrivacySignature(expedienteId: string, reason?: string) {
  return apiClient.post(`/internal/expedientes/${expedienteId}/privacy/defer`, { reason });
}

/** Control de Cambios #44: genera (o reenvía) la liga exclusiva; la anterior deja de funcionar. */
export function issuePrivacySignLink(expedienteId: string) {
  return apiClient.post<{ token: string; url: string; expiresAt: string }>(`/internal/expedientes/${expedienteId}/privacy/sign-link`, {});
}

export function revokePrivacySignLink(expedienteId: string) {
  return apiClient.delete<PrivacyStatusResponse>(`/internal/expedientes/${expedienteId}/privacy/sign-link`);
}
