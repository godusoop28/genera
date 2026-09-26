"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useState, type ReactNode } from "react";

interface ReasonModalProps {
  open: boolean;
  title: string;
  description?: ReactNode;
  label?: string;
  placeholder?: string;
  confirmLabel?: string;
  /** Longitud mínima del texto (0 = opcional). */
  minLength?: number;
  danger?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => Promise<void> | void;
}

/** Pide confirmación con un motivo/justificación antes de una acción que queda en la bitácora. */
export function ReasonModal({
  open,
  title,
  description,
  label = "Motivo",
  placeholder,
  confirmLabel = "Confirmar",
  minLength = 0,
  danger = false,
  onCancel,
  onConfirm,
}: ReasonModalProps) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const valid = reason.trim().length >= minLength;

  const confirm = async () => {
    setBusy(true);
    try {
      await onConfirm(reason.trim());
      setReason("");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={title}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            Cancelar
          </Button>
          <Button variant={danger ? "danger" : "primary"} onClick={confirm} disabled={!valid || busy}>
            {busy ? "Guardando…" : confirmLabel}
          </Button>
        </>
      }
    >
      {description ? <div className="mb-3 text-sm text-muted">{description}</div> : null}
      <label className="mb-1 block text-sm font-medium text-obsessed">{label}</label>
      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder={placeholder}
        rows={3}
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold focus:ring-4 focus:ring-gold/20"
      />
      {minLength > 0 ? (
        <p className="mt-1 text-xs text-muted">
          {reason.trim().length}/{minLength} caracteres mínimo.
        </p>
      ) : null}
    </Modal>
  );
}
