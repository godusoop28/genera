"use client";

import { ApplicabilityModal } from "@/components/documents/ApplicabilityModal";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import {
  listDocuments,
  markNotApplicable,
  requestAgain,
  setDocumentDeferred,
} from "@/lib/api/documents";
import type { DocumentResponse } from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { errorText } from "@/lib/errors";
import { participantRoleLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

/**
 * Antes de mandar la liga, el asesor elige qué documentos pide en la primera
 * entrega y cuáles puede subir el cliente después, cuando los tenga. Los de
 * después no impiden que el cliente envíe, pero siguen siendo obligatorios
 * para aprobar la documentación y generar el contrato.
 */
export function DocumentTimingCard({
  expediente,
  participants,
}: Pick<ExpedienteContext, "expediente" | "participants">) {
  const { showToast } = useToast();
  const can = useCan();
  const canMarkNotApplicable = can("DOCUMENT_MARK_NOT_APPLICABLE");
  const [documents, setDocuments] = useState<DocumentResponse[] | null>(null);
  const [saving, setSaving] = useState<string | null>(null);
  // CC#46: al no pedirlo ahora se pregunta si aplica (se entrega después) o no aplica (con motivo).
  const [deferring, setDeferring] = useState<DocumentResponse | null>(null);

  useEffect(() => {
    listDocuments(expediente.id)
      .then(setDocuments)
      .catch((err) => showToast(errorText(err)));
  }, [expediente.id, showToast]);

  if (documents === null) {
    return (
      <Card>
        <CardHeader
          title="¿Qué documentos pides primero?"
          description="Cargando requisitos…"
        />
      </Card>
    );
  }

  // Solo lo que todavía se le puede pedir al cliente: obligatorio y sin resolver.
  const open = documents.filter(
    (d) =>
      d.required && d.status !== "ACCEPTED" && d.status !== "NOT_APPLICABLE",
  );
  // CC#46: los que se marcaron «No aplica» se muestran aparte, con su motivo, y se pueden corregir.
  const notApplicable = documents.filter(
    (d) => d.required && d.status === "NOT_APPLICABLE",
  );
  if (open.length === 0 && notApplicable.length === 0) return null;

  const names = Object.fromEntries(participants.map((p) => [p.id, p]));
  const groups = [
    ...[...participants]
      .sort((a, b) => a.ordinal - b.ordinal)
      .map((p) => ({
        key: p.id,
        title: `${p.fullName} · ${participantRoleLabels[p.role]}`,
        docs: open.filter((d) => d.participantId === p.id),
      })),
    {
      key: "inmueble",
      title:
        expediente.personType === "MORAL" ? "Inmueble y empresa" : "Inmueble",
      docs: open.filter((d) => !d.participantId || !names[d.participantId]),
    },
  ].filter((g) => g.docs.length > 0);
  const laterCount = open.filter((d) => d.deferred).length;

  const change = async (
    docs: DocumentResponse[],
    deferred: boolean,
    reason?: string,
  ) => {
    const targets = docs.filter((d) => d.deferred !== deferred);
    if (targets.length === 0) return;
    setSaving(targets.length === 1 ? targets[0].id : "all");
    try {
      const updated = await Promise.all(
        targets.map((d) => setDocumentDeferred(d.id, deferred, reason)),
      );
      const byId = Object.fromEntries(updated.map((d) => [d.id, d]));
      setDocuments((prev) => prev?.map((d) => byId[d.id] ?? d) ?? null);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(null);
    }
  };

  const replace = (updated: DocumentResponse) =>
    setDocuments(
      (prev) => prev?.map((d) => (d.id === updated.id ? updated : d)) ?? null,
    );

  const markNa = async (doc: DocumentResponse, justification: string) => {
    setSaving(doc.id);
    try {
      replace(await markNotApplicable(doc.id, justification));
      showToast(
        `"${doc.customName ?? documentTypeLabel(doc.type)}" quedó como No aplica: ya no se le pedirá al cliente.`,
      );
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(null);
    }
  };

  const applyAgain = async (doc: DocumentResponse) => {
    setSaving(doc.id);
    try {
      replace(await requestAgain(doc.id));
      showToast(
        "El documento vuelve a pedirse; el cambio quedó en la bitácora.",
      );
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
          <strong className="text-obsessed">{open.length - laterCount}</strong>{" "}
          se piden ahora ·{" "}
          <strong className="text-obsessed">{laterCount}</strong> para después
        </span>
        <button
          type="button"
          className="text-xs text-dark-gold hover:underline disabled:opacity-50"
          disabled={saving !== null}
          onClick={() => change(open, false)}
        >
          Pedir todos ahora
        </button>
      </div>
      <div className="flex flex-col gap-5">
        {groups.map((group) => (
          <fieldset key={group.key}>
            <legend className="mb-2 break-words text-sm font-semibold text-obsessed">
              {group.title}
            </legend>
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
                        onChange={(e) =>
                          e.target.checked
                            ? change([doc], false)
                            : setDeferring(doc)
                        }
                      />
                      <span className="min-w-0 flex-1 break-words text-sm text-obsessed">
                        {doc.customName ?? documentTypeLabel(doc.type)}
                        {doc.deferred && doc.deferralReason ? (
                          <span className="block text-xs text-muted">
                            Razón: {doc.deferralReason}
                          </span>
                        ) : null}
                      </span>
                      <span
                        className={cn(
                          "shrink-0 text-xs font-medium",
                          now ? "text-dark-gold" : "text-muted",
                        )}
                      >
                        {now ? "Ahora" : "Después"}
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          </fieldset>
        ))}
      </div>
      {notApplicable.length > 0 ? (
        <div className="mt-5 border-t border-border pt-4">
          <p className="mb-2 text-sm font-semibold text-obsessed">
            No aplican a este expediente
          </p>
          <ul className="flex flex-col gap-2">
            {notApplicable.map((doc) => (
              <li
                key={doc.id}
                className="flex flex-wrap items-center gap-3 rounded-xl border border-border px-3 py-2.5"
              >
                <span className="min-w-0 flex-1 break-words text-sm text-obsessed">
                  {doc.customName ?? documentTypeLabel(doc.type)}
                  {doc.participantId && names[doc.participantId] ? (
                    <span className="text-muted">
                      {" "}
                      · {names[doc.participantId].fullName}
                    </span>
                  ) : null}
                  <span className="block text-xs text-muted">
                    No aplica: {doc.notApplicableJustification}
                  </span>
                </span>
                {canMarkNotApplicable ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={saving !== null}
                    onClick={() => applyAgain(doc)}
                  >
                    Sí aplica: volver a pedir
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {deferring ? (
        <ApplicabilityModal
          key={deferring.id}
          open
          documentName={
            deferring
              ? (deferring.customName ?? documentTypeLabel(deferring.type))
              : ""
          }
          canMarkNotApplicable={canMarkNotApplicable}
          notApplicableBlockedReason={
            deferring?.type === "INE"
              ? "La identificación no admite «No aplica»: si no tiene INE, se carga otra identificación oficial vigente."
              : null
          }
          onCancel={() => setDeferring(null)}
          onConfirm={async (choice) => {
            const doc = deferring;
            setDeferring(null);
            if (!doc) return;
            if (choice.kind === "LATER")
              await change([doc], true, choice.reason);
            else await markNa(doc, choice.justification);
          }}
        />
      ) : null}
    </Card>
  );
}
