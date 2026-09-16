"use client";

import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { NotApplicableBadge } from "@/components/ui/NotApplicableBadge";
import { useDemoApp } from "@/context/DemoAppProvider";
import type { Expediente, ExtractedField } from "@/types/expediente";
import { RotateCcw } from "lucide-react";
import { useState } from "react";

const originLabel: Record<ExtractedField["origin"], string> = {
  extraido: "Extraído",
  editado: "Editado manualmente",
  declarado: "Declarado por cliente",
  calculado: "Calculado",
  no_aplica: "No aplica",
};

const confidenceLabel: Record<NonNullable<ExtractedField["confidence"]>, string> = {
  alta: "Confianza alta",
  media: "Confianza media",
  revisar: "Revisar",
};

function FieldRow({ field, expedienteId }: { field: ExtractedField; expedienteId: string }) {
  const { updateExpediente } = useDemoApp();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(field.value);

  if (field.notApplicable) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-xl border border-border px-4 py-3">
        <span className="text-sm text-muted">{field.label}</span>
        <NotApplicableBadge />
      </div>
    );
  }

  const save = () => {
    updateExpediente(expedienteId, (current) => ({
      ...current,
      extractedFields: current.extractedFields.map((f) =>
        f.id === field.id ? { ...f, value, origin: "editado" } : f,
      ),
    }));
    setEditing(false);
  };

  const restore = () => {
    updateExpediente(expedienteId, (current) => ({
      ...current,
      extractedFields: current.extractedFields.map((f) => (f.id === field.id ? { ...f, origin: "extraido" } : f)),
    }));
  };

  return (
    <div className="rounded-xl border border-border px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-muted">{field.label}</span>
        <div className="flex flex-wrap gap-1.5">
          <Badge tone="gold">{field.source}</Badge>
          <Badge tone="neutral">{originLabel[field.origin]}</Badge>
          {field.confidence ? (
            <Badge tone={field.confidence === "revisar" ? "warning" : "info"}>{confidenceLabel[field.confidence]}</Badge>
          ) : null}
        </div>
      </div>
      {editing ? (
        <div className="mt-2 flex gap-2">
          <input
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className="w-full rounded-lg border border-border px-3 py-1.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
          <button onClick={save} className="rounded-lg bg-gold px-3 py-1.5 text-xs font-medium text-obsessed">
            Guardar
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setEditing(true)}
          className="mt-1.5 block w-full rounded-lg px-0 py-1 text-left text-sm text-obsessed hover:bg-app-bg/60"
        >
          {field.value || <span className="text-muted">Sin información</span>}
        </button>
      )}
      {field.origin === "editado" ? (
        <button onClick={restore} className="mt-1 inline-flex items-center gap-1 text-xs text-muted hover:text-dark-gold">
          <RotateCcw className="h-3 w-3" /> Restaurar valor detectado
        </button>
      ) : null}
    </div>
  );
}

export function ExtractedFieldsPanel({ expediente }: { expediente: Expediente }) {
  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Campos obtenidos automáticamente"
          description="La extracción es simulada. El personal interno puede editar cualquier valor."
        />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {expediente.extractedFields.map((field) => (
            <FieldRow key={field.id} field={field} expedienteId={expediente.id} />
          ))}
        </div>
      </Card>

      <Card>
        <CardHeader title="Datos declarados por el cliente" />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <InfoRow label="Estado civil" value={expediente.manualData.civilStatus} />
          <InfoRow label="Correo" value={expediente.manualData.email} />
          <InfoRow label="Teléfono" value={expediente.manualData.phone} />
          <InfoRow label="Domicilio para notificaciones" value={expediente.manualData.notificationAddress} />
          <InfoRow label="Instrucciones para visitas" value={expediente.manualData.visitInstructions} />
          <InfoRow
            label="Autoriza uso publicitario de datos"
            value={boolLabel(expediente.manualData.marketingDataAuthorized)}
          />
          <InfoRow label="Autoriza recibir publicidad" value={boolLabel(expediente.manualData.receiveAdsAuthorized)} />
        </div>
      </Card>
    </div>
  );
}

function boolLabel(value?: boolean) {
  if (value === undefined) return undefined;
  return value ? "Sí" : "No";
}

function InfoRow({ label, value }: { label: string; value?: string }) {
  return (
    <div className="rounded-xl border border-border px-4 py-3">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <p className="mt-1 text-sm text-obsessed">{value || "Sin información"}</p>
    </div>
  );
}
