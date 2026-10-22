"use client";

import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { assignPropertyKey, checkPropertyKey, type PropertyKeyCheck } from "@/lib/api/expedientes";
import type { ExpedienteResponse } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { cn } from "@/lib/utils";
import { AlertTriangle, CheckCircle2, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";

/**
 * Control de Cambios #27 (adenda): valida en tiempo real (mientras se escribe) si la clave tiene el
 * formato y si otro expediente ya la tiene. null mientras no hay nada que validar.
 */
export function usePropertyKeyCheck(key: string, expedienteId?: string) {
  const [result, setResult] = useState<{ key: string; check: PropertyKeyCheck | null; error: string | null } | null>(null);
  const trimmed = key.trim();
  useEffect(() => {
    if (!trimmed) return;
    let cancelled = false;
    const timer = setTimeout(() => {
      checkPropertyKey(trimmed, expedienteId)
        .then((check) => !cancelled && setResult({ key: trimmed, check, error: null }))
        .catch((err) => !cancelled && setResult({ key: trimmed, check: null, error: errorText(err, "No se pudo validar la clave.") }));
    }, 350);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [trimmed, expedienteId]);
  if (!trimmed) return { checking: false, check: null, error: null };
  const current = result?.key === trimmed ? result : null;
  return { checking: current === null, check: current?.check ?? null, error: current?.error ?? null };
}

/** Mensaje de la validación: ⚠️ en uso (con el expediente que la tiene) o ✅ disponible. */
export function PropertyKeyCheckMessage({ checking, check, error }: ReturnType<typeof usePropertyKeyCheck>) {
  if (checking) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted" aria-live="polite">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Validando clave…
      </p>
    );
  }
  if (error) {
    return (
      <p className="text-sm text-danger-text" aria-live="polite">
        {error}
      </p>
    );
  }
  if (!check) return null;
  const ok = check.valid && check.available;
  return (
    <p
      aria-live="polite"
      className={cn(
        "flex items-start gap-2 rounded-xl border px-3 py-2 text-sm",
        ok ? "border-success-text/20 bg-success-bg text-success-text" : "border-danger-text/20 bg-danger-bg text-danger-text",
      )}
    >
      {ok ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden /> : <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />}
      <span>{check.message}</span>
    </p>
  );
}

/**
 * Asignar o corregir a mano la clave de un expediente (administrador o directivo). Se respeta la
 * clave capturada: no se genera consecutivo automático. No deja guardar si la clave ya está en uso.
 */
export function PropertyKeyModal({
  expediente,
  suggestion,
  onClose,
  onSaved,
}: {
  expediente: Pick<ExpedienteResponse, "id" | "folio" | "propertyKey">;
  /** Clave sugerida (siguiente consecutivo de su combinación), para prellenar. */
  suggestion?: string | null;
  onClose: () => void;
  onSaved: (updated: ExpedienteResponse) => void;
}) {
  const [key, setKey] = useState(expediente.propertyKey ?? suggestion ?? "");
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const validation = usePropertyKeyCheck(key, expediente.id);
  const unchanged = validation.check?.key === expediente.propertyKey;
  const canSave = !saving && !validation.checking && validation.check?.valid === true && validation.check.available && !unchanged;

  const save = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      onSaved(await assignPropertyKey(expediente.id, key.trim(), reason.trim() || undefined));
    } catch (err) {
      setSaveError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={expediente.propertyKey ? `Corregir clave de ${expediente.folio}` : `Asignar clave a ${expediente.folio}`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={save} disabled={!canSave}>
            {saving ? "Guardando…" : "Guardar clave"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-muted">
          Formato: tipo + operación (V venta, R renta) + iniciales del asesor + consecutivo, p. ej. <strong>CVEA03</strong>. Se guarda tal como la
          capturas, sin generar consecutivo automático.
        </p>
        <Input
          label="Clave"
          value={key}
          maxLength={24}
          autoFocus
          className="font-mono uppercase tracking-wide"
          onChange={(e) => setKey(e.target.value.toUpperCase())}
        />
        <PropertyKeyCheckMessage {...validation} />
        {expediente.propertyKey ? (
          <Input label="Motivo (opcional)" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} placeholder="Ej. migración del inventario" />
        ) : null}
        {saveError ? <p className="text-sm text-danger-text">{saveError}</p> : null}
      </div>
    </Modal>
  );
}
