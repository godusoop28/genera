"use client";

import { ParticipantFields } from "@/components/expediente/ParticipantFields";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { useToast } from "@/components/ui/Toast";
import { addParticipant, getRequirements, removeParticipant, updateParticipant } from "@/lib/api/expedientes";
import { getExpedienteExtractedFields } from "@/lib/api/extraction";
import type { BackendParticipantRole, ParticipantDetails, ParticipantRequest, ParticipantResponse, RequirementResponse } from "@/lib/api/types";
import { buildDetectedData, type Detected, type DetectedData } from "@/lib/detected-data";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { errorText } from "@/lib/errors";
import { civilStatusLabels, companyOwnerLabel, formatDate, idDocumentLabels, label, participantRoleLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

function toRequest(p: ParticipantResponse): ParticipantRequest {
  const { id: _id, ordinal: _ordinal, ...rest } = p;
  void _id;
  void _ordinal;
  return rest;
}

/** Datos que el contrato necesita de cada participante (para avisar qué falta). */
function missingFields(p: ParticipantResponse, isCompany: boolean): string[] {
  const missing: string[] = [];
  if (!isCompany) {
    if (!p.nationality) missing.push("nacionalidad");
    if (!p.idDocumentType || !p.idDocumentNumber) missing.push("identificación");
    if (!p.idDocumentIssuer) missing.push("autoridad emisora");
    if (!p.birthDate) missing.push("fecha de nacimiento");
  }
  if (p.role === "OWNER" || p.role === "CO_OWNER") {
    if (!isCompany && !p.civilStatus) missing.push("estado civil");
    if (!p.rfc) missing.push("RFC");
    if (!p.address) missing.push("domicilio");
  }
  return missing;
}

export function ParticipantsTab({ expediente, participants, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const editable = can("EXPEDIENT_EDIT") && expediente.correctable;
  const [requirements, setRequirements] = useState<RequirementResponse[]>([]);
  const [editing, setEditing] = useState<{ id: string | null; value: ParticipantRequest } | null>(null);
  const [removing, setRemoving] = useState<ParticipantResponse | null>(null);
  const [detected, setDetected] = useState<DetectedData | null>(null);
  const ownerId = participants.find((p) => p.role === "OWNER")?.id ?? null;

  useEffect(() => {
    getRequirements(expediente.id).then(setRequirements).catch(() => undefined);
    getExpedienteExtractedFields(expediente.id)
      .then((obs) => setDetected(buildDetectedData(obs, ownerId)))
      .catch(() => undefined);
  }, [expediente.id, ownerId]);

  const names = Object.fromEntries(participants.map((p) => [p.id, p.fullName]));
  const moral = expediente.personType === "MORAL";

  const addable: { role: BackendParticipantRole; text: string }[] = moral
    ? [{ role: "LEGAL_REPRESENTATIVE", text: "Agregar representante legal" }]
    : [
        { role: "CO_OWNER", text: "Agregar copropietario" },
        ...(expediente.signedByAttorney ? [{ role: "ATTORNEY" as const, text: "Agregar apoderado" }] : []),
      ];

  const save = async (reason: string) => {
    if (!editing) return;
    try {
      if (editing.id) {
        await updateParticipant(expediente.id, editing.id, editing.value, reason);
      } else {
        await addParticipant(expediente.id, editing.value, reason);
      }
      showToast("Participante guardado. Los documentos requeridos se actualizaron.");
      setEditing(null);
      await reload();
    } catch (err) {
      showToast(errorText(err));
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Participantes"
          description="Cada titular, representante o apoderado tiene sus propios datos, documentos y firma. Los datos marcados como faltantes los pide el contrato."
        />
        <ul className="flex flex-col gap-3">
          {participants.map((p) => {
            const isCompany = moral && p.role === "OWNER";
            const missing = missingFields(p, isCompany);
            return (
              <li key={p.id} className="rounded-xl border border-border p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-medium text-obsessed">{p.fullName}</p>
                    <p className="text-xs text-muted">{isCompany ? companyOwnerLabel : participantRoleLabels[p.role]}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {missing.length === 0 ? <Badge tone="success">Datos completos</Badge> : <Badge tone="warning">Faltan datos</Badge>}
                    {editable ? (
                      <Button variant="ghost" size="sm" onClick={() => setEditing({ id: p.id, value: toRequest(p) })}>
                        <Pencil className="h-4 w-4" aria-hidden /> Editar
                      </Button>
                    ) : null}
                    {editable && p.role !== "OWNER" ? (
                      <Button variant="ghost" size="sm" onClick={() => setRemoving(p)}>
                        <Trash2 className="h-4 w-4" aria-hidden /> Quitar
                      </Button>
                    ) : null}
                  </div>
                </div>
                <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-3">
                  {!isCompany ? <Item k="Identificación" v={`${label(idDocumentLabels, p.idDocumentType)} ${p.idDocumentNumber ?? ""}`} /> : null}
                  {!isCompany && (p.role === "OWNER" || p.role === "CO_OWNER") ? <Item k="Estado civil" v={label(civilStatusLabels, p.civilStatus)} /> : null}
                  {!isCompany ? <Item k="Nacimiento" v={formatDate(p.birthDate)} /> : null}
                  <Item k="RFC" v={p.rfc ?? "—"} />
                  <Item k="Correo" v={p.email ?? "—"} />
                  <Item k="Teléfono" v={p.phone ?? "—"} />
                </dl>
                {missing.length > 0 ? <p className="mt-2 text-xs text-warning-text">Falta: {missing.join(", ")}.</p> : null}
              </li>
            );
          })}
        </ul>
        {editable ? (
          <div className="mt-4 flex flex-wrap gap-2">
            {addable.map((a) => (
              <Button key={a.role} variant="secondary" size="sm" onClick={() => setEditing({ id: null, value: { role: a.role, fullName: "" } })}>
                <Plus className="h-4 w-4" aria-hidden /> {a.text}
              </Button>
            ))}
          </div>
        ) : null}
      </Card>

      <Card>
        <CardHeader
          title="Documentos requeridos"
          description="Se calculan con el tipo de cliente, cada participante, el tipo de inmueble y cómo se acredita la propiedad."
        />
        <ul className="flex flex-col gap-2">
          {requirements.map((r) => (
            <li key={r.requirementCode} className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
              <span className="text-obsessed">
                {documentTypeLabel(r.type)}
                {r.participantId && names[r.participantId] ? <span className="text-muted"> — {names[r.participantId]}</span> : null}
              </span>
              <Badge tone={r.required ? "gold" : "neutral"}>{r.required ? "Obligatorio" : r.conditional ? "No aplica en este caso" : "Opcional"}</Badge>
            </li>
          ))}
        </ul>
      </Card>

      {editing ? (
        <ParticipantModal
          title={editing.id ? "Editar participante" : "Agregar participante"}
          value={editing.value}
          personType={expediente.personType}
          detected={editing.id ? detected?.participants[editing.id] : undefined}
          onChange={(value) => setEditing({ ...editing, value })}
          onCancel={() => setEditing(null)}
          onSave={save}
        />
      ) : null}

      <ReasonModal
        open={removing !== null}
        title={`Quitar a ${removing?.fullName ?? ""}`}
        description="Sus documentos dejarán de ser obligatorios y, si había un contrato sin firmar, quedará sin efecto."
        minLength={5}
        danger
        confirmLabel="Quitar"
        onCancel={() => setRemoving(null)}
        onConfirm={async (reason) => {
          if (!removing) return;
          try {
            await removeParticipant(expediente.id, removing.id, reason);
            showToast("Participante quitado.");
            setRemoving(null);
            await reload();
          } catch (err) {
            showToast(errorText(err));
          }
        }}
      />
    </div>
  );
}

function ParticipantModal({
  title,
  value,
  personType,
  detected,
  onChange,
  onCancel,
  onSave,
}: {
  title: string;
  value: ParticipantRequest;
  personType: ExpedienteContext["expediente"]["personType"];
  detected?: Partial<Record<keyof ParticipantDetails, Detected>>;
  onChange: (v: ParticipantRequest) => void;
  onCancel: () => void;
  onSave: (reason: string) => Promise<void>;
}) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  return (
    <Modal
      open
      onClose={onCancel}
      title={title}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onCancel}>
            Cancelar
          </Button>
          <Button
            disabled={busy || !value.fullName.trim()}
            onClick={async () => {
              setBusy(true);
              try {
                await onSave(reason.trim());
              } finally {
                setBusy(false);
              }
            }}
          >
            Guardar
          </Button>
        </>
      }
    >
      <DetectedParticipantData value={value} detected={detected} onChange={onChange} />
      <ParticipantFields value={value} onChange={onChange} personType={personType} detailed />
      <label className="mb-1 mt-4 block text-sm font-medium text-obsessed">Motivo del cambio (queda en la bitácora)</label>
      <input
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="Ej. Captura de datos de la identificación"
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold"
      />
    </Modal>
  );
}

