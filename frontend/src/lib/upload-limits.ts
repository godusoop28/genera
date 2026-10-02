import { apiClient } from "@/lib/api/client";
import type { UploadLimitsResponse } from "@/lib/api/types";
import { shrinkImages } from "@/lib/image-shrink";

// Límites de carga sincronizados con el backend (GET /public/upload-limits):
// se avisa ANTES de subir un archivo que el servidor rechazaría. Si el
// servidor no responde se usan los mismos valores por defecto del backend.

export const DEFAULT_UPLOAD_LIMITS: UploadLimitsResponse = {
  maxFileSizeBytes: 40 * 1024 * 1024,
  maxFilesPerRequest: 30,
  allowedMimeTypes: ["image/jpeg", "image/png", "image/webp", "application/pdf"],
};

/** Lo que aceptan los <input type="file">: HEIC/HEIF se convierte a JPEG antes de subirse. */
export const ACCEPTED_FILE_TYPES = "image/jpeg,image/png,image/webp,image/heic,image/heif,.heic,.heif,application/pdf";

let limitsPromise: Promise<UploadLimitsResponse> | null = null;

export function getUploadLimits(): Promise<UploadLimitsResponse> {
  if (!limitsPromise) {
    limitsPromise = apiClient
      .get<UploadLimitsResponse>("/public/upload-limits")
      .then((limits) => limits ?? DEFAULT_UPLOAD_LIMITS)
      .catch(() => {
        limitsPromise = null; // Se vuelve a intentar en la próxima carga.
        return DEFAULT_UPLOAD_LIMITS;
      });
  }
  return limitsPromise;
}

export function formatMegabytes(bytes: number): string {
  return `${Math.round(bytes / (1024 * 1024))} MB`;
}

export class UploadValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "UploadValidationError";
  }
}

function isHeic(file: File): boolean {
  return /image\/hei[cf]/i.test(file.type) || /\.hei[cf]$/i.test(file.name);
}

/** Convierte una foto HEIC/HEIF (iPhone) a JPEG en el navegador. La librería se carga solo si hace falta. */
async function convertHeic(file: File): Promise<File> {
  try {
    const { default: heic2any } = await import("heic2any");
    const result = await heic2any({ blob: file, toType: "image/jpeg", quality: 0.9 });
    const blob = Array.isArray(result) ? result[0] : result;
    return new File([blob], `${file.name.replace(/\.[^.]+$/, "")}.jpg`, { type: "image/jpeg", lastModified: file.lastModified });
  } catch {
    throw new UploadValidationError(`No se pudo convertir la foto "${file.name}" (HEIC). Vuelve a intentarlo o envíala como JPG.`);
  }
}

/**
 * Deja los archivos listos para subir: convierte HEIC a JPEG, reduce las fotos
 * grandes (sin perder legibilidad) y valida tamaño y cantidad con los mismos
 * límites del servidor.
 */
export async function prepareUploads(files: File[]): Promise<File[]> {
  const limits = await getUploadLimits();
  if (files.length > limits.maxFilesPerRequest) {
    throw new UploadValidationError(`Puedes subir hasta ${limits.maxFilesPerRequest} archivos a la vez.`);
  }
  const converted = await Promise.all(files.map((f) => (isHeic(f) ? convertHeic(f) : Promise.resolve(f))));
  const prepared = await shrinkImages(converted);
  for (const file of prepared) {
    if (file.size > limits.maxFileSizeBytes) {
      throw new UploadValidationError(
        `"${file.name}" pesa ${formatMegabytes(file.size)}; el máximo es ${formatMegabytes(limits.maxFileSizeBytes)} por archivo. ` +
          "Si es un PDF, guárdalo en menor calidad o súbelo en partes.",
      );
    }
    if (file.type && !limits.allowedMimeTypes.includes(file.type)) {
      throw new UploadValidationError(`"${file.name}" no es un formato aceptado. Sube fotos (JPG, PNG, WEBP o HEIC) o PDF.`);
    }
  }
  return prepared;
}
