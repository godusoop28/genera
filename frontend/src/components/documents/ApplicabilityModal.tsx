"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { cn } from "@/lib/utils";
import { Loader2 } from "lucide-react";
import { useState } from "react";

export type ApplicabilityChoice =
  | { kind: "LATER"; reason: string }
  | { kind: "NOT_APPLICABLE"; justification: string };

/** Mismos mínimos que el backend (DocumentService). */
const MIN_LATER_REASON = 10;
const MIN_NOT_APPLICABLE = 15;

/**
 * Control de Cambios #46: al no pedir un documento ahora, se distingue si sí aplica y se entregará
 * después (queda «Pendiente» con su seguimiento normal) o si no aplica a este expediente (motivo
 * obligatorio; queda «No aplica» y no se vuelve a pedir ni cuenta como pendiente). La decisión es
 * por documento y por expediente: nunca se aplica sola por el tipo de inmueble.
 *
 * Se monta de nuevo por documento (key) para empezar siempre sin elección.
 */
export function ApplicabilityModal({
  open,
  documentName,
  canMarkNotApplicable,
  notApplicableBlockedReason,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  documentName: string;
  /** Con el permiso «Marcar documento como No aplica». */
  canMarkNotApplicable: boolean;
  /** Si el documento no admite «No aplica» (p. ej. la identificación), por qué. */
  notApplicableBlockedReason?: string | null;
  onCancel: () => void;
  onConfirm: (choice: ApplicabilityChoice) => Promise<void>;
}) {
  const [kind, setKind] = useState<"LATER" | "NOT_APPLICABLE" | null>(null);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);

  const naDisabled =
    !canMarkNotApplicable || Boolean(notApplicableBlockedReason);
  const min = kind === "NOT_APPLICABLE" ? MIN_NOT_APPLICABLE : MIN_LATER_REASON;
  const valid = kind !== null && text.trim().length >= min;

  const confirm = async () => {
    if (!valid || kind === null) return;
    setBusy(true);
    try {
      await onConfirm(
        kind === "LATER"
          ? { kind, reason: text.trim() }
          : { kind, justification: text.trim() },
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={`¿"${documentName}" aplica a la propiedad?`}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            Cancelar
          </Button>
          <Button onClick={confirm} disabled={!valid || busy}>
            {busy ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            ) : null}
            {kind === "NOT_APPLICABLE"
              ? "Marcar como No aplica"
              : "Dejar pendiente"}
          </Button>
        </>
      }
    >
      <div
        className="flex flex-col gap-3 text-sm"
        role="radiogroup"
        aria-label="¿Este documento aplica a la propiedad?"
      >
        <p className="font-medium text-obsessed">
          ¿Este documento aplica a la propiedad?
        </p>
        <label
          className={cn(
            "flex cursor-pointer items-start gap-3 rounded-xl border px-3 py-2.5",
            kind === "LATER" ? "border-gold/60 bg-gold/5" : "border-border",
          )}
        >
          <input
            type="radio"
            name="applicability"
            className="mt-0.5 accent-dark-gold"
            checked={kind === "LATER"}
            onChange={() => setKind("LATER")}
          />
          <span>
            <span className="block font-medium text-obsessed">
              Sí aplica; lo entregaré después.
            </span>
            <span className="block text-xs text-muted">
              Queda como «Pendiente» y podrá solicitarse posteriormente. El
              cliente lo sube desde su misma liga.
            </span>
          </span>
        </label>
        <label
          className={cn(
            "flex items-start gap-3 rounded-xl border px-3 py-2.5",
            naDisabled ? "cursor-not-allowed opacity-60" : "cursor-pointer",
            kind === "NOT_APPLICABLE"
              ? "border-gold/60 bg-gold/5"
              : "border-border",
          )}
        >
          <input
            type="radio"
            name="applicability"
            className="mt-0.5 accent-dark-gold"
            checked={kind === "NOT_APPLICABLE"}
            disabled={naDisabled}
            onChange={() => setKind("NOT_APPLICABLE")}
          />
          <span>
            <span className="block font-medium text-obsessed">No aplica.</span>
            <span className="block text-xs text-muted">
              Quedará como «No aplica» y no se solicitará después para este
              expediente. Se puede corregir si cambian las circunstancias.
            </span>
            {notApplicableBlockedReason ? (
              <span className="mt-1 block text-xs text-warning-text">
                {notApplicableBlockedReason}
              </span>
            ) : !canMarkNotApplicable ? (
              <span className="mt-1 block text-xs text-warning-text">
                Tu rol no puede marcar documentos como «No aplica»; pídeselo a
                un revisor o director.
              </span>
            ) : null}
          </span>
        </label>
        {kind ? (
          <label className="flex flex-col gap-1.5">
            <span className="font-medium text-obsessed">
              {kind === "NOT_APPLICABLE"
                ? "Indica el motivo (obligatorio)"
                : "¿Por qué se entrega después?"}
            </span>
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={3}
              autoFocus
              placeholder={
                kind === "NOT_APPLICABLE"
                  ? "Ej. Es un terreno sin construcción: no tiene contrato de luz a su nombre."
                  : "Ej. El cliente está tramitando el certificado y lo tendrá la próxima semana."
              }
              className="rounded-xl border border-border bg-card px-3.5 py-2.5 text-obsessed outline-none focus:border-gold"
            />
            <span className="text-xs text-muted">
              Mínimo {min} caracteres. Queda en el expediente y en la bitácora.
            </span>
          </label>
        ) : null}
      </div>
    </Modal>
  );
}
