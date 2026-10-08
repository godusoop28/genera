"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { useToast } from "@/components/ui/Toast";
import {
  acceptDocument,
  getDownloadUrl,
  listDocuments,
  listReviews,
  markNotApplicable,
  moveDocumentFile,
  rejectDocument,
  reprocessAllDocuments,
  reprocessDocument,
  requestAgain,
  returnDocument,
  uploadVersion,
} from "@/lib/api/documents";
import { confirmField, getExtractedFields } from "@/lib/api/extraction";
import { getDocumentsEmailPreview } from "@/lib/api/notifications";
import type {
  DocumentResponse,
  EmailPreviewResponse,
  ExtractedFieldObservationResponse,
  ReturnReasonCode,
  ReviewHistoryResponse,
} from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { errorText } from "@/lib/errors";
import {
  documentStatusLabels,
  documentStatusTone,
  extractedFieldLabel,
  formatDateTime,
  label,
  participantRoleLabels,
  pipelineStatusLabels,
  pipelineStatusTone,
  returnReasonLabels,
} from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { ACCEPTED_FILE_TYPES, UploadValidationError } from "@/lib/upload-limits";
import { cn } from "@/lib/utils";
import { AlertTriangle, ArrowRightLeft, Bot, Check, Download, History, Info, Loader2, Mail, Pencil, RefreshCw, Upload, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ExpedienteContext } from "./page";

const IN_PROGRESS = ["UPLOADED", "PROCESSING"];

type StatusFilter = "ALL" | "MISSING" | "TO_REVIEW" | "RETURNED";

const statusFilters: Record<StatusFilter, { label: string; matches: (d: DocumentResponse) => boolean }> = {
  ALL: { label: "Todos", matches: () => true },
  MISSING: { label: "Faltantes", matches: (d) => d.required && d.status === "PENDING" },
  TO_REVIEW: { label: "Por revisar", matches: (d) => d.status === "UPLOADED" || d.status === "READY_FOR_REVIEW" },
  RETURNED: { label: "Devueltos o rechazados", matches: (d) => d.status === "RETURNED" || d.status === "REJECTED" },
};

