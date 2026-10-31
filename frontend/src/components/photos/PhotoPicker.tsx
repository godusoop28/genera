"use client";

import { Button } from "@/components/ui/Button";
import type { PhotoLimits } from "@/lib/api/public";
import type { UploadProgress } from "@/lib/api/client";
import { ApiError } from "@/lib/api/client";
import { Camera, ImagePlus, Loader2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

/** CC#33: formatos aceptados. Con solo JPG/PNG/WEBP, Safari en iPhone convierte las HEIC a JPG al elegirlas. */
export const PHOTO_ACCEPT = "image/jpeg,image/png,image/webp";

export function photoLimitsText(limits: PhotoLimits): string {
  const mb = Math.round(limits.maxBytesPerPhoto / 1024 / 1024);
  return `Formatos: ${limits.formats.join(", ")}. Hasta ${mb} MB por foto, ${limits.maxPerUpload} por carga y ${limits.maxPhotos} por inmueble (quedan ${limits.remaining}).`;
}

type Picked = { file: File; preview: string; error: string | null };

/**
 * Elegir varias fotos (computadora o teléfono; en teléfono, también con la cámara), verlas y
 * retirar las que se eligieron por error antes de confirmar la carga. Solo se suben al confirmar.
 */
export function PhotoPicker({
  limits,
  buttonLabel,
  onUpload,
}: {
  limits: PhotoLimits | null;
  buttonLabel: string;
  onUpload: (files: File[], onProgress: (p: UploadProgress) => void) => Promise<void>;
}) {
  const [picked, setPicked] = useState<Picked[]>([]);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const cameraInput = useRef<HTMLInputElement>(null);

  // Libera las vistas previas al salir.
  const pickedRef = useRef(picked);
  useEffect(() => {
    pickedRef.current = picked;
  }, [picked]);
  useEffect(() => () => pickedRef.current.forEach((p) => URL.revokeObjectURL(p.preview)), []);

  const add = (list: FileList | null) => {
    if (!list || !limits) return;
    setError(null);
    setDone(null);
    const next = Array.from(list).map((file) => ({
      file,
      preview: URL.createObjectURL(file),
      error:
        file.size > limits.maxBytesPerPhoto
          ? `Pesa más de ${Math.round(limits.maxBytesPerPhoto / 1024 / 1024)} MB.`
          : !PHOTO_ACCEPT.split(",").includes(file.type)
            ? "Formato no admitido (usa JPG, PNG o WEBP)."
            : null,
    }));
    setPicked((current) => [...current, ...next]);
  };

  const removePicked = (index: number) => {
    setPicked((current) => {
      URL.revokeObjectURL(current[index].preview);
      return current.filter((_, i) => i !== index);
    });
  };

  const valid = picked.filter((p) => !p.error);
  const tooMany = limits ? valid.length > Math.min(limits.maxPerUpload, limits.remaining) : false;

  const confirm = async () => {
    if (!valid.length || tooMany) return;
    setUploading(true);
    setError(null);
    setProgress(0);
    try {
      await onUpload(
        valid.map((p) => p.file),
        (p) => setProgress(p.percent),
      );
      picked.forEach((p) => URL.revokeObjectURL(p.preview));
      setDone(valid.length === 1 ? "Se guardó 1 fotografía." : `Se guardaron ${valid.length} fotografías.`);
      setPicked([]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudieron subir las fotos. Revisa tu conexión e intenta de nuevo; tu selección se conserva.");
    } finally {
      setUploading(false);
      setProgress(null);
    }
  };

  return (
    <div className="flex flex-col gap-3">
      {limits ? <p className="text-xs text-muted">{photoLimitsText(limits)}</p> : null}
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" onClick={() => fileInput.current?.click()} disabled={!limits || limits.remaining === 0 || uploading}>
          <ImagePlus className="h-4 w-4" aria-hidden /> {buttonLabel}
        </Button>
        {/* En teléfono abre la cámara si el navegador lo permite; en computadora se comporta como "elegir archivo". */}
        <Button type="button" variant="ghost" className="sm:hidden" onClick={() => cameraInput.current?.click()} disabled={!limits || limits.remaining === 0 || uploading}>
          <Camera className="h-4 w-4" aria-hidden /> Tomar foto
        </Button>
        <input
          ref={fileInput}
          type="file"
          accept={PHOTO_ACCEPT}
          multiple
          className="hidden"
          onChange={(e) => {
            add(e.target.files);
            e.target.value = "";
          }}
        />
        <input
          ref={cameraInput}
          type="file"
          accept={PHOTO_ACCEPT}
          capture="environment"
          className="hidden"
          onChange={(e) => {
            add(e.target.files);
            e.target.value = "";
          }}
        />
      </div>

      {picked.length > 0 ? (
        <div className="rounded-xl border border-border bg-app-bg p-3">
          <p className="mb-2 text-sm font-medium text-obsessed">Revisa las fotos antes de subirlas</p>
          <ul className="grid grid-cols-3 gap-2 sm:grid-cols-5">
            {picked.map((p, i) => (
              <li key={p.preview} className="relative">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={p.preview} alt={p.file.name} className={`aspect-square w-full rounded-lg object-cover ${p.error ? "opacity-40" : ""}`} />
                <button
                  type="button"
                  onClick={() => removePicked(i)}
                  disabled={uploading}
                  aria-label={`Quitar ${p.file.name}`}
                  className="absolute right-1 top-1 rounded-full bg-obsessed/80 p-1 text-white hover:bg-obsessed"
                >
                  <X className="h-3.5 w-3.5" aria-hidden />
                </button>
                {p.error ? <p className="mt-1 text-[11px] leading-tight text-danger-text">{p.error}</p> : null}
              </li>
            ))}
          </ul>
          {tooMany && limits ? (
            <p className="mt-2 text-xs text-danger-text">
              Puedes subir hasta {Math.min(limits.maxPerUpload, limits.remaining)} fotos en esta carga; quita algunas.
            </p>
          ) : null}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Button type="button" onClick={confirm} disabled={!valid.length || tooMany || uploading}>
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
              {uploading ? `Subiendo… ${progress ?? 0}%` : `Subir ${valid.length} foto${valid.length === 1 ? "" : "s"}`}
            </Button>
            {picked.some((p) => p.error) ? <span className="text-xs text-muted">Las fotos marcadas no se subirán.</span> : null}
          </div>
        </div>
      ) : null}

      {error ? (
        <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">
          {error}
        </p>
      ) : null}
      {done ? (
        <p role="status" className="text-sm text-success-text">
          {done}
        </p>
      ) : null}
    </div>
  );
}
