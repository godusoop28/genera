"use client";

import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useDemoApp } from "@/context/DemoAppProvider";
import type { Expediente } from "@/types/expediente";

function ReadOnlyField({ label, value, source }: { label: string; value: string; source: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-muted">{label}</span>
        <Badge tone="gold">{source}</Badge>
      </div>
      <p className="rounded-xl border border-border bg-app-bg/50 px-3.5 py-2.5 text-sm text-obsessed">
        {value || "Sin información"}
      </p>
    </div>
  );
}

export function ContractFields({ expediente }: { expediente: Expediente }) {
  const { updateExpediente } = useDemoApp();
  const propertyAddress = expediente.extractedFields.find((f) => f.id === "domicilio-inmueble")?.value;

  return (
    <Card>
      <CardHeader
        title="Campos para el contrato"
        description="Los datos extraídos son de solo lectura aquí. Edítalos desde la pestaña Información."
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          <ReadOnlyField label="Cliente" value={expediente.ownerName} source="INE" />
          <ReadOnlyField label="Domicilio del inmueble" value={propertyAddress ?? expediente.propertyAddress ?? ""} source="Escritura" />

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium uppercase tracking-wide text-muted">Precio autorizado de venta</span>
              <Badge tone="manual">Manual</Badge>
            </div>
            <Input
              type="number"
              suffix="MXN"
              value={expediente.manualData.authorizedPrice ?? ""}
              onChange={(e) =>
                updateExpediente(expediente.id, (current) => ({
                  ...current,
                  manualData: { ...current.manualData, authorizedPrice: e.target.value ? Number(e.target.value) : undefined },
                }))
              }
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium uppercase tracking-wide text-muted">Fecha de firma del contrato</span>
              <Badge tone="manual">Manual</Badge>
            </div>
            <Input
              type="date"
              value={expediente.manualData.contractSignatureDate ?? ""}
              onChange={(e) =>
                updateExpediente(expediente.id, (current) => ({
                  ...current,
                  manualData: { ...current.manualData, contractSignatureDate: e.target.value },
                }))
              }
            />
          </div>
        </div>

        <div className="space-y-4">
          <ReadOnlyField label="Carácter" value={expediente.config.signerCharacter} source="Poder notarial" />
          <ReadOnlyField
            label="Tipo de inmueble"
            value={expediente.config.propertyType === "vivienda" ? "Vivienda destinada a casa habitación" : "Terreno destinado a casa habitación"}
            source="Escritura"
          />
          <ReadOnlyField
            label="Régimen de condominio"
            value={expediente.config.condominiumRegime ? "Sí" : "No aplica"}
            source="Régimen de condominio"
          />
        </div>
      </div>
    </Card>
  );
}