const participantFieldLabels: Partial<Record<keyof ParticipantDetails, string>> = {
  idDocumentType: "Identificación",
  idDocumentNumber: "Número de identificación",
  idDocumentIssuer: "Emitida por",
  nationality: "Nacionalidad",
  birthDate: "Fecha de nacimiento",
  curp: "CURP",
  rfc: "RFC",
  address: "Domicilio",
};

/** Lo que la revisión automática leyó en las identificaciones y constancias de este participante. */
function DetectedParticipantData({
  value,
  detected,
  onChange,
}: {
  value: ParticipantRequest;
  detected?: Partial<Record<keyof ParticipantDetails, Detected>>;
  onChange: (v: ParticipantRequest) => void;
}) {
  const rows = (Object.entries(detected ?? {}) as [keyof ParticipantDetails, Detected][]).filter(
    ([key, d]) => participantFieldLabels[key] && (value[key] ?? "") !== d.value,
  );
  if (rows.length === 0) return null;
  const apply = (entries: [keyof ParticipantDetails, Detected][]) =>
    onChange(entries.reduce((acc, [key, d]) => ({ ...acc, [key]: d.value }), value));
  const empty = rows.filter(([key]) => !value[key]);
  return (
    <div className="mb-4 rounded-xl bg-app-bg p-3 text-sm">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <p className="font-medium text-obsessed">Datos detectados en sus documentos</p>
        {empty.length > 0 ? (
          <Button variant="secondary" size="sm" onClick={() => apply(empty)}>
            Llenar campos vacíos
          </Button>
        ) : null}
      </div>
      <ul className="flex flex-col gap-1">
        {rows.map(([key, d]) => (
          <li key={key} className="flex flex-wrap items-center gap-x-2 text-xs text-muted">
            <span>
              {participantFieldLabels[key]} ({d.source}): <span className="font-medium text-obsessed">{key === "idDocumentType" ? label(idDocumentLabels, d.value) : d.value}</span>
            </span>
            <button type="button" className="font-medium text-dark-gold hover:underline" onClick={() => apply([[key, d]])}>
              Usar
            </button>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-xs text-muted">Revísalos contra el documento antes de guardar.</p>
    </div>
  );
}

function Item({ k, v }: { k: string; v: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{k}</dt>
      <dd className="text-obsessed">{v}</dd>
    </div>
  );
}
