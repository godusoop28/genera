"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { addAdditionalDeed } from "@/lib/api/documents";
import type {
  AdditionalDeed,
  DocumentResponse,
  ExpedienteObservationResponse,
  LegalDetails,
} from "@/lib/api/types";
import { detectedForDeedDocument, type Detected } from "@/lib/detected-data";
import { errorText } from "@/lib/errors";
import { Plus } from "lucide-react";
import { useState, type ReactNode } from "react";

type DeedKey =
  | "number"
  | "date"
  | "notaryName"
  | "notaryNumber"
  | "notaryPlace"
  | "registryData"
  | "registryDate";

const FIELDS: { key: DeedKey; label: string; type?: string; hint?: string }[] =
  [
    { key: "number", label: "Número de escritura" },
    { key: "date", label: "Fecha de la escritura", type: "date" },
    { key: "notaryName", label: "Nombre del notario" },
    { key: "notaryNumber", label: "Número de notaría" },
    {
      key: "notaryPlace",
      label: "Lugar de la notaría",
      hint: "Ej. Cuernavaca, Morelos",
    },
    { key: "registryData", label: "Datos registrales / folio real (opcional)" },
    {
      key: "registryDate",
      label: "Fecha de inscripción en el RPP (opcional)",
      type: "date",
    },
  ];

/** Escrituras adicionales: documentos de tipo escritura agregados con su propio nombre. */
export function additionalDeedDocuments(
  documents: DocumentResponse[] | null,
): DocumentResponse[] {
  return (documents ?? []).filter(
    (d) => d.type === "DEED" && Boolean(d.customName),
  );
}

/**
 * Control de Cambios #48: la propiedad puede acreditarse con varias escrituras (varios terrenos y/o
 * la construcción). La principal conserva su captura; cada escritura adicional es un documento
 * propio con sus datos, ligada a su documento de origen, y se elige si va en el contrato. Lo
 * detectado por la IA se ofrece solo desde el documento de esa misma escritura.
 */
