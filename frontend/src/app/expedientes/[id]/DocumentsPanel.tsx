"use client";

import { DocumentViewerModal } from "@/components/documents/DocumentViewerModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { useToast } from "@/components/ui/Toast";
import {
  acceptDocument,
  addCustomDocument,
  approveException,
  cancelDocumentUpdate,
  clearDocumentPending,
  deleteCustomDocument,
  listDocuments,
  listReviews,
  listVersions,
  markDocumentPending,
  markNotApplicable,
  moveDocumentFile,
  rejectDocument,
  renameDocument,
  reprocessAllDocuments,
  removeDocumentFile,
  reprocessDocument,
  requestAgain,
  requestDocumentUpdate,
  returnDocument,
  revokeException,
  setAddressProofKind,
  uploadVersion,
} from "@/lib/api/documents";
import { confirmField, getExtractedFields } from "@/lib/api/extraction";
import type {
  AddressProofKind,
  DocumentResponse,
  DocumentVersionResponse,
  ExtractedFieldObservationResponse,
  ReturnReasonCode,
  ReviewHistoryResponse,
} from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { errorText } from "@/lib/errors";
import {
  ADDRESS_PROOF_KIND_LABELS,
  ADDRESS_PROOF_KINDS,
  documentStatusLabels,
  documentStatusTone,
  extractedFieldLabel,
  formatDate,
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
import { AlertTriangle, ArrowRightLeft, Bot, Check, Clock, Eye, History, Info, Layers, Loader2, Pencil, Plus, RefreshCw, Trash2, Upload, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { NotaryStagesPanel } from "./NotaryStagesPanel";
import type { ExpedienteContext } from "./page";

const IN_PROGRESS = ["UPLOADED", "PROCESSING"];

/** Nombre con el que se muestra: el capturado si se agregó a mano (Control de Cambios #1), si no el del tipo. */
function documentName(d: Pick<DocumentResponse, "type" | "customName">): string {
  return d.customName ?? documentTypeLabel(d.type);
}

type StatusFilter = "ALL" | "MISSING" | "MARKED_PENDING" | "TO_REVIEW" | "RETURNED";

const statusFilters: Record<StatusFilter, { label: string; matches: (d: DocumentResponse) => boolean }> = {
  ALL: { label: "Todos", matches: () => true },
  MISSING: { label: "Faltantes", matches: (d) => d.required && d.status === "PENDING" },
  // Control de Cambios #14: los que el asesor marcó "Pendiente" (se entregan después).
  MARKED_PENDING: { label: "Pendientes de entrega", matches: (d) => Boolean(d.markedPending) },
  TO_REVIEW: { label: "Por revisar", matches: (d) => d.status === "UPLOADED" || d.status === "READY_FOR_REVIEW" },
  RETURNED: { label: "Devueltos o rechazados", matches: (d) => d.status === "RETURNED" || d.status === "REJECTED" },
};

export function DocumentsPanel({ expediente, participants, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [documents, setDocuments] = useState<DocumentResponse[] | null>(null);
  const [showOptional, setShowOptional] = useState(false);
  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [reprocessingAll, setReprocessingAll] = useState(false);
  const [addingDocument, setAddingDocument] = useState(false);
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
  const markedPendingCount = (documents ?? []).filter((d) => d.markedPending).length;
  const requiredCount = visible.filter((d) => d.required).length;
  const withFiles = (documents ?? []).filter((d) => d.latestVersion && d.status !== "NOT_APPLICABLE");
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
          title="Etapa 1 · Documentos para comercialización"
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
        <p className="-mt-2 mb-3 text-xs text-muted">
          Lo necesario para publicar y promover el inmueble; no tienen que estar al corriente. Estado de cada uno: recibido, pendiente o excepción.
        </p>
        {markedPendingCount > 0 ? (
          <p className="mb-3 flex items-center gap-2 rounded-lg bg-info-bg px-3 py-2 text-sm text-info-text">
            <Clock className="h-4 w-4 shrink-0" aria-hidden />
            {markedPendingCount === 1 ? "1 documento está pendiente de entrega" : `${markedPendingCount} documentos están pendientes de entrega`}{" "}
            (marcados por el asesor; no bloquean el avance del expediente).
          </p>
        ) : null}
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
                          canException={can("DOCUMENT_EXCEPTION")}
                          canRequestUpdate={can("DOCUMENT_UPDATE_REQUEST")}
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
        {can("EXPEDIENT_EDIT") || can("DOCUMENT_UPLOAD") ? (
          <div className="mt-4">
            <Button variant="secondary" size="sm" onClick={() => setAddingDocument(true)}>
              <Plus className="h-4 w-4" aria-hidden /> Agregar otro documento
            </Button>
          </div>
        ) : null}
        <button type="button" className="mt-3 block text-xs text-dark-gold hover:underline" onClick={() => setShowOptional((v) => !v)}>
          {showOptional ? "Ocultar documentos que no aplican" : "Mostrar también documentos que no aplican a este expediente"}
        </button>
      </Card>

      <NotaryStagesPanel expedienteId={expediente.id} names={names} stamp={(documents ?? []).map((d) => `${d.id}:${d.currentVersionNumber}:${d.status}`).join("|")} />

      {addingDocument ? (
        <AddDocumentModal
          participants={[...participants].sort((a, b) => a.ordinal - b.ordinal).map((p) => ({ id: p.id, name: p.fullName }))}
          onCancel={() => setAddingDocument(false)}
          onConfirm={async (name, participantId) => {
            try {
              await addCustomDocument(expediente.id, name, participantId);
              showToast(`Se agregó "${name}" a los documentos del expediente.`);
              setAddingDocument(false);
              await onChanged();
            } catch (err) {
              showToast(errorText(err));
            }
          }}
        />
      ) : null}

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
  canException: boolean;
  canRequestUpdate: boolean;
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
  canException,
  canRequestUpdate,
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
  const [viewing, setViewing] = useState<{ id: string; title: string } | null>(null);
  const [versions, setVersions] = useState<DocumentVersionResponse[] | null>(null);
  const [exceptionOpen, setExceptionOpen] = useState(false);
  const [updateOpen, setUpdateOpen] = useState(false);
  const [pendingOpen, setPendingOpen] = useState(false);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [removing, setRemoving] = useState<"file" | "document" | null>(null);
  const [removeBusy, setRemoveBusy] = useState(false);
  const v = doc.latestVersion;
  const issues = v?.blockingIssues ?? [];
  const warnings = v?.warnings ?? [];
  const inProgress = doc.pipelineStatus ? IN_PROGRESS.includes(doc.pipelineStatus) : false;
  const reviewable = ["READY_FOR_REVIEW", "UPLOADED", "RETURNED"].includes(doc.status) && v && !["QUEUED", "PROCESSING"].includes(v.processingStatus);
  const mismatch = v?.aiTypeMatches === false;
  const docLabel = documentName(doc);
  const resolved = doc.status === "ACCEPTED" || doc.status === "NOT_APPLICABLE";

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

  const toggleVersions = async () => {
    if (versions) return setVersions(null);
    try {
      setVersions([...(await listVersions(doc.id))].sort((a, b) => b.versionNumber - a.versionNumber));
    } catch (err) {
      showToast(errorText(err));
    }
  };

  const moveTargets = allDocuments.filter((d) => d.id !== doc.id && d.status !== "ACCEPTED");

  const confirmRemove = async () => {
    setRemoveBusy(true);
    try {
      if (removing === "document") {
        await deleteCustomDocument(doc.id);
        showToast(`Se quitó ${docLabel} del expediente.`);
        await onChanged();
      } else {
        const updated = await removeDocumentFile(doc.id);
        showToast("Archivo eliminado: el documento quedó pendiente y puedes subirlo de nuevo.");
        await onChanged(updated);
      }
      setRemoving(null);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setRemoveBusy(false);
    }
  };

  /** Control de Cambios #18: corrige el nombre de un documento agregado a mano (el archivo no cambia). */
  const saveName = async () => {
    const name = (renaming ?? "").trim();
    if (name.length < 3 || name === doc.customName) {
      setRenaming(null);
      return;
    }
    await act(() => renameDocument(doc.id, name), "Nombre actualizado.");
    setRenaming(null);
  };

  return (
    <div className="rounded-xl border border-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          {renaming !== null ? (
            <div className="flex flex-wrap items-center gap-2">
              <input
                autoFocus
                maxLength={120}
                value={renaming}
                onChange={(e) => setRenaming(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") void saveName();
                  if (e.key === "Escape") setRenaming(null);
                }}
                aria-label="Nombre del documento"
                className="min-h-9 min-w-0 flex-1 rounded-lg border border-border bg-card px-2 text-sm text-obsessed outline-none focus:border-gold"
              />
              <Button size="sm" onClick={saveName} disabled={(renaming ?? "").trim().length < 3}>
                <Check className="h-4 w-4" aria-hidden /> Guardar
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setRenaming(null)}>
                Cancelar
              </Button>
            </div>
          ) : (
            <p className="flex flex-wrap items-center gap-1.5 text-sm font-medium text-obsessed">
              <span>
                {docLabel}
                {participantName ? <span className="text-muted"> — {participantName}</span> : null}
              </span>
              {doc.customName && canUpload ? (
                <button
                  type="button"
                  onClick={() => setRenaming(doc.customName ?? "")}
                  className="inline-flex items-center gap-1 rounded px-1 text-xs font-normal text-dark-gold hover:underline"
                  aria-label={`Editar el nombre de ${docLabel}`}
                >
                  <Pencil className="h-3.5 w-3.5" aria-hidden /> Editar nombre
                </button>
              ) : null}
            </p>
          )}
          {doc.type === "PROOF_OF_ADDRESS" ? (
            <AddressProofKindLine document={doc} canEdit={canUpload || canAccept} onChanged={onChanged} />
          ) : null}
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
          {doc.required && doc.deferred && doc.status === "PENDING" && !doc.exceptionAt && !doc.markedPending ? <Badge tone="neutral">Para después</Badge> : null}
          {doc.markedPending ? <Badge tone="info">Pendiente de entrega</Badge> : null}
          {doc.exceptionAt && doc.status !== "ACCEPTED" ? <Badge tone="info">Excepción aprobada</Badge> : null}
          {doc.updateRequestedAt ? <Badge tone="warning">Requiere actualización</Badge> : null}
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
      {doc.exceptionAt && doc.status !== "ACCEPTED" ? (
        <p className="mt-2 rounded-lg bg-app-bg px-3 py-2 text-sm text-muted">
          Excepción aprobada el {formatDateTime(doc.exceptionAt)}: {doc.exceptionNote}. No bloquea la aprobación ni el contrato; el cliente puede
          entregarlo después desde su liga.
        </p>
      ) : null}
      {doc.markedPending ? (
        <p className="mt-2 rounded-lg bg-info-bg px-3 py-2 text-sm text-info-text">
          Pendiente de entrega desde el {formatDateTime(doc.pendingAt)}
          {doc.pendingDueDate ? ` · entrega estimada: ${formatDate(doc.pendingDueDate)}` : ""}
          {doc.pendingNote ? ` · ${doc.pendingNote}` : ""}. No bloquea el avance; al subirse el archivo queda como entregado.
        </p>
      ) : null}
      {doc.updateRequestedAt ? (
        <p className="mt-2 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
          Se pidió una versión actualizada el {formatDateTime(doc.updateRequestedAt)}: {doc.updateRequestNote}. La versión actual se conserva en el
          historial.
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
          <Button variant="secondary" size="sm" onClick={() => setViewing({ id: v.id, title: `${docLabel} · versión ${v.versionNumber}` })}>
            <Eye className="h-4 w-4" aria-hidden /> Ver archivo
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
        {canUpload ? (
          <Button variant="ghost" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
            {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />}
            {uploading
              ? progress !== null && progress < 100
                ? `Subiendo ${progress}%`
                : "Preparando…"
              : v
                ? doc.status === "ACCEPTED"
                  ? "Reemplazar archivo"
                  : "Cargar nueva versión"
                : "Cargar en nombre del cliente"}
          </Button>
        ) : null}
        {canUpload && v ? (
          <Button variant="ghost" size="sm" onClick={() => setRemoving("file")}>
            <Trash2 className="h-4 w-4" aria-hidden /> Eliminar archivo
          </Button>
        ) : null}
        {canUpload && doc.customName ? (
          <Button variant="ghost" size="sm" onClick={() => setRemoving("document")}>
            <Trash2 className="h-4 w-4" aria-hidden /> Quitar documento
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
        {(canUpload || canAccept) && !doc.markedPending && !resolved ? (
          <Button variant="ghost" size="sm" onClick={() => setPendingOpen(true)}>
            <Clock className="h-4 w-4" aria-hidden /> Marcar como pendiente…
          </Button>
        ) : null}
        {(canUpload || canAccept) && doc.markedPending ? (
          <Button variant="ghost" size="sm" onClick={() => act(() => clearDocumentPending(doc.id), "Se quitó la marca de pendiente.")}>
            Quitar pendiente
          </Button>
        ) : null}
        {canException && doc.required && !doc.exceptionAt && !resolved ? (
          <Button variant="ghost" size="sm" onClick={() => setExceptionOpen(true)}>
            Aprobar excepción…
          </Button>
        ) : null}
        {canException && doc.exceptionAt && doc.status !== "ACCEPTED" ? (
          <Button variant="ghost" size="sm" onClick={() => act(() => revokeException(doc.id), "Se retiró la excepción: el documento vuelve a ser necesario.")}>
            Retirar excepción
          </Button>
        ) : null}
        {canRequestUpdate && v && !doc.updateRequestedAt && doc.status !== "NOT_APPLICABLE" ? (
          <Button variant="ghost" size="sm" onClick={() => setUpdateOpen(true)}>
            Requiere actualización…
          </Button>
        ) : null}
        {canRequestUpdate && doc.updateRequestedAt ? (
          <Button variant="ghost" size="sm" onClick={() => act(() => cancelDocumentUpdate(doc.id), "Se canceló la solicitud de actualización.")}>
            Cancelar actualización
          </Button>
        ) : null}
        {doc.currentVersionNumber > 0 ? (
          <Button variant="ghost" size="sm" onClick={toggleVersions}>
            <Layers className="h-4 w-4" aria-hidden /> Versiones ({doc.currentVersionNumber})
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

      {versions ? (
        <ul className="mt-3 flex flex-col gap-1 rounded-lg bg-app-bg p-3 text-xs text-muted" aria-label="Versiones del documento">
          {versions.map((ver) => (
            <li key={ver.id} className="flex flex-wrap items-center justify-between gap-2">
              <span>
                <strong className="text-obsessed">V{ver.versionNumber}</strong>
                {ver.id === v?.id ? <span className="ml-1.5 font-medium text-success-text">(activa)</span> : null}
                {ver.removedAt ? <span className="ml-1.5 font-medium text-danger-text">(eliminada {formatDateTime(ver.removedAt)})</span> : null} · cargada{" "}
                {formatDateTime(ver.uploadedAt)} por {ver.uploadedVia === "PUBLIC_PORTAL" ? "el cliente" : ver.uploadedByName ?? "el staff"}
              </span>
              <button
                type="button"
                className="font-medium text-dark-gold hover:underline"
                onClick={() => setViewing({ id: ver.id, title: `${docLabel} · versión ${ver.versionNumber}` })}
              >
                Ver
              </button>
            </li>
          ))}
          <li className="pt-1">Las versiones anteriores y las eliminadas se conservan para trazabilidad y auditoría.</li>
        </ul>
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

      <Modal
        open={removing !== null}
        onClose={() => setRemoving(null)}
        title={removing === "document" ? `Quitar ${docLabel}` : `Eliminar el archivo de ${docLabel}`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRemoving(null)} disabled={removeBusy}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={confirmRemove} disabled={removeBusy}>
              {removeBusy ? "Eliminando…" : removing === "document" ? "Quitar documento" : "Eliminar archivo"}
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted">
          {removing === "document"
            ? "Se quita del expediente con sus archivos. Si lo necesitas después, agrégalo de nuevo."
            : `El documento vuelve a pendiente${doc.status === "ACCEPTED" ? " (aunque ya estaba aceptado)" : ""} y puedes subirlo de nuevo cuando quieras, tú o el cliente desde su liga. Lo leído de este archivo deja de usarse; la versión queda en el historial.`}
        </p>
      </Modal>

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
              `El archivo se movió a ${target ? documentName(target) : "otro documento"} y se está leyendo con su tipo correcto.`,
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

      <DocumentViewerModal key={viewing?.id ?? "closed"} versionId={viewing?.id ?? null} title={viewing?.title ?? ""} onClose={() => setViewing(null)} />

      <ReasonModal
        open={exceptionOpen}
        title={`Aprobar excepción: ${docLabel}`}
        description="El documento sigue faltando, pero no bloqueará la aprobación de la documentación ni el contrato. El cliente podrá entregarlo después desde su liga."
        label="Nota justificatoria"
        placeholder="Ej. La boleta está en trámite en el Registro Público; la entrega la próxima semana."
        minLength={10}
        confirmLabel="Aprobar excepción"
        onCancel={() => setExceptionOpen(false)}
        onConfirm={async (note) => {
          await act(() => approveException(doc.id, note), "Excepción aprobada (queda en el historial).");
          setExceptionOpen(false);
        }}
      />

      {pendingOpen ? (
        <PendingModal
          documentLabel={docLabel}
          onCancel={() => setPendingOpen(false)}
          onConfirm={async (note, dueDate) => {
            await act(() => markDocumentPending(doc.id, note, dueDate), "Marcado como pendiente de entrega (no bloquea el expediente).");
            setPendingOpen(false);
          }}
        />
      ) : null}

      <ReasonModal
        open={updateOpen}
        title={`Pedir versión actualizada: ${docLabel}`}
        description="El cliente verá esta nota en su liga y podrá subir la nueva versión. La versión actual se conserva como historial."
        label="¿Qué debe actualizar?"
        placeholder="Ej. La constancia tiene más de 3 meses; súbela con fecha reciente."
        minLength={10}
        confirmLabel="Pedir actualización"
        onCancel={() => setUpdateOpen(false)}
        onConfirm={async (note) => {
          await act(() => requestDocumentUpdate(doc.id, note), "Se pidió la versión actualizada al cliente.");
          setUpdateOpen(false);
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
  const alternatives = f.alternatives ?? [];
  const choosing = alternatives.length > 1 && f.confirmedValue === null;

  const save = async (chosen?: string) => {
    const final = (chosen ?? value).trim();
    if (!final) return;
    setSaving(true);
    try {
      onEdited(await confirmField(f.id, final));
      setEditing(false);
      showToast(chosen ? "Valor elegido: queda registrado con tu nombre." : "Dato confirmado.");
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
            <button type="button" aria-label="Guardar dato" onClick={() => save()} disabled={saving} className="text-success-text">
              {saving ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Check className="h-4 w-4" aria-hidden />}
            </button>
            <button type="button" aria-label="Cancelar" onClick={() => setEditing(false)} className="text-muted">
              <X className="h-4 w-4" aria-hidden />
            </button>
          </div>
        ) : (
          <p className="text-sm break-words text-obsessed">{f.confirmedValue ?? f.detectedValue ?? "—"}</p>
        )}
        {choosing ? (
          // Control de Cambios #19: la escritura trae varios valores (antecedentes); una persona elige el del instrumento actual.
          <div className="mt-2 rounded-lg border border-warning-text/30 bg-warning-bg p-2.5">
            <p className="flex items-start gap-1.5 text-xs font-medium text-warning-text">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
              Se encontraron varios valores en el documento. ¿Cuál corresponde a este instrumento?
            </p>
            <ul className="mt-2 flex flex-col gap-1.5">
              {alternatives.map((a) => (
                <li key={a.value} className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-card px-2 py-1.5">
                  <span className="min-w-0 text-sm text-obsessed">
                    <strong className="break-words">{a.value}</strong>
                    {a.context ? <span className="text-xs text-muted"> — {a.context}</span> : null}
                    {a.page ? <span className="text-xs text-muted"> · pág. {a.page}</span> : null}
                  </span>
                  {canEdit ? (
                    <Button size="sm" variant="secondary" disabled={saving} onClick={() => save(a.value)}>
                      Es este
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
        {f.confirmedValue !== null && f.confirmedByName ? (
          <p className="mt-0.5 text-xs text-success-text">
            Confirmado por {f.confirmedByName}
            {f.confirmedAt ? ` · ${formatDateTime(f.confirmedAt)}` : ""}
          </p>
        ) : null}
      </div>
      <div className="flex items-center gap-2">
        <Badge tone={f.confirmedValue !== null ? "success" : choosing ? "warning" : confidence.tone}>
          {f.confirmedValue !== null ? "confirmado por el staff" : choosing ? "elegir valor" : confidence.text}
        </Badge>
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
        El archivo cargado como <strong>{documentName(doc)}</strong> pasa al documento que elijas (sin que el cliente lo vuelva a subir) y se
        lee otra vez con ese tipo. {documentName(doc)} vuelve a quedar pendiente.
      </p>
      <label className="mb-1 block text-sm font-medium text-obsessed">¿A qué documento corresponde?</label>
      <select
        value={target}
        onChange={(e) => setTarget(e.target.value)}
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm"
      >
        {targets.map((d) => (
          <option key={d.id} value={d.id}>
            {documentName(d)}
            {d.participantId && names[d.participantId] ? ` — ${names[d.participantId]}` : ""}
            {d.latestVersion ? " (ya tiene archivo: se agrega como versión nueva)" : ""}
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

/** Control de Cambios #1: documento que no está en el listado, con el nombre que el usuario le dé. */
function AddDocumentModal({
  participants,
  onCancel,
  onConfirm,
}: {
  participants: { id: string; name: string }[];
  onCancel: () => void;
  onConfirm: (name: string, participantId: string | null) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [owner, setOwner] = useState("");
  const [busy, setBusy] = useState(false);
  const valid = name.trim().length >= 3;
  return (
    <Modal
      open
      onClose={onCancel}
      title="Agregar otro documento"
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            Cancelar
          </Button>
          <Button
            disabled={!valid || busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm(name.trim(), owner || null);
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Plus className="h-4 w-4" aria-hidden />} Agregar
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-muted">
          Para un documento que no está en la lista. Se guarda en el expediente igual que los demás y se le pide al cliente en su liga.
        </p>
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">Nombre del documento</span>
          <input
            autoFocus
            maxLength={120}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Ej. Constancia de no adeudo de mantenimiento"
            className="min-h-10 rounded-lg border border-border bg-card px-3 text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
          />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">¿De quién es?</span>
          <select
            value={owner}
            onChange={(e) => setOwner(e.target.value)}
            className="min-h-10 rounded-lg border border-border bg-card px-3 text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
          >
            <option value="">Del inmueble / del expediente</option>
            {participants.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
      </div>
    </Modal>
  );
}

/** Control de Cambios #13: qué comprobante de domicilio se entregó (y los que se aceptan). */
function AddressProofKindLine({
  document: doc,
  canEdit,
  onChanged,
}: {
  document: DocumentResponse;
  canEdit: boolean;
  onChanged: (updated?: DocumentResponse) => Promise<void>;
}) {
  const { showToast } = useToast();
  const [busy, setBusy] = useState(false);
  const change = async (kind: string) => {
    setBusy(true);
    try {
      await onChanged(await setAddressProofKind(doc.id, (kind || null) as AddressProofKind | null));
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="mt-1 flex flex-col gap-1 text-xs text-muted">
      <p>Se acepta: recibo de luz, estado de cuenta bancario, recibo de Telmex, de telefonía o de internet.</p>
      {canEdit ? (
        <label className="flex flex-wrap items-center gap-2">
          <span>Tipo entregado:</span>
          <select
            value={doc.addressProofKind ?? ""}
            disabled={busy}
            onChange={(e) => change(e.target.value)}
            className="min-h-8 rounded-lg border border-border bg-card px-2 text-xs text-obsessed"
          >
            <option value="">Sin indicar</option>
            {ADDRESS_PROOF_KINDS.map((k) => (
              <option key={k} value={k}>
                {ADDRESS_PROOF_KIND_LABELS[k]}
              </option>
            ))}
          </select>
        </label>
      ) : (
        <p>Tipo entregado: {doc.addressProofKind ? ADDRESS_PROOF_KIND_LABELS[doc.addressProofKind] : "sin indicar"}</p>
      )}
    </div>
  );
}

/** Control de Cambios #14: "Marcar como pendiente" con nota y fecha estimada opcionales. */
function PendingModal({
  documentLabel,
  onCancel,
  onConfirm,
}: {
  documentLabel: string;
  onCancel: () => void;
  onConfirm: (note: string, dueDate: string | null) => Promise<void>;
}) {
  const [note, setNote] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [busy, setBusy] = useState(false);
  return (
    <Modal
      open
      onClose={onCancel}
      title={`Marcar como pendiente: ${documentLabel}`}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            Cancelar
          </Button>
          <Button
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm(note.trim(), dueDate || null);
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Clock className="h-4 w-4" aria-hidden />} Marcar pendiente
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-muted">
          El documento todavía no está listo pero se entregará después. No bloquea el avance del expediente y, al subirse, pasa a entregado.
        </p>
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">Nota (opcional)</span>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            maxLength={1000}
            placeholder="Ej. El cliente lo tramita en el municipio."
            className="rounded-lg border border-border bg-card px-3 py-2 text-obsessed outline-none focus:border-gold"
          />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">Fecha estimada de entrega (opcional)</span>
          <input
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            className="min-h-10 rounded-lg border border-border bg-card px-3 text-obsessed"
          />
        </label>
      </div>
    </Modal>
  );
}
