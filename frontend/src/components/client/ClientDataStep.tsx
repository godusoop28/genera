"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import { useDemoApp } from "@/context/DemoAppProvider";
import type { CivilStatus, Expediente, ManualClientData } from "@/types/expediente";
import { useState } from "react";

const civilStatusOptions: CivilStatus[] = [
  "Soltero(a)",
  "Casado(a)",
  "Unión libre",
  "Divorciado(a)",
  "Viudo(a)",
];

export function ClientDataStep({ expediente, onContinue }: { expediente: Expediente; onContinue: () => void }) {
  const { updateExpediente } = useDemoApp();
  const [data, setData] = useState<ManualClientData>(expediente.manualData);

  const set = <K extends keyof ManualClientData>(key: K, value: ManualClientData[K]) =>
    setData((prev) => ({ ...prev, [key]: value }));

  const handleContinue = () => {
    updateExpediente(expediente.id, (current) => ({ ...current, manualData: data }));
    onContinue();
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader title="Datos del cliente" description="Información declarada por el propietario." />
        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Estado civil"
            value={data.civilStatus ?? ""}
            onChange={(e) => set("civilStatus", e.target.value as CivilStatus)}
            options={[{ label: "Selecciona una opción", value: "" }, ...civilStatusOptions.map((s) => ({ label: s, value: s }))]}
          />
          <Input
            label="Precio autorizado de venta"
            type="number"
            suffix="MXN"
            value={data.authorizedPrice ?? ""}
            onChange={(e) => set("authorizedPrice", e.target.value ? Number(e.target.value) : undefined)}
          />
          <Input
            label="Correo electrónico"
            type="email"
            value={data.email ?? ""}
            onChange={(e) => set("email", e.target.value)}
          />
          <Input
            label="Teléfono"
            value={data.phone ?? ""}
            onChange={(e) => set("phone", e.target.value)}
          />
          <Input
            label="Domicilio para notificaciones (si es distinto)"
            value={data.notificationAddress ?? ""}
            onChange={(e) => set("notificationAddress", e.target.value)}
            containerClassName="sm:col-span-2"
          />
          <Input
            label="Instrucciones para visitas"
            value={data.visitInstructions ?? ""}
            onChange={(e) => set("visitInstructions", e.target.value)}
            containerClassName="sm:col-span-2"
          />
          <Input
            label="Servicios adicionales solicitados"
            value={data.additionalServicesRequested ?? ""}
            onChange={(e) => set("additionalServicesRequested", e.target.value)}
            containerClassName="sm:col-span-2"
          />
        </div>
        <div className="mt-4 flex flex-col gap-1 border-t border-border pt-4">
          <Toggle
            label="Autorizo uso publicitario de mis datos"
            checked={Boolean(data.marketingDataAuthorized)}
            onChange={(v) => set("marketingDataAuthorized", v)}
          />
          <Toggle
            label="Autorizo recibir publicidad"
            checked={Boolean(data.receiveAdsAuthorized)}
            onChange={(v) => set("receiveAdsAuthorized", v)}
          />
        </div>
      </Card>

      <Card>
        <CardHeader title="Características de la propiedad" description="Información general del inmueble." />
        <div className="grid gap-4 sm:grid-cols-3">
          <Input
            label="Número de recámaras"
            type="number"
            value={data.bedrooms ?? ""}
            onChange={(e) => set("bedrooms", e.target.value ? Number(e.target.value) : undefined)}
          />
          <Input
            label="Número de baños"
            type="number"
            value={data.bathrooms ?? ""}
            onChange={(e) => set("bathrooms", e.target.value ? Number(e.target.value) : undefined)}
          />
          <Input
            label="Estacionamientos"
            type="number"
            value={data.parkingSpots ?? ""}
            onChange={(e) => set("parkingSpots", e.target.value ? Number(e.target.value) : undefined)}
          />
          <Input
            label="Estado general de conservación"
            value={data.conservationStatus ?? ""}
            onChange={(e) => set("conservationStatus", e.target.value)}
            containerClassName="sm:col-span-3"
          />
          <Input
            label="Servicios disponibles"
            value={data.availableServices ?? ""}
            onChange={(e) => set("availableServices", e.target.value)}
            containerClassName="sm:col-span-3"
          />
          <Input
            label="Características relevantes"
            value={data.relevantFeatures ?? ""}
            onChange={(e) => set("relevantFeatures", e.target.value)}
            containerClassName="sm:col-span-3"
          />
        </div>
      </Card>

      <div className="flex justify-end">
        <Button size="lg" onClick={handleContinue}>
          Continuar
        </Button>
      </div>
    </div>
  );
}
