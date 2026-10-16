"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import type { ExpedienteResponse } from "@/lib/api/types";
import { deferPrivacySignature, getPrivacyStatus, uploadPhysicalPrivacySignature, type PrivacyStatusResponse } from "@/lib/api/privacy";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { ACCEPTED_FILE_TYPES, UploadValidationError } from "@/lib/upload-limits";
import { FastForward, Loader2, Printer, Upload } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Control de Cambios #2: el aviso de privacidad no bloquea el alta. Se ve si está "Pendiente de
 * firma" o "Firmado"; se puede seguir sin él, imprimirlo para firma en la oficina y cargar el
 * escaneo, o que el cliente lo firme después en su liga.
 */
export function PrivacyCard({ expediente, reload }: { expediente: ExpedienteResponse; reload: () => Promise<void> }) {
  const { showToast } = useToast();
  const can = useCan();
  const [status, setStatus] = useState<PrivacyStatusResponse | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [secondary, setSecondary] = useState(true);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(() => {
    getPrivacyStatus(expediente.id)
      .then(setStatus)
      .catch(() => undefined);
  }, [expediente.id]);

  useEffect(() => {
    load();
  }, [load]);

  const canEdit = can("EXPEDIENT_EDIT") || can("DOCUMENT_UPLOAD");
  const beforeDocuments = expediente.status === "DRAFT" || expediente.status === "WAITING_PRIVACY";
  const signed = status?.status === "SIGNED";

  const defer = async () => {
    setBusy(true);
    try {
      await deferPrivacySignature(expediente.id, "Alta presencial: el aviso se firma después");
      showToast("El expediente sigue al paso de documentos. El aviso queda pendiente de firma.");
      await reload();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const upload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setBusy(true);
    try {
      setStatus(await uploadPhysicalPrivacySignature(expediente.id, Array.from(files), secondary));
      showToast("Aviso firmado cargado: quedó registrado y guardado en los documentos del expediente.");
      setUploadOpen(false);
      await reload();
    } catch (err) {
      showToast(err instanceof UploadValidationError ? err.message : errorText(err));
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  return (
    <Card>
      <CardHeader
        title="Aviso de privacidad"
        description="No detiene el alta: el titular puede firmarlo en su liga o en papel en la oficina. Se necesita firmado antes del contrato."
      />
      <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
        {status === null ? (
          <span className="text-muted">Cargando…</span>
        ) : signed ? (
          <>
            <Badge tone="success">Firmado</Badge>
            <span className="text-muted">
              {status.signatureMethod === "PHYSICAL" ? "En papel, en la oficina" : "En su liga, con firma digital"} · {formatDateTime(status.signedAt)}
              {status.secondaryPurposesAccepted === false
                ? " · finalidades secundarias y promocionales: no autorizadas"
                : status.secondaryPurposesAccepted
                  ? " · finalidades secundarias y promocionales: autorizadas"
                  : ""}
            </span>
          </>
        ) : (
          <Badge tone="warning">Pendiente de firma</Badge>
        )}
      </div>
      {status?.signatureMethod === "PHYSICAL" ? (
        <p className="mb-3 text-xs text-muted">El escaneo firmado está en la pestaña Documentos (&quot;Aviso de privacidad firmado&quot;).</p>
      ) : null}
      {canEdit ? (
        <div className="flex flex-wrap gap-2">
          {beforeDocuments ? (
            <Button size="sm" onClick={defer} disabled={busy}>
              <FastForward className="h-4 w-4" aria-hidden /> Continuar sin firmar (se firma después)
            </Button>
          ) : null}
          <Link
            href={`/expedientes/${expediente.id}/aviso-privacidad`}
            target="_blank"
            className="inline-flex min-h-9 items-center gap-2 rounded-lg border border-border bg-card px-3 text-sm font-medium text-obsessed hover:border-gold/60"
          >
            <Printer className="h-4 w-4" aria-hidden /> Imprimir para firma en oficina
          </Link>
          <Button variant="secondary" size="sm" onClick={() => setUploadOpen(true)} disabled={busy}>
            <Upload className="h-4 w-4" aria-hidden /> {signed ? "Cargar otro aviso firmado" : "Cargar aviso firmado"}
          </Button>
        </div>
      ) : null}
      {!signed ? (
        <p className="mt-3 text-xs text-muted">
          Para firma digital posterior, comparte la liga del cliente (abajo): al abrirla se le pide firmar el aviso. Si ya tiene una liga activa, la
          misma le sirve.
        </p>
      ) : null}

      <Modal open={uploadOpen} onClose={() => setUploadOpen(false)} title="Cargar aviso firmado en papel">
        <div className="flex flex-col gap-4 text-sm">
          <p className="text-muted">
            Sube el escaneo o la foto del aviso impreso con la firma del titular. Se guarda en los documentos del expediente y queda registrado con
            tu usuario.
          </p>
          <label className="flex items-start gap-2 text-obsessed">
            <input
              type="checkbox"
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
              checked={secondary}
              onChange={(e) => setSecondary(e.target.checked)}
            />
            Aceptó las finalidades secundarias (no marcó la casilla para negarse).
          </label>
          <input ref={fileRef} type="file" accept={ACCEPTED_FILE_TYPES} multiple className="hidden" onChange={upload} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setUploadOpen(false)} disabled={busy}>
              Cancelar
            </Button>
            <Button onClick={() => fileRef.current?.click()} disabled={busy}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />} Elegir archivo
            </Button>
          </div>
        </div>
      </Modal>
    </Card>
  );
}