export function DocumentsPanel({ expediente, participants, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [documents, setDocuments] = useState<DocumentResponse[] | null>(null);
  const [emailPreview, setEmailPreview] = useState<EmailPreviewResponse | null>(null);
  const [showOptional, setShowOptional] = useState(false);
  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [reprocessingAll, setReprocessingAll] = useState(false);
  const names = Object.fromEntries(participants.map((p) => [p.id, p.fullName]));
  const canReprocess = can("DOCUMENT_ACCEPT") || can("EXTRACTED_DATA_EDIT") || can("DOCUMENT_UPLOAD");

  const load = useCallback(() => {
    listDocuments(expediente.id)
      .then(setDocuments)
      .catch((err) => showToast(errorText(err)));
  }, [expediente.id, showToast]);

  useEffect(() => {
    load();
  }, [load]);

  // Mientras haya archivos procesándose o en extracción, se actualiza solo (sin volver a subir nada).
  const processing = documents?.some((d) => d.pipelineStatus && IN_PROGRESS.includes(d.pipelineStatus)) ?? false;
  useEffect(() => {
    if (!processing) return;
    const timer = setTimeout(load, 4000);
    return () => clearTimeout(timer);
  }, [documents, processing, load]);

  const onChanged = async (updated?: DocumentResponse) => {
    if (updated) setDocuments((prev) => prev?.map((d) => (d.id === updated.id ? updated : d)) ?? null);
    load();
    await reload();
  };

  const visible = (documents ?? []).filter((d) => d.required || d.currentVersionNumber > 0 || d.status === "NOT_APPLICABLE" || showOptional);
  const isPending = (d: DocumentResponse) => d.required && d.status !== "ACCEPTED" && d.status !== "NOT_APPLICABLE";
  const pendingCount = visible.filter(isPending).length;
  const requiredCount = visible.filter((d) => d.required).length;
  const withFiles = (documents ?? []).filter((d) => d.currentVersionNumber > 0 && d.status !== "NOT_APPLICABLE");
  const filtered = visible.filter(statusFilters[filter].matches);
  // Un expediente con varios copropietarios llega a 20+ requisitos: se agrupan por persona
  // (en el orden del alta) y al final lo del inmueble y la empresa, para no recorrer una sola lista.
  const groups = [
    ...[...participants]
      .sort((a, b) => a.ordinal - b.ordinal)
      .map((p) => ({ key: p.id, title: p.fullName, subtitle: participantRoleLabels[p.role], docs: filtered.filter((d) => d.participantId === p.id) })),
    {
      key: "inmueble",
      title: expediente.personType === "MORAL" ? "Inmueble y empresa" : "Inmueble",
      subtitle: undefined,
      docs: filtered.filter((d) => !d.participantId || !names[d.participantId]),
    },
  ].filter((g) => g.docs.length > 0);

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Documentos"
          description={
            // Hasta tener la lista no se calcula nada: antes decía "todos resueltos" mientras cargaba.
            documents === null
              ? "Cargando requisitos…"
              : pendingCount > 0
                ? `Faltan ${pendingCount} documento(s) obligatorio(s) por aceptar o marcar como "No aplica".`
                : requiredCount > 0
                  ? "Todos los documentos obligatorios están resueltos."
                  : "Este expediente todavía no tiene documentos obligatorios registrados."
          }
        />
        {processing ? (
          <p className="mb-3 flex items-center gap-2 rounded-lg bg-app-bg px-3 py-2 text-xs text-muted">
            <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden /> La extracción automática continúa. Puedes seguir trabajando; la
            información se actualiza sola.
          </p>
        ) : null}
        {canReprocess && withFiles.length > 0 ? (
          <div className="mb-3">
            <Button
              variant="ghost"
              size="sm"
              disabled={reprocessingAll}
              onClick={async () => {
                setReprocessingAll(true);
                try {
                  const { reprocessed } = await reprocessAllDocuments(expediente.id);
                  showToast(`Se volvieron a procesar ${reprocessed} archivo(s) con las reglas actuales; los datos se actualizan solos.`);
                  load();
                } catch (err) {
                  showToast(errorText(err));
                } finally {
                  setReprocessingAll(false);
                }
              }}
            >
              <RefreshCw className={`h-4 w-4 ${reprocessingAll ? "animate-spin" : ""}`} aria-hidden /> Reprocesar todos con IA
            </Button>
          </div>
        ) : null}
        {documents === null ? (
          <p className="flex items-center gap-2 text-sm text-muted">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Cargando requisitos…
          </p>
        ) : (
          <>
            <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label="Filtrar documentos">
              {(Object.keys(statusFilters) as StatusFilter[]).map((id) => {
                const active = filter === id;
                const n = visible.filter(statusFilters[id].matches).length;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setFilter(id)}
                    aria-pressed={active}
                    className={cn(
                      "inline-flex min-h-9 items-center gap-2 rounded-full border px-3 text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                      active ? "border-gold bg-gold text-brand-ink" : "border-border bg-card text-muted hover:border-gold/60 hover:text-obsessed",
                    )}
                  >
                    {statusFilters[id].label}
                    <span className={cn("rounded-full px-1.5 text-xs tabular-nums", active ? "bg-white/40" : "bg-app-bg")}>{n}</span>
                  </button>
                );
              })}
            </div>
            {groups.length === 0 ? <p className="text-sm text-muted">No hay documentos con este filtro.</p> : null}
            <div className="flex flex-col gap-6">
              {groups.map((group) => {
                const groupPending = group.docs.filter(isPending).length;
                return (
                  <section key={group.key} aria-labelledby={`docs-${group.key}`}>
                    <div className="mb-2 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 border-b border-border pb-2">
                      <h3 id={`docs-${group.key}`} className="min-w-0 break-words text-sm font-semibold text-obsessed">
                        {group.title}
                        {group.subtitle ? <span className="ml-2 text-xs font-normal text-muted">{group.subtitle}</span> : null}
                      </h3>
                      <span className={cn("text-xs font-medium", groupPending > 0 ? "text-warning-text" : "text-success-text")}>
                        {groupPending > 0 ? `${groupPending} pendiente(s)` : "Sin pendientes"}
                      </span>
                    </div>
                    <div className="flex flex-col gap-3">
                      {group.docs.map((doc) => (
                        <DocumentRow
                          key={doc.id}
                          document={doc}
                          allDocuments={documents}
                          names={names}
                          participantName={doc.participantId ? names[doc.participantId] : undefined}
                          onChanged={onChanged}
                          canUpload={can("DOCUMENT_UPLOAD")}
                          canAccept={can("DOCUMENT_ACCEPT")}
                          canReturn={can("DOCUMENT_RETURN")}
                          canReject={can("DOCUMENT_REJECT")}
                          canOverride={can("DOCUMENT_QUALITY_OVERRIDE")}
                          canMarkNotApplicable={can("DOCUMENT_MARK_NOT_APPLICABLE")}
                          canEditData={can("EXTRACTED_DATA_EDIT")}
                          canReprocess={canReprocess}
                        />
                      ))}
                    </div>
                  </section>
                );
              })}
            </div>
          </>
        )}
        <button type="button" className="mt-3 text-xs text-dark-gold hover:underline" onClick={() => setShowOptional((v) => !v)}>
          {showOptional ? "Ocultar documentos que no aplican" : "Mostrar también documentos que no aplican a este expediente"}
        </button>
      </Card>

      {can("DOCUMENT_EMAIL_SEND") ? (
        <Card>
          <CardHeader
            title="Envío a notaría"
            description="Arma el correo con los documentos aceptados para que lo revises y lo envíes tú desde tu correo."
          />
          <Button
            size="sm"
            onClick={async () => {
              try {
                setEmailPreview(await getDocumentsEmailPreview(expediente.id));
              } catch (err) {
                showToast(errorText(err));
              }
            }}
          >
            <Mail className="h-4 w-4" aria-hidden /> Vista previa de envío
          </Button>
        </Card>
      ) : null}

      <Modal open={emailPreview !== null} onClose={() => setEmailPreview(null)} title="Vista previa de envío a notaría">
        {emailPreview ? (
          <div className="flex flex-col gap-4 text-sm">
            <p className="text-xs text-muted">Esto no envía nada. Descarga los documentos y envíalos tú desde tu correo.</p>
            <p>
              <span className="font-medium">Asunto:</span> {emailPreview.subject}
            </p>
            <p className="whitespace-pre-wrap">{emailPreview.body}</p>
            <ul className="flex flex-col gap-2">
              {emailPreview.attachments.map((a) => (
                <li key={a.fileName} className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
                  <span>{a.fileName}</span>
                  <a href={a.downloadUrl} target="_blank" rel="noreferrer" className="text-dark-gold hover:underline">
                    Descargar
                  </a>
                </li>
              ))}
            </ul>
            {emailPreview.documentTypesWithoutFile.length > 0 ? (
              <p className="rounded-lg bg-warning-bg px-3 py-2 text-warning-text">
                Sin archivo para adjuntar todavía: {emailPreview.documentTypesWithoutFile.join(", ")}.
              </p>
            ) : null}
          </div>
        ) : null}
      </Modal>
    </div>
  );
}

