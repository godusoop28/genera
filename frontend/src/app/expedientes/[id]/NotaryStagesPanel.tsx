"use client";

import { DocumentViewerModal } from "@/components/documents/DocumentViewerModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { useToast } from "@/components/ui/Toast";
import { requestDocumentUpdate } from "@/lib/api/documents";
import {
  clearOfferAccepted,
  getNotaryOverview,
  registerOfferAccepted,
  sendToNotary,
  setClosingCheck,
  type NotaryOverview,
  type NotaryStageDocument,
  type NotarySubmission,
  type NotarySubmissionKind,
} from "@/lib/api/notary";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, CheckCircle2, Eye, Loader2, Send } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

/**
 * Control de Cambios #23: etapas 2 (envío inicial a notaría) y 3 (cierre, al registrar la oferta
 * aceptada). En cada envío se eligen con casillas los documentos y la versión de cada uno; todo
 * queda en el historial del expediente. El cierre alerta si un documento no está confirmado como
 * vigente y al corriente de pago.
 */
export function NotaryStagesPanel({ expedienteId, names, stamp }: { expedienteId: string; names: Record<string, string>; stamp: string }) {
  const { showToast } = useToast();
  const can = useCan();
  const [overview, setOverview] = useState<NotaryOverview | null>(null);
  const [offerOpen, setOfferOpen] = useState(false);
  const [viewing, setViewing] = useState<{ id: string; title: string } | null>(null);

  const load = useCallback(() => {
    getNotaryOverview(expedienteId)
      .then(setOverview)
      .catch((err) => showToast(errorText(err)));
  }, [expedienteId, showToast]);

  useEffect(() => {
    load();
  }, [load, stamp]);

  const canSend = can("DOCUMENT_EMAIL_SEND");
  const canCheck = can("DOCUMENT_UPLOAD") || can("DOCUMENT_ACCEPT");
  const canOffer = can("EXPEDIENT_EDIT") || can("DOCUMENT_EMAIL_SEND");

  if (!overview) {
    return (
      <Card>
        <p className="flex items-center gap-2 text-sm text-muted">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Cargando etapas de notaría…
        </p>
      </Card>
    );
  }

  const withFiles = overview.documents.filter((d) => d.versions.length > 0);
  const label = (d: NotaryStageDocument) => (d.participantId && names[d.participantId] ? `${d.name} — ${names[d.participantId]}` : d.name);
  const closingDocs = overview.documents.filter((d) => d.closingRequired);

  return (
    <>
      <Card>
        <CardHeader
          title="Etapa 2 · Envío inicial a notaría"
          description="Cuando hay interés formal: elige qué documentos y qué versión mandar para que la notaría abra su expediente."
        />
        {canSend ? (
          <SubmissionBuilder
            kind="INITIAL"
            documents={withFiles}
            label={label}
            defaultSelected={(d) => d.status === "ACCEPTED"}
            onView={setViewing}
            onSent={load}
            expedienteId={expedienteId}
          />
        ) : (
          <p className="text-sm text-muted">Tu usuario no tiene permiso para enviar documentos a notaría.</p>
        )}
        <SubmissionHistory submissions={overview.submissions.filter((s) => s.kind === "INITIAL")} />
      </Card>

      <Card>
        <CardHeader
          title="Etapa 3 · Documentos para cierre"
          description="Con la oferta aceptada se piden los documentos actualizados: vigentes, al corriente de pago y con fecha reciente."
        />
        {!overview.closingActive ? (
          <div className="flex flex-col gap-3 text-sm">
            <p className="rounded-lg bg-app-bg px-3 py-2 text-muted">
              Esta etapa se activa al registrar la oferta aceptada. Hasta entonces solo aplica el envío inicial.
            </p>
            {canOffer ? (
              <div>
                <Button size="sm" onClick={() => setOfferOpen(true)}>
                  <CheckCircle2 className="h-4 w-4" aria-hidden /> Registrar oferta aceptada
                </Button>
              </div>
            ) : null}
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-success-bg px-3 py-2 text-sm text-success-text">
              <span>
                Oferta aceptada registrada el {formatDateTime(overview.offerAcceptedAt)}
                {overview.offerAcceptedByName ? ` por ${overview.offerAcceptedByName}` : ""}
                {overview.offerNote ? ` · ${overview.offerNote}` : ""}
              </span>
              {canOffer ? (
                <button
                  type="button"
                  className="text-xs underline"
                  onClick={async () => {
                    try {
                      setOverview(await clearOfferAccepted(expedienteId));
                    } catch (err) {
                      showToast(errorText(err));
                    }
                  }}
                >
                  Quitar registro
                </button>
              ) : null}
            </div>

            <div>
              <p className="mb-2 text-sm font-medium text-obsessed">Documentos que deben ir actualizados</p>
              {closingDocs.length === 0 ? (
                <p className="text-sm text-muted">Este expediente no tiene documentos de cierre en su listado.</p>
              ) : (
                <ul className="flex flex-col gap-2">
                  {closingDocs.map((d) => (
                    <ClosingDocumentRow key={d.documentId} document={d} label={label(d)} canCheck={canCheck} onChanged={load} onView={setViewing} />
                  ))}
                </ul>
              )}
            </div>

            {canSend ? (
              <SubmissionBuilder
                kind="CLOSING"
                documents={withFiles}
                label={label}
                defaultSelected={(d) => d.closingRequired}
                onView={setViewing}
                onSent={load}
                expedienteId={expedienteId}
              />
            ) : null}
          </div>
        )}
        <SubmissionHistory submissions={overview.submissions.filter((s) => s.kind === "CLOSING")} />
      </Card>

      <ReasonModal
        open={offerOpen}
        title="Registrar oferta aceptada"
        description="Activa la etapa de cierre: se piden los documentos actualizados y se habilita el envío de cierre a la notaría."
        label="Nota (opcional)"
        placeholder="Ej. Oferta de $3,200,000 aceptada el 15/10; escrituración estimada en noviembre."
        minLength={0}
        confirmLabel="Registrar"
        onCancel={() => setOfferOpen(false)}
        onConfirm={async (note) => {
          try {
            setOverview(await registerOfferAccepted(expedienteId, note));
            showToast("Oferta aceptada registrada: la etapa de cierre está activa.");
            setOfferOpen(false);
          } catch (err) {
            showToast(errorText(err));
          }
        }}
      />

      <DocumentViewerModal key={viewing?.id ?? "closed"} versionId={viewing?.id ?? null} title={viewing?.title ?? ""} onClose={() => setViewing(null)} />
    </>
  );
}

