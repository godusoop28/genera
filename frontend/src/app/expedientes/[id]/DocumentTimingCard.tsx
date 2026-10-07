"use client";

import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { listDocuments, setDocumentDeferred } from "@/lib/api/documents";
import type { DocumentResponse } from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { errorText } from "@/lib/errors";
import { participantRoleLabels } from "@/lib/labels";
import { cn } from "@/lib/utils";
import { useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

/**
 * Antes de mandar la liga, el asesor elige qué documentos pide en la primera
 * entrega y cuáles puede subir el cliente después, cuando los tenga. Los de
 * después no impiden que el cliente envíe, pero siguen siendo obligatorios
 * para aprobar la documentación y generar el contrato.
 */
export function DocumentTimingCard({ expediente, participants }: Pick<ExpedienteContext, "expediente" | "participants">) {
  const { showToast } = useToast();
  const [documents, setDocuments] = useState<DocumentResponse[] | null>(null);
  const [saving, setSaving] = useState<string | null>(null);

  useEffect(() => {
    listDocuments(expediente.id)
      .then(setDocuments)
      .catch((err) => showToast(errorText(err)));
  }, [expediente.id, showToast]);

  if (documents === null) {
    return (
      <Card>
        <CardHeader title="¿Qué documentos pides primero?" description="Cargando requisitos…" />
      </Card>
    );
  }

  // Solo lo que todavía se le puede pedir al cliente: obligatorio y sin resolver.
  const open = documents.filter((d) => d.required && d.status !== "ACCEPTED" && d.status !== "NOT_APPLICABLE");
  if (open.length === 0) return null;

  const names = Object.fromEntries(participants.map((p) => [p.id, p]));
  const groups = [
    ...[...participants]
      .sort((a, b) => a.ordinal - b.ordinal)
      .map((p) => ({ key: p.id, title: `${p.fullName} · ${participantRoleLabels[p.role]}`, docs: open.filter((d) => d.participantId === p.id) })),
    { key: "inmueble", title: expediente.personType === "MORAL" ? "Inmueble y empresa" : "Inmueble", docs: open.filter((d) => !d.participantId || !names[d.participantId]) },
  ].filter((g) => g.docs.length > 0);
  const laterCount = open.filter((d) => d.deferred).length;

  const change = async (docs: DocumentResponse[], deferred: boolean) => {
    const targets = docs.filter((d) => d.deferred !== deferred);
    if (targets.length === 0) return;
    setSaving(targets.length === 1 ? targets[0].id : "all");
    try {
      const updated = await Promise.all(targets.map((d) => setDocumentDeferred(d.id, deferred)));
      const byId = Object.fromEntries(updated.map((d) => [d.id, d]));
      setDocuments((prev) => prev?.map((d) => byId[d.id] ?? d) ?? null);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(null);
    }
  };

  return (
    <Card>
      <CardHeader
        title="¿Qué documentos pides primero?"
        description="Marca lo que necesitas en la primera entrega. Lo demás el cliente lo podrá subir después desde su misma liga, cuando lo tenga; se sigue necesitando para aprobar la documentación y generar el contrato."
      />
      <div className="mb-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        <span className="text-muted">
          <strong className="text-obsessed">{open.length - laterCount}</strong> se piden ahora · <strong className="text-obsessed">{laterCount}</strong> para
          después
        </span>
        <button type="button" className="text-xs text-dark-gold hover:underline disabled:opacity-50" disabled={saving !== null} onClick={() => change(open, false)}>
          Pedir todos ahora
        </button>
      </div>
      <div className="flex flex-col gap-5">
        {groups.map((group) => (
          <fieldset key={group.key}>
            <legend className="mb-2 break-words text-sm font-semibold text-obsessed">{group.title}</legend>
            <ul className="flex flex-col gap-2">
              {group.docs.map((doc) => {
                const now = !doc.deferred;
                return (
                  <li key={doc.id}>
                    <label
                      className={cn(
                        "flex cursor-pointer items-center gap-3 rounded-xl border px-3 py-2.5 transition-colors duration-150",
                        now ? "border-gold/60 bg-gold/5" : "border-border",
                        saving !== null && "cursor-wait opacity-70",
                      )}
                    >
                      <input
                        type="checkbox"
                        className="h-4 w-4 shrink-0 accent-dark-gold"
                        checked={now}
                        disabled={saving !== null}
                        onChange={(e) => change([doc], !e.target.checked)}
                      />
                      <span className="min-w-0 flex-1 break-words text-sm text-obsessed">{documentTypeLabel(doc.type)}</span>
                      <span className={cn("shrink-0 text-xs font-medium", now ? "text-dark-gold" : "text-muted")}>{now ? "Ahora" : "Después"}</span>
                    </label>
                  </li>
                );
              })}
            </ul>
          </fieldset>
        ))}
      </div>
    </Card>
  );
}
