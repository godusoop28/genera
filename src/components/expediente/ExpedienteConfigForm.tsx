"use client";

import { Badge } from "@/components/ui/Badge";
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

      <div className="mb-5 flex flex-col gap-2 rounded-xl border border-border p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm font-medium text-obsessed">
            Intermediación para compraventa de inmueble destinado a casa habitación
          </p>
          <Badge tone="gold" className="mt-1.5">PROFECO 7/002193-2026</Badge>
        </div>
      </div>
      <div className="mb-6 flex flex-wrap gap-2">
        <Badge tone="neutral">Arrendamiento — Próximamente</Badge>
        <Badge tone="neutral">Otro — Próximamente</Badge>
      </div>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
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
          label="Tipo de persona"
          value={config.personType}
          onChange={(e) => onChange("personType", e.target.value as ExpedienteConfig["personType"])}
          options={[
            { label: "Persona física", value: "fisica" },
            { label: "Persona moral", value: "moral" },
          ]}
        />

        <Select
          label="Carácter de quien firma"
          value={config.signerCharacter}
          onChange={(e) => onChange("signerCharacter", e.target.value as ExpedienteConfig["signerCharacter"])}
          options={[
            { label: "Propietario", value: "propietario" },
            { label: "Copropietario", value: "copropietario" },
            { label: "Apoderado", value: "apoderado" },
          ]}
        />

        <Select
          label="Acreditación del inmueble"
          value={config.accreditation}
          onChange={(e) => onChange("accreditation", e.target.value as ExpedienteConfig["accreditation"])}
          options={[
            { label: "Escritura pública", value: "escritura_publica" },
            { label: "Contrato privado", value: "contrato_privado" },
          ]}
        />

        <Select
          label="Tipo de inmueble"
          value={config.propertyType}
          onChange={(e) => onChange("propertyType", e.target.value as ExpedienteConfig["propertyType"])}
          options={[
            { label: "Vivienda destinada a casa habitación", value: "vivienda" },
            { label: "Terreno destinado a casa habitación", value: "terreno" },
          ]}
        />

        <Select
          label="Situación jurídica declarada (preliminar)"
          value={config.legalStatus}
          onChange={(e) => onChange("legalStatus", e.target.value as ExpedienteConfig["legalStatus"])}
          options={[
            { label: "Libre de gravamen", value: "libre_gravamen" },
            { label: "Con gravamen", value: "con_gravamen" },
            { label: "En revisión", value: "en_revision" },
          ]}
        />
      </div>
      <p className="mt-2 text-xs text-muted">
        La situación jurídica declarada aquí es preliminar; se actualizará con el certificado de
        gravamen recibido.
      </p>

      <div className="mt-6 space-y-4 border-t border-border pt-5">
        <Toggle
          id="condominium-toggle"
          checked={config.condominiumRegime}
          onChange={(checked) => onChange("condominiumRegime", checked)}
          label="El inmueble se encuentra en régimen de condominio"
        />
        {config.signerCharacter === "apoderado" ? (
          <p className="text-xs text-dark-gold">
            El poder notarial se solicitará automáticamente por tratarse de un apoderado.
          </p>
        ) : null}
      </div>
    </Card>
  );
}
