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
  rejectDocument,
  requestAgain,
  returnDocument,
  uploadVersion,
} from "@/lib/api/documents";
import { getExtractedFields } from "@/lib/api/extraction";
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
import { documentStatusLabels, documentStatusTone, extractedFieldLabels, formatDateTime, label, returnReasonLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, Bot, Download, History, Loader2, Mail, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ExpedienteContext } from "./page";

const processingLabels: Record<string, string> = {
  QUEUED: "En cola para procesar",
  PROCESSING: "Procesando",
  QUALITY_FAILED: "No pasó la verificación de calidad",
  PROCESSED: "Procesado",
  FAILED: "Falló el procesamiento",
};

export function DocumentsPanel({ expediente, participants, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [documents, setDocuments] = useState<DocumentResponse[] | null>(null);
  const [emailPreview, setEmailPreview] = useState<EmailPreviewResponse | null>(null);
  const [showOptional, setShowOptional] = useState(false);
  const names = Object.fromEntries(participants.map((p) => [p.id, p.fullName]));

  const load = useCallback(() => {
    listDocuments(expediente.id)
      .then(setDocuments)
      .catch((err) => showToast(errorText(err)));
  }, [expediente.id, showToast]);

  useEffect(() => {
    load();
  }, [load]);

  // Mientras haya archivos procesándose, se actualiza solo.
  useEffect(() => {
    const processing = documents?.some((d) => d.latestVersion && ["QUEUED", "PROCESSING"].includes(d.latestVersion.processingStatus));
    if (!processing) return;
    const timer = setTimeout(load, 4000);
    return () => clearTimeout(timer);
  }, [documents, load]);

  const onChanged = async (updated?: DocumentResponse) => {
    if (updated) setDocuments((prev) => prev?.map((d) => (d.id === updated.id ? updated : d)) ?? null);
    load();
    await reload();
  };

  const visible = (documents ?? []).filter((d) => d.required || d.currentVersionNumber > 0 || d.status === "NOT_APPLICABLE" || showOptional);
  const pendingCount = visible.filter((d) => d.required && d.status !== "ACCEPTED" && d.status !== "NOT_APPLICABLE").length;

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Documentos"
          description={
            pendingCount > 0
              ? `Faltan ${pendingCount} documento(s) obligatorio(s) por aceptar o marcar como "No aplica".`
              : "Todos los documentos obligatorios están resueltos."
          }
        />
        {documents === null ? (
          <p className="text-sm text-muted">Cargando documentos…</p>
        ) : (
          <div className="flex flex-col gap-3">
            {visible.map((doc) => (
              <DocumentRow
                key={doc.id}
                document={doc}
                participantName={doc.participantId ? names[doc.participantId] : undefined}
                onChanged={onChanged}
                canUpload={can("DOCUMENT_UPLOAD")}
                canAccept={can("DOCUMENT_ACCEPT")}
                canReturn={can("DOCUMENT_RETURN")}
                canReject={can("DOCUMENT_REJECT")}
                canOverride={can("DOCUMENT_QUALITY_OVERRIDE")}
                canMarkNotApplicable={can("DOCUMENT_MARK_NOT_APPLICABLE")}
              />
            ))}
          </div>
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
  participantName?: string;
  onChanged: (updated?: DocumentResponse) => Promise<void>;
  canUpload: boolean;
  canAccept: boolean;
  canReturn: boolean;
  canReject: boolean;
  canOverride: boolean;
  canMarkNotApplicable: boolean;
}

function DocumentRow({
  document: doc,
  participantName,
  onChanged,
  canUpload,
  canAccept,
  canReturn,
  canReject,
  canOverride,
  canMarkNotApplicable,
}: RowProps) {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [fields, setFields] = useState<ExtractedFieldObservationResponse[] | null>(null);
  const [history, setHistory] = useState<ReviewHistoryResponse[] | null>(null);
  const [review, setReview] = useState<"return" | "reject" | null>(null);
  const [overriding, setOverriding] = useState(false);
  const [notApplicable, setNotApplicable] = useState(false);
  const v = doc.latestVersion;
  const issues = v?.blockingIssues ?? [];
  const reviewable = ["READY_FOR_REVIEW", "UPLOADED", "RETURNED"].includes(doc.status) && v && !["QUEUED", "PROCESSING"].includes(v.processingStatus);

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
        showToast("Este archivo tiene alertas: devuélvelo al cliente o pide a un director o administrador que autorice la excepción.");
      }
      return;
    }
    act(() => acceptDocument(doc.id), "Documento aceptado.");
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploading(true);
    try {
      await uploadVersion(doc.id, Array.from(files));
      showToast("Archivo cargado; se está verificando su calidad.");
      await onChanged();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
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

  return (
    <div className="rounded-xl border border-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-obsessed">
            {documentTypeLabel(doc.type)}
            {participantName ? <span className="text-muted"> — {participantName}</span> : null}
          </p>
          <p className="text-xs text-muted">
            {doc.required ? "Obligatorio" : "Opcional / no aplica en este caso"}
            {v ? ` · Versión ${v.versionNumber}, cargada ${formatDateTime(v.uploadedAt)} por ${v.uploadedVia === "PUBLIC_PORTAL" ? "el cliente" : v.uploadedByName ?? "el staff"}` : ""}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {v && v.processingStatus !== "PROCESSED" ? (
            <Badge tone={v.processingStatus === "QUALITY_FAILED" || v.processingStatus === "FAILED" ? "danger" : "info"}>
              {processingLabels[v.processingStatus]}
            </Badge>
          ) : null}
          <Badge tone={documentStatusTone[doc.status]}>{documentStatusLabels[doc.status]}</Badge>
        </div>
      </div>

      {issues.length > 0 && doc.status !== "ACCEPTED" ? (
        <div className="mt-3 rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">
          <p className="flex items-center gap-1.5 font-medium">
            <AlertTriangle className="h-4 w-4" aria-hidden /> No se puede aceptar sin autorización de excepción
          </p>
          <ul className="ml-5 mt-1 list-disc">
            {issues.map((issue) => (
              <li key={issue}>{issue}</li>
            ))}
          </ul>
        </div>
      ) : null}
      {v?.aiObservations && (v.aiTypeMatches === false || v.aiLegible === false) ? (
        <p className="mt-2 flex items-start gap-1.5 text-xs text-muted">
          <Bot className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden /> Revisión automática: {v.aiObservations}
        </p>
      ) : null}
      {v && v.processingStatus === "PROCESSED" && v.aiTypeMatches === null && v.aiLegible === null ? (
        <p className="mt-2 text-xs text-muted">La revisión automática del contenido no está disponible para este archivo; revísalo visualmente.</p>
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
        <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,application/pdf" multiple className="hidden" onChange={handleUpload} />
        {canUpload && doc.status !== "NOT_APPLICABLE" && doc.status !== "ACCEPTED" ? (
          <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
            {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />}
            {doc.currentVersionNumber > 0 ? "Cargar nueva versión" : "Cargar en nombre del cliente"}
          </Button>
        ) : null}
        {v ? (
          <Button variant="secondary" size="sm" onClick={handleDownload}>
            <Download className="h-4 w-4" aria-hidden /> Ver archivo
          </Button>
        ) : null}
        {v ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={async () => {
              if (fields) return setFields(null);
              try {
                setFields(await getExtractedFields(doc.id));
              } catch (err) {
                showToast(errorText(err));
              }
            }}
          >
            {fields ? "Ocultar datos leídos" : "Datos leídos del documento"}
          </Button>
        ) : null}
        {reviewable && canAccept ? (
          <Button size="sm" onClick={handleAccept}>
            {issues.length > 0 ? "Aceptar por excepción…" : "Aceptar"}
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
        {canMarkNotApplicable && doc.status !== "ACCEPTED" && doc.status !== "NOT_APPLICABLE" ? (
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

      {fields ? (
        fields.length === 0 ? (
          <p className="mt-3 text-xs text-muted">No se leyeron datos de este archivo (o este tipo de documento no tiene lectura automática).</p>
        ) : (
          <dl className="mt-3 grid grid-cols-1 gap-2 rounded-lg bg-app-bg p-3 sm:grid-cols-2">
            {fields.map((f) => (
              <div key={f.id}>
                <dt className="text-xs text-muted">{extractedFieldLabels[f.fieldName] ?? f.fieldName}</dt>
                <dd className="text-sm text-obsessed">{f.confirmedValue ?? f.detectedValue ?? "—"}</dd>
              </div>
            ))}
          </dl>
        )
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
          documentLabel={documentTypeLabel(doc.type)}
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

      <ReasonModal
        open={overriding}
        title="Aceptar por excepción"
        description={
          <>
            <p className="mb-2">Este archivo tiene alertas:</p>
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
        title={`"${documentTypeLabel(doc.type)}" no aplica`}
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
