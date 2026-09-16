"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { useDemoApp } from "@/context/DemoAppProvider";
import { formatDateEs } from "@/lib/calculations";
import { generateId, makeActivity } from "@/lib/expediente-factory";
import type { ClosingStatus, Expediente } from "@/types/expediente";
import { FileText, Plus } from "lucide-react";
import { useState } from "react";

const statusLabels: Record<ClosingStatus, string> = {
  pending: "Pendiente",
  in_progress: "En proceso",
  completed: "Completado",
};

export function ClosingSection({ expediente }: { expediente: Expediente }) {
  const { updateExpediente } = useDemoApp();
  const [docName, setDocName] = useState("");
  const closing = expediente.closing;

  const addDocument = () => {
    if (!docName.trim()) return;
    updateExpediente(expediente.id, (current) => ({
      ...current,
      closing: {
        ...current.closing,
        documents: [
          ...current.closing.documents,
          { id: generateId("closing-doc"), name: docName.trim(), addedAt: new Date().toISOString() },
        ],
      },
      activity: [makeActivity("cierre_actualizado", `Documento de cierre agregado: ${docName.trim()}.`), ...current.activity],
    }));
    setDocName("");
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Cierre de venta"
          action={
            <Select
              value={closing.status}
              onChange={(e) =>
                updateExpediente(expediente.id, (current) => ({
                  ...current,
                  closing: { ...current.closing, status: e.target.value as ClosingStatus },
                }))
              }
              options={Object.entries(statusLabels).map(([value, label]) => ({ value, label }))}
            />
          }
        />
        <p className="text-xs text-muted">
          Esta sección queda preparada para las especificaciones posteriores del cierre
          documental. No se asumen documentos jurídicos obligatorios adicionales.
        </p>
      </Card>

      <Card>
        <CardHeader title="Entrega del contrato" />
        <div className="flex flex-col gap-4">
          <label className="flex items-center gap-2.5 text-sm text-obsessed">
            <input
              type="checkbox"
              checked={closing.contractSigned}
              onChange={(e) =>
                updateExpediente(expediente.id, (current) => ({
                  ...current,
                  closing: { ...current.closing, contractSigned: e.target.checked },
                }))
              }
              className="h-4 w-4 rounded border-border accent-[var(--color-c21-dark-gold)]"
            />
            Contrato firmado
          </label>
          <label className="flex items-center gap-2.5 text-sm text-obsessed">
            <input
              type="checkbox"
              checked={closing.copyDelivered}
              onChange={(e) =>
                updateExpediente(expediente.id, (current) => ({
                  ...current,
                  closing: {
                    ...current.closing,
                    copyDelivered: e.target.checked,
                    deliveryDate: e.target.checked ? new Date().toISOString() : undefined,
                  },
                }))
              }
              className="h-4 w-4 rounded border-border accent-[var(--color-c21-dark-gold)]"
            />
            Copia entregada al cliente
          </label>
          {closing.copyDelivered ? (
            <div className="flex items-center gap-3">
              <Select
                label="Medio de entrega"
                value={closing.deliveryMethod ?? "correo"}
                onChange={(e) =>
                  updateExpediente(expediente.id, (current) => ({
                    ...current,
                    closing: { ...current.closing, deliveryMethod: e.target.value as "correo" | "fisico" | "otro" },
                  }))
                }
                options={[
                  { label: "Correo", value: "correo" },
                  { label: "Físico", value: "fisico" },
                  { label: "Otro", value: "otro" },
                ]}
              />
              {closing.deliveryDate ? (
                <p className="text-xs text-muted">Entregado el {formatDateEs(closing.deliveryDate)}</p>
              ) : null}
            </div>
          ) : null}
        </div>
      </Card>

      <Card>
        <CardHeader title="Documentos de cierre" action={<Badge tone="neutral">{closing.documents.length}</Badge>} />
        {closing.documents.length === 0 ? (
          <p className="text-sm text-muted">Sin documentos de cierre agregados todavía.</p>
        ) : (
          <ul className="mb-4 space-y-2">
            {closing.documents.map((doc) => (
              <li key={doc.id} className="flex items-center gap-2.5 rounded-xl border border-border px-3.5 py-2.5 text-sm">
                <FileText className="h-4 w-4 shrink-0 text-muted" aria-hidden />
                <span className="text-obsessed">{doc.name}</span>
                <span className="ml-auto text-xs text-muted">{formatDateEs(doc.addedAt)}</span>
              </li>
            ))}
          </ul>
        )}
        <div className="flex gap-2">
          <Input
            value={docName}
            onChange={(e) => setDocName(e.target.value)}
            placeholder="Nombre del documento"
            containerClassName="flex-1"
          />
          <Button variant="secondary" onClick={addDocument}>
            <Plus className="h-4 w-4" /> Agregar
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader title="Notas" />
        <textarea
          value={closing.notes}
          onChange={(e) =>
            updateExpediente(expediente.id, (current) => ({
              ...current,
              closing: { ...current.closing, notes: e.target.value },
            }))
          }
          rows={4}
          className="w-full rounded-xl border border-border bg-white px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
        />
      </Card>
    </div>
  );
}