export function DeedsSection({
  expedienteId,
  legal,
  setLegal,
  editable,
  documents,
  observations,
  mainDeedFields,
  onDocumentsChanged,
}: {
  expedienteId: string;
  legal: LegalDetails;
  setLegal: (updater: (current: LegalDetails) => LegalDetails) => void;
  editable: boolean;
  documents: DocumentResponse[] | null;
  observations: ExpedienteObservationResponse[];
  /** Campos de la escritura principal (los que ya existían). */
  mainDeedFields: ReactNode;
  onDocumentsChanged: () => void;
}) {
  const { showToast } = useToast();
  const [adding, setAdding] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const extraDocs = additionalDeedDocuments(documents);
  const several = extraDocs.length > 0;
  const entries = legal.additionalDeeds ?? [];

  const entryFor = (documentId: string): AdditionalDeed =>
    entries.find((e) => e.documentId === documentId) ?? { documentId };
  const updateEntry = (documentId: string, patch: Partial<AdditionalDeed>) =>
    setLegal((current) => {
      const list = current.additionalDeeds ?? [];
      const exists = list.some((e) => e.documentId === documentId);
      return {
        ...current,
        additionalDeeds: exists
          ? list.map((e) =>
              e.documentId === documentId ? { ...e, ...patch } : e,
            )
          : [...list, { documentId, ...patch }],
      };
    });

  const add = async () => {
    const name = adding?.trim() ?? "";
    if (name.length < 3) return;
    setBusy(true);
    try {
      await addAdditionalDeed(expedienteId, name);
      showToast(
        `"${name}" se agregó a los documentos del expediente: súbela ahí o pídesela al cliente en su liga.`,
      );
      setAdding(null);
      onDocumentsChanged();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader
        title={
          several
            ? "Escrituras con las que se acredita la propiedad (declaración II.d)"
            : "Escritura con la que se acredita la propiedad (declaración II.d)"
        }
        description={
          several
            ? "Cada escritura aparece en el contrato como una entrada separada, con sus propios datos. Revisa la lista y marca cuáles corresponden a esta operación."
            : "Si la propiedad comprende varios terrenos o la construcción tiene otra escritura, agrégala para que cada una aparezca en el contrato."
        }
      />
      {several ? (
        <p className="mb-2 text-sm font-semibold text-obsessed">
          Escritura 1 (la del listado de documentos)
        </p>
      ) : null}
      <div className="grid gap-4 sm:grid-cols-2">
        {mainDeedFields}
        {several ? (
          <>
            <Input
              label="¿Qué terreno o parte ampara? (opcional)"
              value={legal.deedLabel ?? ""}
              disabled={!editable}
              placeholder="Ej. Terreno 1 (lote 4, manzana 2)"
              onChange={(e) =>
                setLegal((c) => ({ ...c, deedLabel: e.target.value }))
              }
            />
            <label className="flex items-center gap-2 self-end pb-2 text-sm text-obsessed">
              <input
                type="checkbox"
                className="h-4 w-4 accent-dark-gold"
                disabled={!editable}
                checked={Boolean(legal.deedIncludesConstruction)}
                onChange={(e) =>
                  setLegal((c) => ({
                    ...c,
                    deedIncludesConstruction: e.target.checked,
                  }))
                }
              />
              Incluye la construcción
            </label>
          </>
        ) : null}
      </div>

      {extraDocs.map((doc, i) => {
        const entry = entryFor(doc.id);
        const detected = detectedForDeedDocument(
          observations,
          doc.id,
          doc.customName ?? "Escritura",
        );
        const included = entry.includeInContract !== false;
        return (
          <div key={doc.id} className="mt-6 border-t border-border pt-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-semibold text-obsessed">
                Escritura {i + 2}: {doc.customName}
                <span className="ml-2 text-xs font-normal text-muted">
                  {doc.currentVersionNumber > 0
                    ? "documento cargado"
                    : "documento pendiente de cargar"}
                </span>
              </p>
              <label className="flex items-center gap-2 text-sm text-obsessed">
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-dark-gold"
                  disabled={!editable}
                  checked={included}
                  onChange={(e) =>
                    updateEntry(doc.id, { includeInContract: e.target.checked })
                  }
                />
                Incluir en el contrato
              </label>
            </div>
            <div
              className={`grid gap-4 sm:grid-cols-2 ${included ? "" : "opacity-60"}`}
            >
              <Input
                label="¿Qué terreno o parte ampara?"
                value={entry.label ?? ""}
                disabled={!editable}
                placeholder="Ej. Terreno 2 (lote 5) o Construcción"
                onChange={(e) => updateEntry(doc.id, { label: e.target.value })}
              />
              <label className="flex items-center gap-2 self-end pb-2 text-sm text-obsessed">
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-dark-gold"
                  disabled={!editable}
                  checked={Boolean(entry.includesConstruction)}
                  onChange={(e) =>
                    updateEntry(doc.id, {
                      includesConstruction: e.target.checked,
                    })
                  }
                />
                Incluye la construcción
              </label>
              {FIELDS.map((f) => (
                <DeedField
                  key={f.key}
                  label={f.label}
                  type={f.type}
                  hint={f.hint}
                  disabled={!editable}
                  value={(entry[f.key] as string | null | undefined) ?? ""}
                  detected={detected[f.key]}
                  onChange={(v) =>
                    updateEntry(doc.id, {
                      [f.key]: f.type === "date" ? v || null : v,
                    })
                  }
                />
              ))}
            </div>
          </div>
        );
      })}

      {editable ? (
        <div className="mt-5">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setAdding(`Escritura ${extraDocs.length + 2}`)}
          >
            <Plus className="h-4 w-4" aria-hidden /> Agregar otra escritura
          </Button>
        </div>
      ) : null}

      <Modal
        open={adding !== null}
        onClose={() => setAdding(null)}
        title="Agregar otra escritura"
        footer={
          <>
            <Button
              variant="ghost"
              onClick={() => setAdding(null)}
              disabled={busy}
            >
              Cancelar
            </Button>
            <Button
              onClick={add}
              disabled={busy || (adding?.trim().length ?? 0) < 3}
            >
              Agregar
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-3 text-sm">
          <p className="text-muted">
            Se agrega como un documento independiente del expediente (no
            reemplaza a las escrituras ya cargadas). Ponle un nombre que la
            identifique; después súbela en Documentos o pídesela al cliente en
            su liga.
          </p>
          <Input
            label="Nombre"
            value={adding ?? ""}
            autoFocus
            maxLength={120}
            onChange={(e) => setAdding(e.target.value)}
            placeholder="Ej. Escritura — Terreno 2 (lote 5)"
          />
        </div>
      </Modal>
    </Card>
  );
}

function DeedField({
  label,
  value,
  onChange,
  type = "text",
  hint,
  disabled,
  detected,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  hint?: string;
  disabled: boolean;
  detected?: Detected;
}) {
  const differs = detected !== undefined && detected.value !== value;
  return (
    <div>
      <Input
        label={label}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        hint={hint}
      />
      {detected && differs ? (
        <p className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-muted">
          <span>
            Detectado en {detected.source}:{" "}
            <span className="font-medium text-obsessed">{detected.value}</span>
          </span>
          {!disabled ? (
            <button
              type="button"
              className="font-medium text-dark-gold hover:underline"
              onClick={() => onChange(detected.value)}
            >
              Usar este dato
            </button>
          ) : null}
        </p>
      ) : null}
    </div>
  );
}
