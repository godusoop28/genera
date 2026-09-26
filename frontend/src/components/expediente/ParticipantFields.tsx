"use client";

import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import type { BackendPersonType, ParticipantRequest } from "@/lib/api/types";
import { civilStatusLabels, companyOwnerLabel, idDocumentLabels, maritalRegimeLabels, participantRoleLabels } from "@/lib/labels";

interface ParticipantFieldsProps {
  value: ParticipantRequest;
  onChange: (value: ParticipantRequest) => void;
  personType: BackendPersonType;
  /** Muestra todos los datos que pide el contrato (identificación, nacimiento, RFC, domicilio...). */
  detailed?: boolean;
}

const emptyOption = { label: "Selecciona…", value: "" };

/**
 * Campos de un participante. Se adaptan al tipo: la empresa titular (persona
 * moral) no tiene estado civil ni identificación personal; el estado civil
 * solo aplica a titulares persona física.
 */
export function ParticipantFields({ value, onChange, personType, detailed = false }: ParticipantFieldsProps) {
  const isCompany = personType === "MORAL" && value.role === "OWNER";
  const isOwner = value.role === "OWNER" || value.role === "CO_OWNER";
  const set = <K extends keyof ParticipantRequest>(key: K, v: ParticipantRequest[K]) => onChange({ ...value, [key]: v });
  const text = (key: keyof ParticipantRequest) => (value[key] as string | null | undefined) ?? "";

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Input
        label={isCompany ? "Razón social" : "Nombre completo (como aparece en su identificación)"}
        value={value.fullName}
        onChange={(e) => set("fullName", e.target.value)}
        placeholder={isCompany ? "Ej. Inmobiliaria del Sol, S.A. de C.V." : "Ej. Juan Pérez López"}
        containerClassName="sm:col-span-2"
      />
      <p className="text-xs text-muted sm:col-span-2">
        Rol: {isCompany ? companyOwnerLabel : participantRoleLabels[value.role]}
      </p>

      {!isCompany && isOwner && personType === "FISICA" ? (
        <>
          <Select
            label="Estado civil"
            value={value.civilStatus ?? ""}
            onChange={(e) => set("civilStatus", (e.target.value || null) as ParticipantRequest["civilStatus"])}
            options={[emptyOption, ...Object.entries(civilStatusLabels).map(([v, l]) => ({ value: v, label: l }))]}
          />
          {value.civilStatus === "CASADO" ? (
            <Select
              label="Régimen matrimonial"
              value={value.maritalRegime ?? ""}
              onChange={(e) => set("maritalRegime", (e.target.value || null) as ParticipantRequest["maritalRegime"])}
              options={[emptyOption, ...Object.entries(maritalRegimeLabels).map(([v, l]) => ({ value: v, label: l }))]}
            />
          ) : (
            <span className="hidden sm:block" />
          )}
        </>
      ) : null}

      <Input label="Correo electrónico" type="email" value={text("email")} onChange={(e) => set("email", e.target.value)} />
      <Input label="Teléfono" type="tel" value={text("phone")} onChange={(e) => set("phone", e.target.value)} />

      {detailed ? (
        <>
          {!isCompany ? (
            <>
              <Input label="Nacionalidad" value={text("nationality")} onChange={(e) => set("nationality", e.target.value)} placeholder="Mexicana" />
              <Input label="Fecha de nacimiento" type="date" value={text("birthDate")} onChange={(e) => set("birthDate", e.target.value || null)} />
              <Select
                label="Identificación oficial"
                value={value.idDocumentType ?? ""}
                onChange={(e) => set("idDocumentType", (e.target.value || null) as ParticipantRequest["idDocumentType"])}
                options={[emptyOption, ...Object.entries(idDocumentLabels).map(([v, l]) => ({ value: v, label: l }))]}
              />
              <Input label="Folio de la identificación" value={text("idDocumentNumber")} onChange={(e) => set("idDocumentNumber", e.target.value)} />
              <Input
                label="Autoridad que la emitió"
                value={text("idDocumentIssuer")}
                onChange={(e) => set("idDocumentIssuer", e.target.value)}
                placeholder="Instituto Nacional Electoral"
              />
              <Input label="CURP" value={text("curp")} onChange={(e) => set("curp", e.target.value)} />
            </>
          ) : null}
          <Input label="RFC" value={text("rfc")} onChange={(e) => set("rfc", e.target.value)} />
          <Input
            label={isCompany ? "Domicilio fiscal" : "Domicilio particular"}
            value={text("address")}
            onChange={(e) => set("address", e.target.value)}
            containerClassName={isCompany ? "" : "sm:col-span-2"}
          />
        </>
      ) : null}
    </div>
  );
}
