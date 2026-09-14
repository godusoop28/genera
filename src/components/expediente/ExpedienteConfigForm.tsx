"use client";

import { Card, CardHeader } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import type { ExpedienteConfig } from "@/types/expediente";

interface ExpedienteConfigFormProps {
  config: ExpedienteConfig;
  onChange: <K extends keyof ExpedienteConfig>(key: K, value: ExpedienteConfig[K]) => void;
}

export function ExpedienteConfigForm({ config, onChange }: ExpedienteConfigFormProps) {
  return (
    <Card>
      <CardHeader
        title="Configuración del expediente"
        description="Selecciona las características del inmueble y del propietario para generar la lista de documentos."
      />

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <Select
          label="Contrato"
          value={config.contractType}
          onChange={(e) => onChange("contractType", e.target.value as ExpedienteConfig["contractType"])}
          options={[
            { label: "Compraventa", value: "Compraventa" },
            { label: "Arrendamiento", value: "Arrendamiento" },
            { label: "Otro", value: "Otro" },
          ]}
        />

        <Select
          label="Número de propietarios"
          value={String(config.ownerCount)}
          onChange={(e) => onChange("ownerCount", Number(e.target.value) as ExpedienteConfig["ownerCount"])}
          options={[
            { label: "1 propietario", value: "1" },
            { label: "2 propietarios", value: "2" },
            { label: "3 propietarios", value: "3" },
          ]}
        />

        <Select
          label="Acreditación del inmueble"
          value={config.accreditation}
          onChange={(e) =>
            onChange("accreditation", e.target.value as ExpedienteConfig["accreditation"])
          }
          options={[
            { label: "Escritura pública", value: "Escritura pública" },
            { label: "Contrato privado", value: "Contrato privado" },
            { label: "Otro", value: "Otro" },
          ]}
        />

        <Select
          label="Tipo de persona"
          value={config.personType}
          onChange={(e) => onChange("personType", e.target.value as ExpedienteConfig["personType"])}
          options={[
            { label: "Persona física", value: "Persona física" },
            { label: "Persona moral", value: "Persona moral" },
          ]}
        />

        <Select
          label="Estatus jurídico del inmueble"
          value={config.legalStatus}
          onChange={(e) => onChange("legalStatus", e.target.value as ExpedienteConfig["legalStatus"])}
          options={[
            { label: "Libre de gravamen", value: "Libre de gravamen" },
            { label: "Con gravamen", value: "Con gravamen" },
            { label: "En proceso", value: "En proceso" },
          ]}
          containerClassName="sm:col-span-2"
        />
      </div>

      <div className="mt-6 space-y-4 border-t border-border pt-5">
        <Toggle
          id="condominium-toggle"
          checked={config.condominiumRegime}
          onChange={(checked) => onChange("condominiumRegime", checked)}
          label="El inmueble se encuentra en régimen de condominio"
        />
        <Toggle
          id="representative-toggle"
          checked={config.signedByRepresentative}
          onChange={(checked) => onChange("signedByRepresentative", checked)}
          label="¿Firma mediante representante/apoderado?"
        />
      </div>
    </Card>
  );
}
