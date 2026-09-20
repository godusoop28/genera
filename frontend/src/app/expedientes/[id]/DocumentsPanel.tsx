"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import {
  acceptDocument,
  getDownloadUrl,
  getProcessingStatus,
  listDocuments,
  listVersions,
  rejectDocument,
  returnDocument,
  uploadVersion,
} from "@/lib/api/documents";
import { generateContract, getContractData, listContracts, markContractDelivered, markContractSigned } from "@/lib/api/contracts";
import { getExtractedFields } from "@/lib/api/extraction";
import { getDocumentsEmailPreview } from "@/lib/api/notifications";
import type {
  ContractCalculationsResponse,
  ContractGenerationResponse,
  DocumentResponse,
  DocumentVersionResponse,
  EmailPreviewResponse,
  ExtractedFieldObservationResponse,
  ReturnReasonCode,
} from "@/lib/api/types";
import { Modal } from "@/components/ui/Modal";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { Download, FileText, Loader2, Mail, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

const processingLabels: Record<DocumentVersionResponse["processingStatus"], string> = {
  QUEUED: "En cola",
  PROCESSING: "Procesando",
  QUALITY_FAILED: "Calidad insuficiente",
  PROCESSED: "Procesado",
  FAILED: "Falló el procesamiento",
};

const processingTone: Record<DocumentVersionResponse["processingStatus"], "neutral" | "info" | "warning" | "success" | "danger"> = {
  QUEUED: "neutral",
  PROCESSING: "info",
  QUALITY_FAILED: "warning",
  PROCESSED: "success",
  FAILED: "danger",
};

const documentStatusLabels: Record<DocumentResponse["status"], string> = {
  PENDING: "Pendiente",
  UPLOADED: "Subido",
  READY_FOR_REVIEW: "Listo para revisión",
  ACCEPTED: "Aceptado",
  RETURNED: "Devuelto",
  REJECTED: "Rechazado",
  REPLACED: "Reemplazado",
};

const documentStatusTone: Record<DocumentResponse["status"], "neutral" | "info" | "warning" | "success" | "danger"> = {
  PENDING: "neutral",
  UPLOADED: "info",
  READY_FOR_REVIEW: "info",
  ACCEPTED: "success",
  RETURNED: "warning",
  REJECTED: "danger",
  REPLACED: "neutral",
};

const returnReasons: { value: ReturnReasonCode; label: string }[] = [
  { value: "BLURRY_IMAGE", label: "Imagen borrosa" },
  { value: "INCOMPLETE_DOCUMENT", label: "Documento incompleto" },
  { value: "EXPIRED_DOCUMENT", label: "Documento vencido" },
  { value: "ILLEGIBLE_INFORMATION", label: "Información ilegible" },
  { value: "WRONG_DOCUMENT", label: "Documento equivocado" },
  { value: "MISSING_PAGE", label: "Falta una página" },
  { value: "OTHER", label: "Otro" },
];

export function DocumentsPanel({ expedienteId }: { expedienteId: string }) {
  const { showToast } = useToast();
  const [documents, setDocuments] = useState<DocumentResponse[] | null>(null);
  const [contractData, setContractData] = useState<ContractCalculationsResponse | null>(null);
  const [contracts, setContracts] = useState<ContractGenerationResponse[]>([]);
  const [generatingContract, setGeneratingContract] = useState(false);
  const [emailPreview, setEmailPreview] = useState<EmailPreviewResponse | null>(null);
  const [loadingEmailPreview, setLoadingEmailPreview] = useState(false);

  const loadDocuments = useCallback(() => {
    listDocuments(expedienteId).then(setDocuments).catch(() => undefined);
  }, [expedienteId]);

  const loadContracts = useCallback(() => {
    listContracts(expedienteId).then(setContracts).catch(() => undefined);
  }, [expedienteId]);

  useEffect(() => {
    loadDocuments();
    loadContracts();
    getContractData(expedienteId)
      .then(setContractData)
      .catch(() => undefined);
  }, [expedienteId, loadDocuments, loadContracts]);

  const handleGenerateContract = async () => {
    setGeneratingContract(true);
    try {
      await generateContract(expedienteId);
      showToast("Contrato generado.");
      loadContracts();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo generar el contrato (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setGeneratingContract(false);
    }
  };

  const handleMarkSigned = async (contractId: string) => {
    try {
      await markContractSigned(contractId);
      showToast("Contrato marcado como firmado.");
      loadContracts();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo actualizar (${err.status}).` : "Error de conexión.");
    }
  };

  const handleMarkDelivered = async (contractId: string) => {
    try {
      await markContractDelivered(contractId, "MANUAL");
      showToast("Contrato marcado como entregado.");
      loadContracts();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo actualizar (${err.status}).` : "Error de conexión.");
    }
  };

  const handleOpenEmailPreview = async () => {
    setLoadingEmailPreview(true);
    try {
      const preview = await getDocumentsEmailPreview(expedienteId);
      setEmailPreview(preview);
    } catch (err) {
      showToast(
        err instanceof ApiError
          ? `No se pudo armar la vista previa (${err.status}): ${err.message}`
          : "Error de conexión.",
      );
    } finally {
      setLoadingEmailPreview(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader title="Documentos" description="Cada renglón es un requisito del expediente; sube el archivo correspondiente." />
        {documents === null ? (
          <p className="text-sm text-muted">Cargando documentos…</p>
        ) : documents.length === 0 ? (
          <p className="text-sm text-muted">Sin documentos requeridos.</p>
        ) : (
          <div className="flex flex-col gap-3">
            {documents.map((doc) => (
              <DocumentRow key={doc.id} document={doc} onChanged={loadDocuments} />
            ))}
          </div>
        )}
      </Card>

      <Card>
        <CardHeader title="Contrato" description="Snapshot inmutable: si el expediente cambia después, los contratos ya generados no cambian." />
        {contractData ? (
          <dl className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Field label="Precio" value={contractData.price} />
            <Field label="Comisión" value={contractData.commission} />
            <Field label="IVA" value={contractData.vat} />
            <Field label="Comisión + IVA" value={contractData.totalCommissionWithVat} />
            <Field label="Pena convencional" value={contractData.penalty} />
            <Field label="Exclusividad" value={`${contractData.exclusivityDays} días (hasta ${contractData.exclusivityEndDate})`} />
          </dl>
        ) : (
          <p className="mb-4 text-sm text-muted">No se pudieron cargar los cálculos del contrato todavía.</p>
        )}

        <Button onClick={handleGenerateContract} disabled={generatingContract} size="sm">
          {generatingContract ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <FileText className="h-4 w-4" aria-hidden />}
          Generar contrato
        </Button>

        {contracts.length > 0 ? (
          <ul className="mt-4 flex flex-col gap-2">
            {contracts.map((c) => (
              <li key={c.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                <div>
                  <span className="font-medium text-obsessed">Versión {c.versionNumber}</span>
                  <span className="ml-2 text-xs text-muted">{new Date(c.generatedAt).toLocaleString("es-MX")}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={c.status === "DELIVERED" ? "success" : c.status === "SIGNED" ? "info" : "neutral"}>{c.status}</Badge>
                  {c.status === "GENERATED" ? (
                    <Button variant="secondary" size="sm" onClick={() => handleMarkSigned(c.id)}>
                      Marcar firmado
                    </Button>
                  ) : null}
                  {c.status === "SIGNED" ? (
                    <Button variant="secondary" size="sm" onClick={() => handleMarkDelivered(c.id)}>
                      Marcar entregado
                    </Button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        ) : null}
      </Card>

      <Card>
        <CardHeader
          title="Envío a notaría"
          description="El envío es manual: aquí solo se arma el correo con los documentos aceptados para que lo revises y lo mandes tú mismo."
        />
        <Button onClick={handleOpenEmailPreview} disabled={loadingEmailPreview} size="sm">
          {loadingEmailPreview ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Mail className="h-4 w-4" aria-hidden />}
          Vista previa de envío
        </Button>
      </Card>

      <Modal open={emailPreview !== null} onClose={() => setEmailPreview(null)} title="Vista previa de envío a notaría">
        {emailPreview ? (
          <div className="flex flex-col gap-4">
            <p className="text-xs text-muted">
              Esto no envía nada. Copia el contenido o descarga los documentos y envíalos tú mismo desde tu correo.
            </p>
            <Field label="Asunto" value={emailPreview.subject} />
            <div>
              <dt className="text-xs font-medium tracking-wide text-muted uppercase">Cuerpo</dt>
              <dd className="mt-0.5 whitespace-pre-wrap text-sm text-obsessed">{emailPreview.body}</dd>
            </div>
            <div>
              <dt className="mb-2 text-xs font-medium tracking-wide text-muted uppercase">Documentos adjuntos</dt>
              <ul className="flex flex-col gap-2">
                {emailPreview.attachments.map((a) => (
                  <li key={a.fileName} className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                    <span className="text-obsessed">{a.fileName}</span>
                    <a href={a.downloadUrl} target="_blank" rel="noreferrer" className="text-dark-gold hover:underline">
                      Descargar
                    </a>
                  </li>
                ))}
              </ul>
            </div>
            {emailPreview.documentTypesWithoutFile.length > 0 ? (
              <p className="rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
                {emailPreview.documentTypesWithoutFile.join(", ")} se{" "}
                {emailPreview.documentTypesWithoutFile.length > 1 ? "marcaron" : "marcó"} como aceptado(s) pero todavía no tiene(n) un
                archivo generado, así que no se puede adjuntar por ahora.
              </p>
            ) : null}
          </div>
        ) : null}
      </Modal>
    </div>
  );
}

function DocumentRow({ document, onChanged }: { document: DocumentResponse; onChanged: () => void }) {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [latestVersion, setLatestVersion] = useState<DocumentVersionResponse | null>(null);
  const [fields, setFields] = useState<ExtractedFieldObservationResponse[] | null>(null);
  const [showReviewForm, setShowReviewForm] = useState<"return" | "reject" | null>(null);
  const [reasonCode, setReasonCode] = useState<ReturnReasonCode>("BLURRY_IMAGE");
  const [comment, setComment] = useState("");
  const pollRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const loadLatestVersion = useCallback(() => {
    if (document.currentVersionNumber === 0) return;
    listVersions(document.id)
      .then((versions) => {
        const latest = versions.find((v) => v.versionNumber === document.currentVersionNumber) ?? versions[0] ?? null;
        setLatestVersion(latest);
      })
      .catch(() => undefined);
  }, [document.id, document.currentVersionNumber]);

  useEffect(() => {
    loadLatestVersion();
    return () => {
      if (pollRef.current) clearTimeout(pollRef.current);
    };
  }, [loadLatestVersion]);

  const pollProcessing = useCallback(
    (versionId: string, attemptsLeft: number) => {
      function tick(attempts: number) {
        if (attempts <= 0) return;
        pollRef.current = setTimeout(() => {
          getProcessingStatus(versionId)
            .then((version) => {
              setLatestVersion(version);
              if (version.processingStatus === "QUEUED" || version.processingStatus === "PROCESSING") {
                tick(attempts - 1);
              } else {
                onChanged();
              }
            })
            .catch(() => undefined);
        }, 3000);
      }
      tick(attemptsLeft);
    },
    [onChanged],
  );

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploading(true);
    try {
      const version = await uploadVersion(document.id, Array.from(files));
      setLatestVersion(version);
      showToast("Archivo subido, procesando…");
      pollProcessing(version.id, 8);
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo subir el archivo (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleShowFields = async () => {
    if (fields !== null) {
      setFields(null);
      return;
    }
    try {
      const result = await getExtractedFields(document.id);
      setFields(result);
    } catch {
      showToast("No se pudieron cargar los campos extraídos.");
    }
  };

  const handleDownload = async () => {
    if (!latestVersion) return;
    // Se abre la pestaña de forma síncrona (dentro del gesto de clic) para que el
    // navegador no la bloquee como popup; se navega a la URL real una vez llega.
    // No se usa "noopener" aquí: se necesita la referencia para fijar la URL después.
    const tab = window.open("about:blank", "_blank");
    try {
      const { url } = await getDownloadUrl(latestVersion.id);
      if (tab) tab.location.href = url;
    } catch (err) {
      tab?.close();
      showToast(err instanceof ApiError ? `No se pudo generar la descarga (${err.status}): ${err.message}` : "Error de conexión.");
    }
  };

  const handleAccept = async () => {
    try {
      await acceptDocument(document.id);
      showToast("Documento aceptado.");
      onChanged();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo aceptar (${err.status}): ${err.message}` : "Error de conexión.");
    }
  };

  const handleReviewSubmit = async () => {
    try {
      if (showReviewForm === "return") {
        await returnDocument(document.id, reasonCode, comment);
        showToast("Documento devuelto para corrección.");
      } else if (showReviewForm === "reject") {
        await rejectDocument(document.id, reasonCode, comment);
        showToast("Documento rechazado.");
      }
      setShowReviewForm(null);
      setComment("");
      onChanged();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo enviar la decisión (${err.status}): ${err.message}` : "Error de conexión.");
    }
  };

  const canReview = document.status === "READY_FOR_REVIEW" || document.status === "UPLOADED";
  const canDownload = latestVersion?.processingStatus === "PROCESSED";

  return (
    <div className="rounded-lg border border-border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <span className="text-sm font-medium text-obsessed">{documentTypeLabel(document.type)}</span>
          {!document.required ? <span className="ml-2 text-xs text-muted">(condicional)</span> : null}
        </div>
        <div className="flex items-center gap-2">
          {latestVersion ? (
            <Badge tone={processingTone[latestVersion.processingStatus]}>{processingLabels[latestVersion.processingStatus]}</Badge>
          ) : null}
          <Badge tone={documentStatusTone[document.status]}>{documentStatusLabels[document.status]}</Badge>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,application/pdf" className="hidden" onChange={handleFileSelected} />
        <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
          {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />}
          {document.currentVersionNumber > 0 ? "Subir nueva versión" : "Subir archivo"}
        </Button>

        {canDownload ? (
          <Button variant="secondary" size="sm" onClick={handleDownload}>
            <Download className="h-4 w-4" aria-hidden />
            Descargar
          </Button>
        ) : null}

        <Button variant="ghost" size="sm" onClick={handleShowFields}>
          {fields ? "Ocultar datos extraídos" : "Ver datos extraídos"}
        </Button>

        {canReview ? (
          <>
            <Button variant="secondary" size="sm" onClick={handleAccept}>
              Aceptar
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setShowReviewForm("return")}>
              Devolver
            </Button>
            <Button variant="danger" size="sm" onClick={() => setShowReviewForm("reject")}>
              Rechazar
            </Button>
          </>
        ) : null}
      </div>

      {fields ? (
        fields.length === 0 ? (
          <p className="mt-3 text-xs text-muted">Sin campos extraídos todavía (o el documento no aplica para extracción automática).</p>
        ) : (
          <dl className="mt-3 grid grid-cols-1 gap-2 rounded-lg bg-app-bg p-3 sm:grid-cols-2">
            {fields.map((f) => (
              <div key={f.id}>
                <dt className="text-xs font-medium tracking-wide text-muted uppercase">{f.fieldName}</dt>
                <dd className="text-sm text-obsessed">{f.confirmedValue ?? f.detectedValue ?? "—"}</dd>
              </div>
            ))}
          </dl>
        )
      ) : null}

      {showReviewForm ? (
        <div className="mt-3 flex flex-col gap-2 rounded-lg bg-app-bg p-3">
          <select
            value={reasonCode}
            onChange={(e) => setReasonCode(e.target.value as ReturnReasonCode)}
            className="rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
          >
            {returnReasons.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
          <input
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Comentario (opcional)…"
            className="rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
          />
          <div className="flex gap-2">
            <Button size="sm" onClick={handleReviewSubmit}>
              Confirmar
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setShowReviewForm(null)}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium tracking-wide text-muted uppercase">{label}</dt>
      <dd className="mt-0.5 text-sm text-obsessed">{value}</dd>
    </div>
  );
}
