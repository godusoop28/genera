import type { DraftSaveState } from "@/lib/draft-sync";
import { AlertTriangle, Check, Loader2 } from "lucide-react";

/** Indicador del autoguardado: el usuario sabe si lo capturado ya quedó guardado. */
export function SaveStatus({ status, lastSavedAt }: { status: DraftSaveState; lastSavedAt: Date | null }) {
  if (status === "saving") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-muted" role="status">
        <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden /> Guardando…
      </span>
    );
  }
  if (status === "error") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-warning-text" role="status">
        <AlertTriangle className="h-3.5 w-3.5" aria-hidden /> No se pudo guardar; reintentando. Lo que escribiste sigue aquí.
      </span>
    );
  }
  if (status === "saved") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-success-text" role="status">
        <Check className="h-3.5 w-3.5" aria-hidden /> Guardado
        {lastSavedAt ? ` a las ${lastSavedAt.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit" })}` : ""}
      </span>
    );
  }
  return null;
}
