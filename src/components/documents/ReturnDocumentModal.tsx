"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { returnReasonLabels, type ReturnReason } from "@/types/expediente";
import { useState } from "react";

interface ReturnDocumentModalProps {
  open: boolean;
  documentName: string;
  onClose: () => void;
  onConfirm: (reason: ReturnReason, comment: string) => void;
}

const reasonOptions = Object.entries(returnReasonLabels).map(([value, label]) => ({ value, label }));

export function ReturnDocumentModal({ open, documentName, onClose, onConfirm }: ReturnDocumentModalProps) {
  const [reason, setReason] = useState<ReturnReason>("imagen_borrosa");
  const [comment, setComment] = useState("");

  return (
    <Modal open={open} onClose={onClose} title={`Solicitar corrección · ${documentName}`}>
      <div className="flex flex-col gap-4">
        <Select
          label="Motivo de devolución"
          value={reason}
          onChange={(e) => setReason(e.target.value as ReturnReason)}
          options={reasonOptions}
        />
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-obsessed" htmlFor="return-comment">
            Comentario para el cliente (opcional)
          </label>
          <textarea
            id="return-comment"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={3}
            className="w-full rounded-xl border border-border bg-white px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
            placeholder="Ej. La imagen no permite leer claramente la fecha de expedición."
          />
        </div>
        <div className="mt-2 flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => {
              onConfirm(reason, comment);
              setComment("");
            }}
          >
            Devolver documento
          </Button>
        </div>
      </div>
    </Modal>
  );
}
