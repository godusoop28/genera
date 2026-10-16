import { apiClient, uploadFiles } from "@/lib/api/client";
import { prepareUploads } from "@/lib/upload-limits";

/** Control de Cambios #2: el aviso de privacidad ya no bloquea el alta; se firma en la liga o en papel. */
export interface PrivacyStatusResponse {
  status: "SIGNED" | "PENDING";
  /** DIGITAL: en su liga; PHYSICAL: en papel en la oficina (el escaneo está en Documentos). */
  signatureMethod?: "DIGITAL" | "PHYSICAL" | null;
  signedAt?: string | null;
  secondaryPurposesAccepted?: boolean | null;
  signedDocumentId?: string | null;
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