function ClosingDocumentRow({
  document: d,
  label,
  canCheck,
  onChanged,
  onView,
}: {
  document: NotaryStageDocument;
  label: string;
  canCheck: boolean;
  onChanged: () => void;
  onView: (v: { id: string; title: string }) => void;
}) {
  const { showToast } = useToast();
  const [mode, setMode] = useState<"invalid" | "update" | null>(null);
  const current = d.versions.find((v) => v.current) ?? d.versions[0];
  const check = async (valid: boolean, note: string) => {
    try {
      await setClosingCheck(d.documentId, valid, note);
      showToast(valid ? "Confirmado como vigente y al corriente." : "Marcado como no vigente.");
      onChanged();
    } catch (err) {
      showToast(errorText(err));
    }
  };
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="font-medium text-obsessed">{label}</span>
        {!current ? (
          <Badge tone="neutral">Sin archivo</Badge>
        ) : d.closingValid ? (
          <Badge tone="success">✅ Vigente y al corriente (V{current.versionNumber})</Badge>
        ) : d.closingCheckValid === false && d.closingCheckedVersion === current.versionNumber ? (
          <Badge tone="danger">⚠️ No vigente / no al corriente</Badge>
        ) : (
          <Badge tone="warning">⚠️ Sin confirmar vigencia</Badge>
        )}
      </div>
      {d.closingCheckedByName ? (
        <p className="text-xs text-muted">
          Revisado por {d.closingCheckedByName} el {formatDateTime(d.closingCheckedAt)} (V{d.closingCheckedVersion ?? "?"})
          {d.closingCheckNote ? ` · ${d.closingCheckNote}` : ""}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {current ? (
          <Button variant="ghost" size="sm" onClick={() => onView({ id: current.id, title: `${label} · versión ${current.versionNumber}` })}>
            <Eye className="h-4 w-4" aria-hidden /> Ver V{current.versionNumber}
          </Button>
        ) : null}
        {canCheck && current && !d.closingValid ? (
          <Button variant="secondary" size="sm" onClick={() => check(true, "")}>
            <CheckCircle2 className="h-4 w-4" aria-hidden /> Confirmar vigente y al corriente
          </Button>
        ) : null}
        {canCheck && current && d.closingCheckValid !== false ? (
          <Button variant="ghost" size="sm" onClick={() => setMode("invalid")}>
            No está vigente…
          </Button>
        ) : null}
        {canCheck && current ? (
          <Button variant="ghost" size="sm" onClick={() => setMode("update")}>
            Pedir versión actualizada…
          </Button>
        ) : null}
      </div>
      <ReasonModal
        open={mode !== null}
        title={mode === "update" ? `Pedir versión actualizada: ${label}` : `${label}: no está vigente`}
        description={
          mode === "update"
            ? "El cliente verá esta nota en su liga y subirá la versión nueva; la actual se conserva."
            : "Queda registrado para el cierre; el envío de cierre te lo advertirá."
        }
        label={mode === "update" ? "¿Qué debe actualizar?" : "Motivo"}
        placeholder={mode === "update" ? "Ej. El predial debe ser del bimestre en curso." : "Ej. Adeuda el 4º bimestre."}
        minLength={mode === "update" ? 10 : 0}
        confirmLabel={mode === "update" ? "Pedir actualización" : "Guardar"}
        onCancel={() => setMode(null)}
        onConfirm={async (note) => {
          if (mode === "update") {
            try {
              await requestDocumentUpdate(d.documentId, note);
              showToast("Se pidió la versión actualizada al cliente.");
              onChanged();
            } catch (err) {
              showToast(errorText(err));
            }
          } else {
            await check(false, note);
          }
          setMode(null);
        }}
      />
    </li>
  );
}

function SubmissionBuilder({
  kind,
  documents,
  label,
  defaultSelected,
  onView,
  onSent,
  expedienteId,
}: {
  kind: NotarySubmissionKind;
  documents: NotaryStageDocument[];
  label: (d: NotaryStageDocument) => string;
  defaultSelected: (d: NotaryStageDocument) => boolean;
  onView: (v: { id: string; title: string }) => void;
  onSent: () => void;
  expedienteId: string;
}) {
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<Record<string, string>>({});
  const [email, setEmail] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const closing = kind === "CLOSING";

  const start = () => {
    const initial: Record<string, string> = {};
    for (const d of documents) {
      const current = d.versions.find((v) => v.current) ?? d.versions[0];
      if (current && defaultSelected(d)) initial[d.documentId] = current.id;
    }
    setSelected(initial);
    setOpen(true);
  };

  // Cierre: documentos de cierre sin confirmar como vigentes (sobre la versión elegida).
  const warnings = useMemo(() => {
    if (!closing) return [];
    return documents
      .filter((d) => selected[d.documentId] && d.closingRequired)
      .filter((d) => {
        const chosen = d.versions.find((v) => v.id === selected[d.documentId]);
        return !(d.closingValid && chosen?.current);
      })
      .map((d) => {
        const chosen = d.versions.find((v) => v.id === selected[d.documentId]);
        return `${label(d)} V${chosen?.versionNumber ?? "?"}`;
      });
  }, [closing, documents, selected, label]);

  const count = Object.keys(selected).length;

  const send = async (confirmWarnings: boolean) => {
    setBusy(true);
    try {
      await sendToNotary(expedienteId, {
        kind,
        documents: Object.entries(selected).map(([documentId, versionId]) => ({ documentId, versionId })),
        recipientEmail: email.trim() || undefined,
        note: note.trim() || undefined,
        confirmWarnings,
      });
      showToast(
        email.trim()
          ? "Envío registrado y correo en cola para la notaría."
          : "Envío registrado en el historial del expediente (con los documentos y la versión de cada uno).",
      );
      setOpen(false);
      setConfirming(false);
      onSent();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  if (!open) {
    return (
      <div className="mb-2">
        <Button size="sm" onClick={start} disabled={documents.length === 0}>
          <Send className="h-4 w-4" aria-hidden /> {closing ? "Enviar a notaría: envío de cierre" : "Enviar a notaría: envío inicial"}
        </Button>
        {documents.length === 0 ? <p className="mt-1 text-xs text-muted">Todavía no hay documentos con archivo para enviar.</p> : null}
      </div>
    );
  }

  return (
    <div className="mb-3 flex flex-col gap-3 rounded-lg border border-border p-3">
      <p className="text-sm font-medium text-obsessed">{closing ? "Paquete de cierre" : "Paquete inicial"}: marca los documentos y la versión</p>
      <ul className="flex flex-col gap-1.5">
        {documents.map((d) => {
          const checked = Boolean(selected[d.documentId]);
          const current = d.versions.find((v) => v.current) ?? d.versions[0];
          return (
            <li key={d.documentId} className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-app-bg px-2 py-1.5 text-sm">
              <label className="flex min-w-0 flex-1 items-start gap-2">
                <input
                  type="checkbox"
                  className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--color-c21-dark-gold)]"
                  checked={checked}
                  onChange={(e) =>
                    setSelected((prev) => {
                      const next = { ...prev };
                      if (e.target.checked && current) next[d.documentId] = current.id;
                      else delete next[d.documentId];
                      return next;
                    })
                  }
                />
                <span className="min-w-0 break-words text-obsessed">
                  {label(d)}
                  {closing && d.closingRequired ? (
                    <span className={d.closingValid ? "ml-1 text-xs text-success-text" : "ml-1 text-xs text-warning-text"}>
                      {d.closingValid ? "· vigente" : "· sin confirmar vigencia"}
                    </span>
                  ) : null}
                </span>
              </label>
              <div className="flex items-center gap-2">
                <select
                  aria-label={`Versión de ${label(d)}`}
                  value={selected[d.documentId] ?? current?.id ?? ""}
                  disabled={!checked || d.versions.length < 2}
                  onChange={(e) => setSelected((prev) => ({ ...prev, [d.documentId]: e.target.value }))}
                  className="min-h-8 rounded-lg border border-border bg-card px-2 text-xs text-obsessed"
                >
                  {d.versions.map((v) => (
                    <option key={v.id} value={v.id}>
                      V{v.versionNumber}
                      {v.current ? " (vigente)" : ""}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="text-xs font-medium text-dark-gold hover:underline"
                  onClick={() => {
                    const id = selected[d.documentId] ?? current?.id;
                    const v = d.versions.find((x) => x.id === id);
                    if (v) onView({ id: v.id, title: `${label(d)} · versión ${v.versionNumber}` });
                  }}
                >
                  Ver
                </button>
              </div>
            </li>
          );
        })}
      </ul>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium text-obsessed">Correo de la notaría (opcional)</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Si lo dejas vacío solo se registra el envío"
            className="min-h-10 rounded-lg border border-border bg-card px-3 text-obsessed"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium text-obsessed">Nota (opcional)</span>
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Ej. Notaría 12, Lic. Ruiz"
            className="min-h-10 rounded-lg border border-border bg-card px-3 text-obsessed"
          />
        </label>
      </div>
      {warnings.length > 0 ? (
        <p className="flex items-start gap-1.5 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          Sin confirmar como vigentes y al corriente: {warnings.join(", ")}.
        </p>
      ) : null}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" size="sm" onClick={() => setOpen(false)} disabled={busy}>
          Cancelar
        </Button>
        <Button size="sm" disabled={busy || count === 0} onClick={() => (warnings.length > 0 ? setConfirming(true) : send(false))}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Send className="h-4 w-4" aria-hidden />}{" "}
          {email.trim() ? `Enviar ${count} documento(s) y registrar` : `Registrar envío de ${count} documento(s)`}
        </Button>
      </div>
      <Modal
        open={confirming}
        onClose={() => setConfirming(false)}
        title="¿Enviar de todos modos?"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirming(false)} disabled={busy}>
              Revisar primero
            </Button>
            <Button onClick={() => send(true)} disabled={busy}>
              Enviar de todos modos
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted">
          Estos documentos de cierre no están confirmados como vigentes y al corriente de pago: {warnings.join(", ")}. El envío quedará registrado con
          esta advertencia.
        </p>
      </Modal>
    </div>
  );
}

function SubmissionHistory({ submissions }: { submissions: NotarySubmission[] }) {
  if (submissions.length === 0) return null;
  return (
    <div className="mt-3">
      <p className="mb-1 text-xs font-medium text-obsessed">Historial de envíos</p>
      <ul className="flex flex-col gap-1.5 rounded-lg bg-app-bg p-3 text-xs text-muted">
        {submissions.map((s) => (
          <li key={s.id}>
            <strong className="text-obsessed">{formatDateTime(s.sentAt)}</strong>
            {s.sentByName ? ` · ${s.sentByName}` : ""}
            {s.recipientEmail ? ` · enviado a ${s.recipientEmail}` : " · registrado (envío por fuera)"}
            {s.note ? ` · ${s.note}` : ""}
            <br />
            {s.items.map((i) => `${i.documentName} V${i.versionNumber}`).join(", ")}
            {s.warnings ? <span className="block text-warning-text">Advertencias aceptadas: {s.warnings}</span> : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