interface RowProps {
  document: DocumentResponse;
  allDocuments: DocumentResponse[];
  names: Record<string, string>;
  participantName?: string;
  onChanged: (updated?: DocumentResponse) => Promise<void>;
  canUpload: boolean;
  canAccept: boolean;
  canReturn: boolean;
  canReject: boolean;
  canOverride: boolean;
  canMarkNotApplicable: boolean;
  canEditData: boolean;
  canReprocess: boolean;
}

function DocumentRow({
  document: doc,
  allDocuments,
  names,
  participantName,
  onChanged,
  canUpload,
  canAccept,
  canReturn,
  canReject,
  canOverride,
  canMarkNotApplicable,
  canEditData,
  canReprocess,
}: RowProps) {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [fields, setFields] = useState<ExtractedFieldObservationResponse[] | null>(null);
  const [showData, setShowData] = useState(false);
  const [history, setHistory] = useState<ReviewHistoryResponse[] | null>(null);
  const [review, setReview] = useState<"return" | "reject" | null>(null);
  const [overriding, setOverriding] = useState(false);
  const [confirmingMismatch, setConfirmingMismatch] = useState(false);
  const [moving, setMoving] = useState(false);
  const [notApplicable, setNotApplicable] = useState(false);
  const [reprocessing, setReprocessing] = useState(false);
  const v = doc.latestVersion;
  const issues = v?.blockingIssues ?? [];
  const warnings = v?.warnings ?? [];
  const inProgress = doc.pipelineStatus ? IN_PROGRESS.includes(doc.pipelineStatus) : false;
  const reviewable = ["READY_FOR_REVIEW", "UPLOADED", "RETURNED"].includes(doc.status) && v && !["QUEUED", "PROCESSING"].includes(v.processingStatus);
  const mismatch = v?.aiTypeMatches === false;
  const docLabel = documentTypeLabel(doc.type);

  // Al abrir el panel de datos (y cada vez que termina una extracción) se cargan los datos leídos.
  const assessedAt = v?.aiAssessedAt ?? null;
  useEffect(() => {
    if (!showData) return;
    let active = true;
    getExtractedFields(doc.id)
      .then((result) => active && setFields(result))
      .catch((err) => active && showToast(errorText(err)));
    return () => {
      active = false;
    };
  }, [showData, assessedAt, doc.id, showToast]);

  const act = async (action: () => Promise<DocumentResponse>, success: string) => {
    try {
      const updated = await action();
      showToast(success);
      await onChanged(updated);
    } catch (err) {
      showToast(errorText(err));
    }
  };

  const handleAccept = () => {
    if (issues.length > 0) {
      if (canOverride) {
        setOverriding(true);
      } else {
        showToast("Este archivo no se puede leer: devuélvelo al cliente o pide a un director o administrador que autorice la excepción.");
      }
      return;
    }
    if (mismatch) {
      setConfirmingMismatch(true);
      return;
    }
    act(() => acceptDocument(doc.id), warnings.length > 0 ? "Documento aceptado (las advertencias quedaron registradas)." : "Documento aceptado.");
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploading(true);
    setProgress(null);
    try {
      await uploadVersion(doc.id, Array.from(files), (p) => setProgress(p.percent));
      showToast("Archivo cargado. El procesamiento y la extracción siguen en segundo plano: puedes seguir trabajando.");
      await onChanged();
    } catch (err) {
      showToast(err instanceof UploadValidationError ? err.message : errorText(err));
    } finally {
      setUploading(false);
      setProgress(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleReprocess = async () => {
    setReprocessing(true);
    try {
      const updated = await reprocessDocument(doc.id);
      showToast("Se está volviendo a procesar con IA. Los datos se actualizan solos al terminar; puedes seguir trabajando.");
      await onChanged(updated);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setReprocessing(false);
    }
  };

  const handleDownload = async () => {
    if (!v) return;
    const tab = window.open("about:blank", "_blank");
    try {
      const { url } = await getDownloadUrl(v.id);
      if (tab) tab.location.href = url;
    } catch (err) {
      tab?.close();
      showToast(errorText(err));
    }
  };

  const moveTargets = allDocuments.filter((d) => d.id !== doc.id && d.status !== "ACCEPTED");

  return (
    <div className="rounded-xl border border-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-obsessed">
            {docLabel}
            {participantName ? <span className="text-muted"> — {participantName}</span> : null}
          </p>
          <p className="text-xs text-muted">
            {doc.required
              ? doc.deferred && doc.status === "PENDING"
                ? `Obligatorio · el cliente lo sube después${doc.deferralReason ? ` (razón: ${doc.deferralReason})` : ""}`
                : "Obligatorio"
              : "Opcional / no aplica en este caso"}
            {v ? ` · Versión ${v.versionNumber}, cargada ${formatDateTime(v.uploadedAt)} por ${v.uploadedVia === "PUBLIC_PORTAL" ? "el cliente" : v.uploadedByName ?? "el staff"}` : ""}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {doc.pipelineStatus && doc.pipelineStatus !== "ACCEPTED" ? (
            <Badge tone={pipelineStatusTone[doc.pipelineStatus]}>
              {inProgress ? <Loader2 className="h-3 w-3 animate-spin" aria-hidden /> : null}
              {pipelineStatusLabels[doc.pipelineStatus]}
            </Badge>
          ) : null}
          {doc.required && doc.deferred && doc.status === "PENDING" ? <Badge tone="neutral">Para después</Badge> : null}
          <Badge tone={documentStatusTone[doc.status]}>{documentStatusLabels[doc.status]}</Badge>
        </div>
      </div>

      {issues.length > 0 && doc.status !== "ACCEPTED" ? (
        <div className="mt-3 rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">
          <p className="flex items-center gap-1.5 font-medium">
            <AlertTriangle className="h-4 w-4" aria-hidden /> No se puede leer: solo se acepta con autorización de excepción
          </p>
          <ul className="ml-5 mt-1 list-disc">
            {issues.map((issue) => (
              <li key={issue}>{issue}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {mismatch && doc.status !== "ACCEPTED" ? (
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
          <p className="flex items-start gap-1.5">
            <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            El archivo parece ser {v?.aiDetectedKind ? `un(a) ${v.aiDetectedKind}` : "otro documento"} aunque fue cargado como {docLabel}. Los datos
            que se leyeron se conservaron.
          </p>
          {canUpload && moveTargets.length > 0 ? (
            <Button variant="secondary" size="sm" onClick={() => setMoving(true)}>
              <ArrowRightLeft className="h-4 w-4" aria-hidden /> Cambiar tipo de documento
            </Button>
          ) : null}
        </div>
      ) : null}

      {warnings.length > 0 && doc.status !== "ACCEPTED" ? (
        <div className="mt-3 rounded-lg bg-app-bg px-3 py-2 text-xs text-muted">
          <p className="font-medium text-obsessed">Advertencias (no impiden aceptarlo):</p>
          <ul className="ml-5 mt-1 list-disc">
            {warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {inProgress ? (
        <p className="mt-2 text-xs text-muted">La extracción automática continúa. Puedes seguir trabajando; este documento se actualiza solo.</p>
      ) : null}
      {doc.status === "RETURNED" || doc.status === "REJECTED" ? (
        <p className="mt-2 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
          {doc.status === "RETURNED" ? "Devuelto al cliente" : "Rechazado"} el {formatDateTime(doc.lastReviewedAt)}. Motivo que ve el cliente:{" "}
          <strong>{label(returnReasonLabels, doc.lastReviewReasonCode)}</strong>
          {doc.lastReviewComment ? ` — "${doc.lastReviewComment}"` : ""}
        </p>
      ) : null}
      {doc.status === "NOT_APPLICABLE" ? (
        <p className="mt-2 rounded-lg bg-app-bg px-3 py-2 text-sm text-muted">
          Marcado como &quot;No aplica&quot; el {formatDateTime(doc.notApplicableAt)}: {doc.notApplicableJustification}
        </p>
      ) : null}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <input ref={fileInputRef} type="file" accept={ACCEPTED_FILE_TYPES} multiple className="hidden" onChange={handleUpload} />
        {reviewable && canAccept ? (
          <Button size="sm" onClick={handleAccept}>
            <Check className="h-4 w-4" aria-hidden /> {issues.length > 0 ? "Aceptar por excepción…" : "Aceptar"}
          </Button>
        ) : null}
        {v ? (
          <Button variant="secondary" size="sm" onClick={() => setShowData((s) => !s)}>
            <Bot className="h-4 w-4" aria-hidden /> {showData ? "Ocultar datos leídos" : "Datos leídos del documento"}
          </Button>
        ) : null}
        {v ? (
          <Button variant="secondary" size="sm" onClick={handleDownload}>
            <Download className="h-4 w-4" aria-hidden /> Ver archivo
          </Button>
        ) : null}
        {v && canReprocess ? (
          <Button variant="ghost" size="sm" onClick={handleReprocess} disabled={reprocessing || inProgress}>
            <RefreshCw className={`h-4 w-4 ${reprocessing ? "animate-spin" : ""}`} aria-hidden /> Reprocesar con IA
          </Button>
        ) : null}
        {v && canUpload && doc.status !== "ACCEPTED" && moveTargets.length > 0 ? (
          <Button variant="ghost" size="sm" onClick={() => setMoving(true)}>
            <ArrowRightLeft className="h-4 w-4" aria-hidden /> Cambiar tipo de documento
          </Button>
        ) : null}
        {reviewable && canReturn ? (
          <Button variant="secondary" size="sm" onClick={() => setReview("return")}>
            Devolver al cliente
          </Button>
        ) : null}
        {reviewable && canReject ? (
          <Button variant="danger" size="sm" onClick={() => setReview("reject")}>
            Rechazar
          </Button>
        ) : null}
        {canUpload && doc.status !== "NOT_APPLICABLE" && doc.status !== "ACCEPTED" ? (
          <Button variant="ghost" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
            {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />}
            {uploading
              ? progress !== null && progress < 100
                ? `Subiendo ${progress}%`
                : "Preparando…"
              : doc.currentVersionNumber > 0
                ? "Cargar nueva versión"
                : "Cargar en nombre del cliente"}
          </Button>
        ) : null}
        {/* La identificación no se omite: si no hay INE se carga otra identificación oficial y se acepta con justificación. */}
        {canMarkNotApplicable && doc.type !== "INE" && doc.status !== "ACCEPTED" && doc.status !== "NOT_APPLICABLE" ? (
          <Button variant="ghost" size="sm" onClick={() => setNotApplicable(true)}>
            No aplica…
          </Button>
        ) : null}
        {canMarkNotApplicable && doc.status === "NOT_APPLICABLE" ? (
          <Button variant="ghost" size="sm" onClick={() => act(() => requestAgain(doc.id), "El documento se volvió a solicitar.")}>
            Volver a solicitar
          </Button>
        ) : null}
        {v ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={async () => {
              if (history) return setHistory(null);
              try {
                setHistory(await listReviews(doc.id));
              } catch (err) {
                showToast(errorText(err));
              }
            }}
          >
            <History className="h-4 w-4" aria-hidden /> Historial
          </Button>
        ) : null}
      </div>
      {uploading && progress !== null ? (
        <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-app-bg" aria-hidden>
          <div className="h-full bg-gold transition-all" style={{ width: `${progress}%` }} />
        </div>
      ) : null}

      {showData && v ? (
        <ExtractedDataPanel
          document={doc}
          fields={fields}
          canEdit={canEditData}
          onEdited={(updated) => setFields((prev) => prev?.map((f) => (f.id === updated.id ? updated : f)) ?? null)}
        />
      ) : null}

      {history ? (
        <ul className="mt-3 flex flex-col gap-1 rounded-lg bg-app-bg p-3 text-xs text-muted">
          {history.length === 0 ? <li>Sin decisiones todavía.</li> : null}
          {history.map((h) => (
            <li key={h.id}>
              {formatDateTime(h.reviewedAt)} —{" "}
              {h.decision === "ACCEPTED" ? (h.overrideJustification ? "Aceptado POR EXCEPCIÓN" : "Aceptado") : h.decision === "RETURNED" ? "Devuelto" : "Rechazado"}
              {h.reasonCode ? `: ${label(returnReasonLabels, h.reasonCode)}` : ""}
              {h.comment ? ` — "${h.comment}"` : ""}
              {h.overrideJustification ? ` — Justificación: "${h.overrideJustification}" (alertas: ${h.overriddenIssues})` : ""}
            </li>
          ))}
        </ul>
      ) : null}

      {review ? (
        <ReviewModal
          mode={review}
          documentLabel={docLabel}
          onCancel={() => setReview(null)}
          onConfirm={async (reasonCode, comment) => {
            await act(
              () => (review === "return" ? returnDocument(doc.id, reasonCode, comment) : rejectDocument(doc.id, reasonCode, comment)),
              review === "return" ? "Guardado: el documento se devolvió y el cliente verá el motivo en su liga." : "Documento rechazado; el cliente verá el motivo.",
            );
            setReview(null);
          }}
        />
      ) : null}

      {moving ? (
        <MoveModal
          document={doc}
          targets={moveTargets}
          names={names}
          onCancel={() => setMoving(false)}
          onConfirm={async (targetId) => {
            const target = allDocuments.find((d) => d.id === targetId);
            await act(
              () => moveDocumentFile(doc.id, targetId),
              `El archivo se movió a ${target ? documentTypeLabel(target.type) : "otro documento"} y se está leyendo con su tipo correcto.`,
            );
            setMoving(false);
          }}
        />
      ) : null}

      <ReasonModal
        open={confirmingMismatch}
        title={`¿Aceptar como ${docLabel}?`}
        description={
          <p>
            La revisión automática indica que el archivo parece ser {v?.aiDetectedKind ? `un(a) ${v.aiDetectedKind}` : "otro documento"}. Si
            revisaste el archivo y sí es {docLabel}, acéptalo; si es otro documento, usa &quot;Cambiar tipo de documento&quot;.
          </p>
        }
        label="Nota (opcional)"
        minLength={0}
        confirmLabel="Sí, aceptarlo"
        onCancel={() => setConfirmingMismatch(false)}
        onConfirm={async () => {
          await act(() => acceptDocument(doc.id), "Documento aceptado.");
          setConfirmingMismatch(false);
        }}
      />

      <ReasonModal
        open={overriding}
        title="Aceptar por excepción"
        description={
          <>
            <p className="mb-2">La revisión automática indica que este archivo no se puede leer:</p>
            <ul className="ml-5 list-disc">
              {issues.map((i) => (
                <li key={i}>{i}</li>
              ))}
            </ul>
            <p className="mt-2">La aceptación quedará registrada con tu usuario y esta justificación.</p>
          </>
        }
        label="¿Por qué es válido pese a las alertas?"
        minLength={15}
        confirmLabel="Aceptar por excepción"
        onCancel={() => setOverriding(false)}
        onConfirm={async (justification) => {
          await act(() => acceptDocument(doc.id, justification), "Documento aceptado por excepción (registrado en la bitácora).");
          setOverriding(false);
        }}
      />

      <ReasonModal
        open={notApplicable}
        title={`"${docLabel}" no aplica`}
        description="Deja de pedirse al cliente y cuenta como resuelto. Explica por qué no aplica a este expediente."
        label="Justificación"
        placeholder="Ej. El terreno no tiene contrato de agua todavía."
        minLength={15}
        confirmLabel="Marcar como No aplica"
        onCancel={() => setNotApplicable(false)}
        onConfirm={async (justification) => {
          await act(() => markNotApplicable(doc.id, justification), "Marcado como No aplica.");
          setNotApplicable(false);
        }}
      />
    </div>
  );
}

function confidenceBadge(confidence: number | null): { text: string; tone: "success" | "info" | "warning" | "neutral" } {
  if (confidence === null) return { text: "sin dato de confianza", tone: "neutral" };
  if (confidence >= 0.8) return { text: `confianza alta (${Math.round(confidence * 100)}%)`, tone: "success" };
  if (confidence >= 0.5) return { text: `confianza media (${Math.round(confidence * 100)}%)`, tone: "info" };
  return { text: `confianza baja (${Math.round(confidence * 100)}%): revisar`, tone: "warning" };
}

/** "Datos leídos del documento": qué detectó la IA, si se lee, cada dato con su confianza y página, y advertencias. */
function ExtractedDataPanel({
  document: doc,
  fields,
  canEdit,
  onEdited,
}: {
  document: DocumentResponse;
  fields: ExtractedFieldObservationResponse[] | null;
  canEdit: boolean;
  onEdited: (updated: ExtractedFieldObservationResponse) => void;
}) {
  const v = doc.latestVersion;
  if (!v) return null;
  const inProgress = doc.pipelineStatus ? IN_PROGRESS.includes(doc.pipelineStatus) : false;
  const legibility =
    v.qualityLevel === "UNREADABLE"
      ? "No legible"
      : v.aiLegible === false
        ? (v.aiFieldsFound ?? 0) > 0
          ? `Difícil de leer: se extrajeron ${v.aiFieldsFound} datos, revísalos contra el archivo`
          : "No legible"
      : v.aiLegible === true
        ? v.qualityLevel === "ACCEPTED_WITH_WARNINGS"
          ? "Legible (con advertencias de calidad)"
          : "Legible"
        : v.aiCheckFailed
          ? "Sin revisión automática: verifícalo visualmente"
          : inProgress
            ? "En revisión…"
            : "Sin determinar";
  const schemaFields = (fields ?? []).filter((f) => !f.extra);
  const extraFields = (fields ?? []).filter((f) => f.extra);

  return (
    <div className="mt-3 rounded-lg bg-app-bg p-3">
      <dl className="grid grid-cols-1 gap-2 text-xs sm:grid-cols-3">
        <div>
          <dt className="text-muted">Documento detectado</dt>
          <dd className="text-sm text-obsessed">{v.aiDetectedKind ?? (inProgress ? "En revisión…" : "—")}</dd>
        </div>
        <div>
          <dt className="text-muted">Legibilidad</dt>
          <dd className="text-sm text-obsessed">{legibility}</dd>
        </div>
        <div>
          <dt className="text-muted">Cobertura</dt>
          <dd className="text-sm text-obsessed">
            {v.aiFieldsExpected != null && v.aiFieldsFound != null ? `${v.aiFieldsFound} de ${v.aiFieldsExpected} datos` : "—"}
            {v.aiPagesTotal ? ` · ${v.aiPagesAnalyzed ?? "?"} de ${v.aiPagesTotal} páginas revisadas` : ""}
          </dd>
        </div>
      </dl>
      {v.aiObservations ? <p className="mt-2 text-xs text-muted">Observaciones: {v.aiObservations}</p> : null}

      {fields === null ? (
        <p className="mt-3 text-xs text-muted">Cargando datos leídos…</p>
      ) : fields.length === 0 ? (
        <p className="mt-3 text-xs text-muted">
          {inProgress ? "La extracción automática continúa; los datos aparecerán aquí al terminar." : "No se leyeron datos de este archivo. Puedes reprocesarlo con IA o capturarlos a mano."}
        </p>
      ) : (
        <>
          <ul className="mt-3 flex flex-col divide-y divide-border">
            {schemaFields.map((f) => (
              <FieldRow key={f.id} field={f} canEdit={canEdit} onEdited={onEdited} />
            ))}
          </ul>
          {extraFields.length > 0 ? (
            <>
              <p className="mt-3 text-xs font-medium text-obsessed">Otros datos encontrados</p>
              <ul className="mt-1 flex flex-col divide-y divide-border">
                {extraFields.map((f) => (
                  <FieldRow key={f.id} field={f} canEdit={canEdit} onEdited={onEdited} />
                ))}
              </ul>
            </>
          ) : null}
        </>
      )}
    </div>
  );
}

function FieldRow({
  field: f,
  canEdit,
  onEdited,
}: {
  field: ExtractedFieldObservationResponse;
  canEdit: boolean;
  onEdited: (updated: ExtractedFieldObservationResponse) => void;
}) {
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(f.confirmedValue ?? f.detectedValue ?? "");
  const [saving, setSaving] = useState(false);
  const confidence = confidenceBadge(f.confirmedValue !== null ? 1 : f.confidence);

  const save = async () => {
    if (!value.trim()) return;
    setSaving(true);
    try {
      onEdited(await confirmField(f.id, value.trim()));
      setEditing(false);
      showToast("Dato confirmado.");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <li className="flex flex-wrap items-start justify-between gap-2 py-2">
      <div className="min-w-0 flex-1">
        <p className="text-xs text-muted">
          {extractedFieldLabel(f.fieldName)}
          {f.sourcePage ? ` · página ${f.sourcePage}` : ""}
        </p>
        {editing ? (
          <div className="mt-1 flex items-center gap-2">
            <input
              value={value}
              onChange={(e) => setValue(e.target.value)}
              className="min-w-0 flex-1 rounded-lg border border-border bg-card px-2 py-1 text-sm outline-none focus:border-gold"
              autoFocus
            />
            <button type="button" aria-label="Guardar dato" onClick={save} disabled={saving} className="text-success-text">
              {saving ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Check className="h-4 w-4" aria-hidden />}
            </button>
            <button type="button" aria-label="Cancelar" onClick={() => setEditing(false)} className="text-muted">
              <X className="h-4 w-4" aria-hidden />
            </button>
          </div>
        ) : (
          <p className="text-sm break-words text-obsessed">{f.confirmedValue ?? f.detectedValue ?? "—"}</p>
        )}
      </div>
      <div className="flex items-center gap-2">
        <Badge tone={f.confirmedValue !== null ? "success" : confidence.tone}>{f.confirmedValue !== null ? "confirmado por el staff" : confidence.text}</Badge>
        {canEdit && !editing ? (
          <button type="button" aria-label="Editar dato" onClick={() => setEditing(true)} className="text-muted hover:text-obsessed">
            <Pencil className="h-3.5 w-3.5" aria-hidden />
          </button>
        ) : null}
      </div>
    </li>
  );
}

function MoveModal({
  document: doc,
  targets,
  names,
  onCancel,
  onConfirm,
}: {
  document: DocumentResponse;
  targets: DocumentResponse[];
  names: Record<string, string>;
  onCancel: () => void;
  onConfirm: (targetId: string) => Promise<void>;
}) {
  const [target, setTarget] = useState(targets[0]?.id ?? "");
  const [busy, setBusy] = useState(false);
  return (
    <Modal
      open
      onClose={onCancel}
      title="Cambiar tipo de documento"
      footer={
        <>
          <Button variant="ghost" onClick={onCancel}>
            Cancelar
          </Button>
          <Button
            disabled={!target || busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm(target);
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Moviendo…" : "Mover archivo"}
          </Button>
        </>
      }
    >
      <p className="mb-3 text-sm text-muted">
        El archivo cargado como <strong>{documentTypeLabel(doc.type)}</strong> pasa al documento que elijas (sin que el cliente lo vuelva a subir) y se
        lee otra vez con ese tipo. {documentTypeLabel(doc.type)} vuelve a quedar pendiente.
      </p>
      <label className="mb-1 block text-sm font-medium text-obsessed">¿A qué documento corresponde?</label>
      <select
        value={target}
        onChange={(e) => setTarget(e.target.value)}
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm"
      >
        {targets.map((d) => (
          <option key={d.id} value={d.id}>
            {documentTypeLabel(d.type)}
            {d.participantId && names[d.participantId] ? ` — ${names[d.participantId]}` : ""}
            {d.currentVersionNumber > 0 ? " (ya tiene archivo: se agrega como versión nueva)" : ""}
          </option>
        ))}
      </select>
    </Modal>
  );
}

function ReviewModal({
  mode,
  documentLabel,
  onCancel,
  onConfirm,
}: {
  mode: "return" | "reject";
  documentLabel: string;
  onCancel: () => void;
  onConfirm: (reasonCode: ReturnReasonCode, comment: string) => Promise<void>;
}) {
  const [reasonCode, setReasonCode] = useState<ReturnReasonCode>("ILLEGIBLE_INFORMATION");
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const valid = reasonCode !== "OTHER" || comment.trim().length > 0;
  return (
    <Modal
      open
      onClose={onCancel}
      title={mode === "return" ? `Devolver "${documentLabel}" al cliente` : `Rechazar "${documentLabel}"`}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel}>
            Cancelar
          </Button>
          <Button
            variant={mode === "reject" ? "danger" : "primary"}
            disabled={!valid || busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm(reasonCode, comment.trim());
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Guardando…" : mode === "return" ? "Devolver" : "Rechazar"}
          </Button>
        </>
      }
    >
      <label className="mb-1 block text-sm font-medium text-obsessed">Motivo</label>
      <select
        value={reasonCode}
        onChange={(e) => setReasonCode(e.target.value as ReturnReasonCode)}
        className="mb-3 w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm"
      >
        {Object.entries(returnReasonLabels).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
      <label className="mb-1 block text-sm font-medium text-obsessed">
        Indicación para el cliente {reasonCode === "OTHER" ? "(obligatoria)" : "(recomendada)"}
      </label>
      <textarea
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        rows={3}
        placeholder="Ej. La foto salió cortada; tómala de nuevo mostrando la hoja completa."
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold"
      />
      <p className="mt-2 text-xs text-muted">El cliente verá este motivo en su liga y recibirá un aviso por correo si registró uno.</p>
    </Modal>
  );
}
