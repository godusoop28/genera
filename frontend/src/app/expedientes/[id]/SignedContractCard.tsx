"use client";

import { DocumentViewerModal } from "@/components/documents/DocumentViewerModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { listSignedContracts, uploadSignedContract, type SignatureMethod, type SignedContractUploadResponse } from "@/lib/api/contracts";
import { listVersions } from "@/lib/api/documents";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { UploadValidationError } from "@/lib/upload-limits";
import { Eye, Loader2, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

const METHOD_LABELS: Record<SignatureMethod, string> = {
  DOCUSIGN: "DocuSign",
  PHYSICAL: "Firma física (escaneado)",
};

/**
 * Control de Cambios #20: todos los contratos se firman fuera del sistema (DocuSign o en papel).
 * Aquí se sube el PDF firmado, aunque no se haya generado en el sistema (también contratos
 * históricos). Queda con estatus "Firmado" y se consulta en el visor con los permisos por rol.
 */
export function SignedContractCard({ expedienteId, onUploaded }: { expedienteId: string; onUploaded: () => Promise<void> }) {
  const { showToast } = useToast();
  const can = useCan();
  const [uploads, setUploads] = useState<SignedContractUploadResponse[] | null>(null);
  const [open, setOpen] = useState(false);
  const [method, setMethod] = useState<SignatureMethod>("DOCUSIGN");
  const [historical, setHistorical] = useState(false);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [viewing, setViewing] = useState<{ id: string; title: string } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const canUpload = can("DOCUMENT_UPLOAD") || can("CONTRACT_GENERATE") || can("CONTRACT_SIGN");

  const load = useCallback(() => {
    listSignedContracts(expedienteId)
      .then(setUploads)
      .catch(() => setUploads([]));
  }, [expedienteId]);

  useEffect(() => {
    load();
  }, [load]);

  const upload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setBusy(true);
    try {
      await uploadSignedContract(expedienteId, Array.from(files), method, historical, note);
      showToast("Contrato firmado cargado: el expediente queda con contrato firmado.");
      setOpen(false);
      setNote("");
      setHistorical(false);
      load();
      await onUploaded();
    } catch (err) {
      showToast(err instanceof UploadValidationError ? err.message : errorText(err));
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const view = async (u: SignedContractUploadResponse, index: number) => {
    try {
      const versions = [...(await listVersions(u.documentId))].sort((a, b) => b.versionNumber - a.versionNumber);
      // Cada carga es una versión nueva del mismo documento: la más reciente es la primera de la lista.
      const version = versions[index] ?? versions[0];
      if (version) setViewing({ id: version.id, title: `Contrato firmado · versión ${version.versionNumber}` });
    } catch (err) {
      showToast(errorText(err));
    }
  };

  return (
    <Card>
      <CardHeader
        title="Contrato firmado"
        description="Los contratos se firman fuera del sistema (DocuSign o impresos). Sube aquí el PDF firmado para completar el expediente, aunque el contrato no se haya generado aquí."
      />
      {uploads === null ? (
        <p className="text-sm text-muted">Cargando…</p>
      ) : uploads.length === 0 ? (
        <p className="mb-3 text-sm text-muted">Todavía no se ha subido el contrato firmado.</p>
      ) : (
        <ul className="mb-3 flex flex-col gap-2">
          {uploads.map((u, i) => (
            <li key={u.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-obsessed">
                  <Badge tone="success">Firmado</Badge>
                  {METHOD_LABELS[u.signatureMethod]}
                  {u.historical ? <Badge tone="neutral">Operación anterior al sistema</Badge> : null}
                </p>
                <p className="mt-0.5 text-xs text-muted">
                  Subido {formatDateTime(u.uploadedAt)}
                  {u.uploadedByName ? ` por ${u.uploadedByName}` : ""}
                  {u.note ? ` · ${u.note}` : ""}
                </p>
              </div>
              <Button variant="secondary" size="sm" onClick={() => view(u, i)}>
                <Eye className="h-4 w-4" aria-hidden /> Ver
              </Button>
            </li>
          ))}
        </ul>
      )}
      {canUpload ? (
        <Button size="sm" onClick={() => setOpen(true)}>
          <Upload className="h-4 w-4" aria-hidden /> Subir contrato firmado
        </Button>
      ) : null}

      <Modal open={open} onClose={() => setOpen(false)} title="Subir contrato firmado">
        <div className="flex flex-col gap-4 text-sm">
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 font-medium text-obsessed">¿Cómo se firmó?</legend>
            {(Object.keys(METHOD_LABELS) as SignatureMethod[]).map((m) => (
              <label key={m} className="flex items-center gap-2 text-obsessed">
                <input type="radio" name="signature-method" checked={method === m} onChange={() => setMethod(m)} className="accent-[var(--color-c21-dark-gold)]" />
                {m === "DOCUSIGN" ? "DocuSign (PDF descargado de DocuSign)" : "Firma física (contrato impreso, escaneado)"}
              </label>
            ))}
          </fieldset>
          <label className="flex items-start gap-2 text-obsessed">
            <input
              type="checkbox"
              checked={historical}
              onChange={(e) => setHistorical(e.target.checked)}
              className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--color-c21-dark-gold)]"
            />
            Es de una operación anterior al sistema (contrato histórico)
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="font-medium text-obsessed">Nota (opcional)</span>
            <input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={500}
              placeholder="Ej. Firmado el 14/10 en la oficina"
              className="min-h-10 rounded-lg border border-border bg-card px-3 text-obsessed"
            />
          </label>
          <p className="text-xs text-muted">Formato principal: PDF. El asesor solo podrá consultarlo en el visor; el administrador puede descargarlo.</p>
          <input ref={fileRef} type="file" accept="application/pdf,image/jpeg,image/png" multiple className="hidden" onChange={upload} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)} disabled={busy}>
              Cancelar
            </Button>
            <Button onClick={() => fileRef.current?.click()} disabled={busy}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />} Elegir PDF
            </Button>
          </div>
        </div>
      </Modal>

      <DocumentViewerModal key={viewing?.id ?? "closed"} versionId={viewing?.id ?? null} title={viewing?.title ?? ""} onClose={() => setViewing(null)} />
    </Card>
  );
}
