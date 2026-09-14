"use client";

import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { ownerFields, ownerManualFields, propertyFields } from "@/data/mock-expediente";
import { useState } from "react";

function AutoField({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-muted">{label}</span>
        <Badge tone="teal">Extraído automáticamente</Badge>
      </div>
      <p className="rounded-xl border border-border bg-app-bg/50 px-3.5 py-2.5 text-sm text-navy">
        {value}
      </p>
    </div>
  );
}

function ManualField({
  label,
  value,
  suffix,
  onChange,
}: {
  label: string;
  value: string;
  suffix?: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-muted">{label}</span>
        <Badge tone="manual">Manual</Badge>
      </div>
      <div className="relative">
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="w-full rounded-xl border border-manual-text/30 bg-manual-bg/40 px-3.5 py-2.5 text-sm text-navy focus:border-manual-text focus:outline-none focus:ring-4 focus:ring-manual-text/15"
        />
        {suffix ? (
          <span className="pointer-events-none absolute inset-y-0 right-3.5 flex items-center text-sm text-muted">
            {suffix}
          </span>
        ) : null}
      </div>
    </div>
  );
}

export function ContractFields() {
  const [manualValues, setManualValues] = useState(
    Object.fromEntries(ownerManualFields.map((field) => [field.label, field.value])),
  );

  return (
    <Card>
      <CardHeader
        title="Campos para el contrato"
        description="La información fue extraída automáticamente. Revisa y completa los campos manuales."
      />

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
        <div>
          <p className="mb-4 text-sm font-semibold text-navy">Datos del propietario</p>
          <div className="space-y-4">
            {ownerFields.map((field) => (
              <AutoField key={field.label} label={field.label} value={field.value} />
            ))}
            {ownerManualFields.map((field) => (
              <ManualField
                key={field.label}
                label={field.label}
                suffix={field.suffix}
                value={manualValues[field.label]}
                onChange={(value) =>
                  setManualValues((prev) => ({ ...prev, [field.label]: value }))
                }
              />
            ))}
          </div>
        </div>

        <div>
          <p className="mb-4 text-sm font-semibold text-navy">Datos del inmueble</p>
          <div className="space-y-4">
            {propertyFields.map((field) => (
              <AutoField key={field.label} label={field.label} value={field.value} />
            ))}
          </div>
        </div>
      </div>
    </Card>
  );
}
