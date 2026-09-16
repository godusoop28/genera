"use client";

import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { makeActivity } from "@/lib/expediente-factory";
import type { Expediente } from "@/types/expediente";
import { useMemo, useState } from "react";

interface SendDocumentsModalProps {
  open: boolean;
  onClose: () => void;
  expediente: Expediente;
}

export function SendDocumentsModal({ open, onClose, expediente }: SendDocumentsModalProps) {
  const { updateExpediente } = useDemoApp();
  const { showToast } = useToast();

  const attachmentOptions = useMemo(() => {
    const docs = expediente.documentRequirements
      .filter((r) => r.required && expediente.documents[r.id]?.status === "accepted")
      .map((r) => r.name);
    return ["Contrato", "Aviso de privacidad", ...docs, "Anexos"];
  }, [expediente]);

  const [to, setTo] = useState(expediente.manualData.email ?? "");
  const [subject, setSubject] = useState(`Documentación del expediente ${expediente.folio}`);
  const [message, setMessage] = useState(
    "Adjunto encontrará la documentación correspondiente a su expediente con CENTURY 21 Genera.",
  );
  const [selected, setSelected] = useState<string[]>(attachmentOptions);

  const toggle = (name: string) =>
    setSelected((prev) => (prev.includes(name) ? prev.filter((a) => a !== name) : [...prev, name]));

  const handleSend = () => {
    const now = new Date().toISOString();
    updateExpediente(expediente.id, (current) => ({
      ...current,
      emails: [{ to, subject, message, attachments: selected, sentAt: now }, ...current.emails],
      activity: [makeActivity("correo_enviado", `Correo simulado enviado a ${to || "destinatario"}.`, now), ...current.activity],
    }));
    showToast("Correo simulado enviado.");
    onClose();
  };

  return (
    <Modal open={open} onClose={onClose} title="Enviar documentación por correo" size="lg">
      <div className="flex flex-col gap-4">
        <Input label="Destinatario" value={to} onChange={(e) => setTo(e.target.value)} placeholder="cliente@correo.com" />
        <Input label="Asunto" value={subject} onChange={(e) => setSubject(e.target.value)} />
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-obsessed" htmlFor="email-message">
            Mensaje
          </label>
          <textarea
            id="email-message"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            rows={3}
            className="w-full rounded-xl border border-border bg-white px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <p className="text-sm font-medium text-obsessed">Adjuntos</p>
            <div className="flex gap-3 text-xs font-medium text-dark-gold">
              <button type="button" onClick={() => setSelected(attachmentOptions)} className="hover:underline">
                Seleccionar todos
              </button>
              <button type="button" onClick={() => setSelected([])} className="hover:underline">
                Deseleccionar
              </button>
            </div>
          </div>
          <div className="flex flex-col gap-2 rounded-xl border border-border p-3">
            {attachmentOptions.map((name) => (
              <label key={name} className="flex items-center gap-2.5 text-sm text-obsessed">
                <input
                  type="checkbox"
                  checked={selected.includes(name)}
                  onChange={() => toggle(name)}
                  className="h-4 w-4 rounded border-border accent-[var(--color-c21-dark-gold)]"
                />
                {name}
              </label>
            ))}
          </div>
        </div>

        <div className="mt-2 flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={handleSend} disabled={!to}>
            Enviar
          </Button>
        </div>
      </div>
    </Modal>
  );
}
