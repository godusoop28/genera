"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useState } from "react";

interface RejectDocumentModalProps {
  open: boolean;
  documentName: string;
  onClose: () => void;
  onConfirm: (comment: string) => void;
}

export function RejectDocumentModal({ open, documentName, onClose, onConfirm }: RejectDocumentModalProps) {
  const [comment, setComment] = useState("");

  return (
    <Modal open={open} onClose={onClose} title={`Rechazar documento · ${documentName}`}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">
          Esta acción marca el documento como rechazado de forma definitiva. No debe confundirse
          con rechazar el inmueble.
        </p>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-obsessed" htmlFor="reject-comment">
            Motivo del rechazo
          </label>
          <textarea
            id="reject-comment"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={3}
            className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>
        <div className="mt-2 flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              onConfirm(comment);
              setComment("");
            }}
          >
            Rechazar documento
          </Button>
        </div>
      </div>
    </Modal>
  );
}
