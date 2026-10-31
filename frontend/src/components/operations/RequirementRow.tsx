"use client";

import { formatDay, formatMoment } from "@/components/operations/OperationUi";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { useToast } from "@/components/ui/Toast";
import { errorText } from "@/lib/errors";
import {
  addEvidenceNote,
  attachEvidenceFile,
  decideNotApplicable,
  evidenceDownload,
  linkEvidenceDocument,
  requestNotApplicable,
  requestedFromLabels,
  requirementFollowUp,
  requirementStatusLabels,
  requirementStatusTone,
  reviewRequirement,
  type EvidenceView,
  type OperationDetail,
  type RequestedFrom,
  type RequirementView,
} from "@/lib/api/operations";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { ChevronDown, ChevronUp, Download, FileText, Link2, Paperclip } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";

type Panel = null | "evidence" | "follow" | "review" | "na";

/** CC#32: un requisito con su estado, evidencia vigente y versiones, seguimiento, revisión y "No aplica". */
export function RequirementRow({
  detail,
  requirement: r,
  canManage,
  canReview,
  onChange,
}: {
  detail: OperationDetail;
  requirement: RequirementView;
  canManage: boolean;
  canReview: boolean;
  onChange: (d: OperationDetail) => void;
}) {
  const { showToast } = useToast();
  const [panel, setPanel] = useState<Panel>(null);
  const [busy, setBusy] = useState(false);
  const [showVersions, setShowVersions] = useState(false);
  const opId = detail.operation.id;

  const run = async (action: () => Promise<OperationDetail>, ok: string) => {
    setBusy(true);
    try {
      onChange(await action());
      setPanel(null);
      showToast(ok);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const naPending = r.status === "NOT_APPLICABLE" && !r.naApprovedAt;
  const toggle = (p: Panel) => setPanel((current) => (current === p ? null : p));

  return (
    <div className={`rounded-xl border p-3 ${r.satisfied ? "border-success-text/30 bg-success-bg/30" : r.expired || r.status === "NEEDS_CORRECTION" ? "border-danger-text/30" : "border-border"}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium text-obsessed">
            {r.label} {r.mandatory ? <span className="ml-1 text-xs font-normal text-danger-text">Obligatorio</span> : null}
          </p>
          {r.description ? <p className="text-xs text-muted">{r.description}</p> : null}
          <p className="mt-1 text-xs text-muted">
            Se solicita a: {r.requestedFrom ? requestedFromLabels[r.requestedFrom] : "—"}
            {r.requestedAt ? ` · desde ${formatMoment(r.requestedAt)}` : ""}
            {r.followUpOwner ? ` · seguimiento: ${r.followUpOwner}` : ""}
            {r.dueDate ? ` · compromiso ${formatDay(r.dueDate)}` : ""}
            {r.validityDays ? ` · vigencia ${r.validityDays} días` : ""}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-1">
          <Badge tone={requirementStatusTone[r.status]}>{naPending ? "No aplica (por validar)" : requirementStatusLabels[r.status]}</Badge>
          {r.expired ? <Badge tone="danger">Requiere actualización</Badge> : null}
          {r.dateMissing ? <Badge tone="warning">Falta fecha del documento</Badge> : null}
        </div>
      </div>

      {r.currentEvidence ? (
        <EvidenceLine opId={opId} evidence={r.currentEvidence} current expedienteId={detail.operation.expedienteId} />
      ) : (
        <p className="mt-2 text-xs text-muted">Sin evidencia.</p>
      )}
      {r.previousEvidence.length > 0 ? (
        <button type="button" onClick={() => setShowVersions((v) => !v)} className="mt-1 inline-flex items-center gap-1 text-xs text-dark-gold hover:underline">
          {showVersions ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />} {r.previousEvidence.length} versión(es) anterior(es)
        </button>
      ) : null}
      {showVersions ? r.previousEvidence.map((e) => <EvidenceLine key={e.id} opId={opId} evidence={e} expedienteId={detail.operation.expedienteId} />) : null}

      {r.observation ? <p className="mt-2 rounded-lg bg-app-bg px-2 py-1 text-xs">Observación: {r.observation}</p> : null}
      {r.status === "NOT_APPLICABLE" ? (
        <p className="mt-2 text-xs text-muted">
          No aplica — {r.naJustification} ({r.naRequestedByName}, {formatMoment(r.naRequestedAt)})
          {r.naApprovedByName ? ` · validado por ${r.naApprovedByName}` : " · pendiente de validar por el rol autorizado"}
        </p>
      ) : null}
      {r.reviewedByName && r.status !== "NOT_APPLICABLE" ? (
        <p className="mt-1 text-xs text-muted">
          Revisó: {r.reviewedByName} · {formatMoment(r.reviewedAt)}
        </p>
      ) : null}

      {canManage || canReview ? (
        <div className="mt-2 flex flex-wrap gap-1">
          {canManage ? (
            <>
              <Button size="sm" variant="ghost" onClick={() => toggle("evidence")}>
                <Paperclip className="h-3.5 w-3.5" aria-hidden /> Evidencia
              </Button>
              <Button size="sm" variant="ghost" onClick={() => toggle("follow")}>
                Seguimiento
              </Button>
              {!r.mandatory && r.status !== "NOT_APPLICABLE" ? (
                <Button size="sm" variant="ghost" onClick={() => toggle("na")}>
                  No aplica…
                </Button>
              ) : null}
            </>
          ) : null}
          {canReview ? (
            naPending ? (
              <>
                <Button size="sm" variant="secondary" disabled={busy} onClick={() => run(() => decideNotApplicable(opId, r.id, true), "\"No aplica\" validado.")}>
                  Validar &quot;No aplica&quot;
                </Button>
                <Button size="sm" variant="ghost" onClick={() => toggle("review")}>
                  Rechazar &quot;No aplica&quot;
                </Button>
              </>
            ) : r.status !== "NOT_APPLICABLE" ? (
              <Button size="sm" variant="secondary" onClick={() => toggle("review")}>
                Revisar
              </Button>
            ) : null
          ) : null}
        </div>
      ) : null}

      {panel === "evidence" ? <EvidencePanel detail={detail} requirement={r} busy={busy} run={run} /> : null}
      {panel === "follow" ? <FollowPanel opId={opId} requirement={r} busy={busy} run={run} /> : null}
      {panel === "na" ? (
        <TextPanel
          label="Justificación"
          placeholder="Por qué este requisito no aplica a esta operación"
          confirm="Solicitar “No aplica”"
          busy={busy}
          onConfirm={(text) => run(() => requestNotApplicable(opId, r.id, text), "Solicitud registrada.")}
        />
      ) : null}
      {panel === "review" ? (
        naPending ? (
          <TextPanel label="Por qué sí aplica" confirm="Rechazar" busy={busy} onConfirm={(text) => run(() => decideNotApplicable(opId, r.id, false, text), "\"No aplica\" rechazado.")} />
        ) : (
          <ReviewPanel opId={opId} requirement={r} busy={busy} run={run} />
        )
      ) : null}
    </div>
  );
}

function EvidenceLine({ opId, evidence: e, current, expedienteId }: { opId: string; evidence: EvidenceView; current?: boolean; expedienteId: string | null }) {
  const { showToast } = useToast();
  const open = async () => {
    try {
      const { url } = await evidenceDownload(opId, e.id);
      window.open(url, "_blank", "noopener");
    } catch (err) {
      showToast(errorText(err));
    }
  };
  return (
    <div className={`mt-2 flex flex-wrap items-center gap-2 text-xs ${current ? "" : "pl-4 text-muted"}`}>
      <Badge tone="neutral">v{e.versionNumber}</Badge>
      {e.kind === "FILE" ? (
        <button type="button" onClick={open} className="inline-flex items-center gap-1 text-dark-gold hover:underline">
          <Download className="h-3.5 w-3.5" aria-hidden /> {e.filename}
        </button>
      ) : e.kind === "EXPEDIENTE_DOCUMENT" ? (
        <span className="inline-flex items-center gap-1">
          <Link2 className="h-3.5 w-3.5" aria-hidden /> Documento del expediente: {e.documentName}
          {expedienteId ? (
            <Link href={`/expedientes/${expedienteId}`} className="text-dark-gold hover:underline">
              (ver)
            </Link>
          ) : null}
        </span>
      ) : (
        <span className="inline-flex items-center gap-1">
          <FileText className="h-3.5 w-3.5" aria-hidden /> {e.note}
        </span>
      )}
      {e.kind !== "NOTE" && e.note ? <span className="text-muted">“{e.note}”</span> : null}
      {e.documentDate ? <span>Fecha del documento: {formatDay(e.documentDate)}</span> : null}
      {e.expiresOn ? <span>Vence: {formatDay(e.expiresOn)}</span> : null}
      <span className="text-muted">
        {e.attachedByName} · {formatMoment(e.attachedAt)}
      </span>
    </div>
  );
}

type Run = (action: () => Promise<OperationDetail>, ok: string) => Promise<void>;

function EvidencePanel({ detail, requirement: r, busy, run }: { detail: OperationDetail; requirement: RequirementView; busy: boolean; run: Run }) {
  const opId = detail.operation.id;
  const [mode, setMode] = useState<"file" | "document" | "note">(detail.expedienteDocuments.length > 0 ? "document" : "file");
  const [documentDate, setDocumentDate] = useState("");
  const [note, setNote] = useState("");
  const suggested = detail.expedienteDocuments.filter((doc) => !r.documentTypeHint || doc.type === r.documentTypeHint);
  const others = detail.expedienteDocuments.filter((doc) => !suggested.includes(doc));
  const [documentId, setDocumentId] = useState(suggested[0]?.documentId ?? detail.expedienteDocuments[0]?.documentId ?? "");
  const file = useRef<HTMLInputElement>(null);
  const [picked, setPicked] = useState<File | null>(null);
  const docOption = (doc: OperationDetail["expedienteDocuments"][number]) => ({
    value: doc.documentId,
    label: `${doc.name ?? (doc.type ? documentTypeLabel(doc.type) : "Documento")} · ${doc.status}`,
  });

  return (
    <div className="mt-3 flex flex-col gap-3 rounded-lg bg-app-bg p-3">
      <div className="flex flex-wrap gap-2 text-xs">
        {detail.expedienteDocuments.length > 0 ? (
          <label className="inline-flex items-center gap-1">
            <input type="radio" checked={mode === "document"} onChange={() => setMode("document")} /> Vincular documento del expediente
          </label>
        ) : null}
        <label className="inline-flex items-center gap-1">
          <input type="radio" checked={mode === "file"} onChange={() => setMode("file")} /> Cargar archivo
        </label>
        <label className="inline-flex items-center gap-1">
          <input type="radio" checked={mode === "note"} onChange={() => setMode("note")} /> Dato o referencia
        </label>
      </div>
      {mode === "document" ? (
        <>
          <Select
            label="Documento ya entregado (se vincula, no se vuelve a pedir)"
            value={documentId}
            onChange={(e) => setDocumentId(e.target.value)}
            options={suggested.map(docOption)}
            groups={others.length ? [{ label: "Otros documentos del expediente", options: others.map(docOption) }] : undefined}
          />
          <p className="text-xs text-muted">Revisa su vigencia: si ya no es válido, carga una versión actualizada.</p>
        </>
      ) : null}
      {mode === "file" ? (
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant="secondary" type="button" onClick={() => file.current?.click()}>
            <Paperclip className="h-3.5 w-3.5" aria-hidden /> {picked ? picked.name : "Elegir archivo (PDF o imagen, 25 MB)"}
          </Button>
          <input ref={file} type="file" accept="application/pdf,image/jpeg,image/png,image/webp" className="hidden" onChange={(e) => setPicked(e.target.files?.[0] ?? null)} />
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <Input
          label={r.validityDays ? "Fecha del documento (para su vigencia)" : "Fecha del documento (opcional)"}
          type="date"
          value={documentDate}
          onChange={(e) => setDocumentDate(e.target.value)}
        />
        <Input label={mode === "note" ? "Dato o referencia" : "Nota (opcional)"} value={note} onChange={(e) => setNote(e.target.value)} placeholder={mode === "note" ? "Ej. número de registro, folio de consulta" : ""} />
      </div>
      <div>
        <Button
          size="sm"
          disabled={busy || (mode === "file" && !picked) || (mode === "note" && !note.trim()) || (mode === "document" && !documentId)}
          onClick={() =>
            run(
              () =>
                mode === "file" && picked
                  ? attachEvidenceFile(opId, r.id, picked, documentDate || undefined, note || undefined)
                  : mode === "document"
                    ? linkEvidenceDocument(opId, r.id, documentId, documentDate, note)
                    : addEvidenceNote(opId, r.id, note, documentDate),
              "Evidencia registrada: queda como Recibido hasta su revisión.",
            )
          }
        >
          Guardar evidencia
        </Button>
      </div>
    </div>
  );
}

function FollowPanel({ opId, requirement: r, busy, run }: { opId: string; requirement: RequirementView; busy: boolean; run: Run }) {
  const [from, setFrom] = useState<RequestedFrom>(r.requestedFrom ?? "CLIENT");
  const [owner, setOwner] = useState(r.followUpOwner ?? "");
  const [due, setDue] = useState(r.dueDate ?? "");
  return (
    <div className="mt-3 grid grid-cols-1 gap-2 rounded-lg bg-app-bg p-3 sm:grid-cols-3">
      <Select label="Se solicita a" value={from} onChange={(e) => setFrom(e.target.value as RequestedFrom)} options={(Object.keys(requestedFromLabels) as RequestedFrom[]).map((k) => ({ value: k, label: requestedFromLabels[k] }))} />
      <Input label="Responsable del seguimiento" value={owner} onChange={(e) => setOwner(e.target.value)} />
      <Input label="Fecha compromiso" type="date" value={due} onChange={(e) => setDue(e.target.value)} />
      <div className="sm:col-span-3">
        <Button size="sm" disabled={busy} onClick={() => run(() => requirementFollowUp(opId, r.id, { requestedFrom: from, owner: owner || null, dueDate: due || null }), "Seguimiento guardado.")}>
          Guardar seguimiento
        </Button>
      </div>
    </div>
  );
}

function ReviewPanel({ opId, requirement: r, busy, run }: { opId: string; requirement: RequirementView; busy: boolean; run: Run }) {
  const [observation, setObservation] = useState("");
  return (
    <div className="mt-3 flex flex-col gap-2 rounded-lg bg-app-bg p-3">
      <Input label="Observación" value={observation} onChange={(e) => setObservation(e.target.value)} placeholder="Obligatoria si requiere corrección" />
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="secondary" disabled={busy} onClick={() => run(() => reviewRequirement(opId, r.id, "IN_REVIEW", observation), "En revisión.")}>
          En revisión
        </Button>
        <Button size="sm" disabled={busy || !r.currentEvidence} onClick={() => run(() => reviewRequirement(opId, r.id, "VALIDATED", observation), "Requisito validado.")}>
          Validar
        </Button>
        <Button size="sm" variant="danger" disabled={busy || !observation.trim()} onClick={() => run(() => reviewRequirement(opId, r.id, "NEEDS_CORRECTION", observation), "Marcado para corrección.")}>
          Requiere corrección
        </Button>
      </div>
      {!r.currentEvidence ? <p className="text-xs text-muted">Para validar se necesita la evidencia que lo sustenta.</p> : null}
    </div>
  );
}

function TextPanel({ label, placeholder, confirm, busy, onConfirm }: { label: string; placeholder?: string; confirm: string; busy: boolean; onConfirm: (text: string) => void }) {
  const [text, setText] = useState("");
  return (
    <div className="mt-3 flex flex-col gap-2 rounded-lg bg-app-bg p-3">
      <Input label={label} value={text} placeholder={placeholder} onChange={(e) => setText(e.target.value)} />
      <div>
        <Button size="sm" disabled={busy || !text.trim()} onClick={() => onConfirm(text.trim())}>
          {confirm}
        </Button>
      </div>
    </div>
  );
}
