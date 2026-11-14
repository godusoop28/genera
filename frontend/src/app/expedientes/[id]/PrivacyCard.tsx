"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import type { ExpedienteResponse } from "@/lib/api/types";
import {
  deferPrivacySignature,
  getPrivacyStatus,
  issuePrivacySignLink,
  revokePrivacySignLink,
  uploadPhysicalPrivacySignature,
  type PrivacyStatusResponse,
} from "@/lib/api/privacy";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { frontendLink, whatsappShareUrl } from "@/lib/share-links";
import {
  ACCEPTED_FILE_TYPES,
  UploadValidationError,
} from "@/lib/upload-limits";
import {
  Copy,
  FastForward,
  Link2,
  Loader2,
  MessageCircle,
  Printer,
  Upload,
  XCircle,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Control de Cambios #2: el aviso de privacidad no bloquea el alta. Se ve si está "Pendiente de
 * firma" o "Firmado"; se puede seguir sin él, imprimirlo para firma en la oficina y cargar el
 * escaneo, o que el cliente lo firme después.
 *
 * Control de Cambios #44: «Generar liga para firmar aviso de privacidad» crea una liga que solo
 * sirve para leer y firmar el aviso de este expediente. Generarla no es firmar.
 * Control de Cambios #45: el aviso firmado (escaneo o copia firmada en liga) aparece en Documentos
 * como «Aviso de Privacidad»; un escaneo queda «Firma por verificar» hasta aceptarlo ahí.
 */
export function PrivacyCard({
  expediente,
  reload,
  phone,
}: {
  expediente: ExpedienteResponse;
  reload: () => Promise<void>;
  phone?: string | null;
}) {
  const { showToast } = useToast();
  const can = useCan();
  const [status, setStatus] = useState<PrivacyStatusResponse | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [secondary, setSecondary] = useState(true);
  const [busy, setBusy] = useState(false);
  const [newLink, setNewLink] = useState<{
    url: string;
    expiresAt: string;
  } | null>(null);
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
  const beforeDocuments =
    expediente.status === "DRAFT" || expediente.status === "WAITING_PRIVACY";
  const signed = status?.status === "SIGNED";
  const verifying = status?.status === "PENDING_VERIFICATION";
  const signedDigitally =
    status?.status === "SIGNED" && status.signatureMethod === "DIGITAL";
  const link = status?.signLink ?? null;

  const defer = async () => {
    setBusy(true);
    try {
      await deferPrivacySignature(
        expediente.id,
        "Alta presencial: el aviso se firma después",
      );
      showToast(
        "El expediente sigue al paso de documentos. El aviso queda pendiente de firma.",
      );
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
      setStatus(
        await uploadPhysicalPrivacySignature(
          expediente.id,
          Array.from(files),
          secondary,
        ),
      );
      showToast(
        "Aviso cargado: está en Documentos como «Aviso de Privacidad». Revisa que esté firmado y acéptalo ahí.",
      );
      setUploadOpen(false);
      await reload();
    } catch (err) {
      showToast(
        err instanceof UploadValidationError ? err.message : errorText(err),
      );
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const issueLink = async () => {
    setBusy(true);
    try {
      const issued = await issuePrivacySignLink(expediente.id);
      setNewLink({
        url: frontendLink(issued.url, "aviso"),
        expiresAt: issued.expiresAt,
      });
      load();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const revokeLink = async () => {
    setBusy(true);
    try {
      setStatus(await revokePrivacySignLink(expediente.id));
      setNewLink(null);
      showToast("La liga para firmar el aviso quedó desactivada.");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const shareMessage = newLink
    ? [
        "Hola, te compartimos la liga de CENTURY 21 Genera para leer y firmar tu Aviso de Privacidad.",
        "Solo sirve para firmar el aviso; no necesitas crear una cuenta. La liga es personal: no la compartas.",
        "",
        newLink.url,
      ].join("\n")
    : "";

  return (
    <Card>
      <CardHeader
        title="Aviso de privacidad"
        description="No detiene el alta: el titular puede firmarlo en una liga o en papel en la oficina. Se necesita firmado antes del contrato."
      />
      <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
        {status === null ? (
          <span className="text-muted">Cargando…</span>
        ) : signed || verifying ? (
          <>
            {signed ? (
              <Badge tone="success">Firmado</Badge>
            ) : (
              <Badge tone="warning">Firma por verificar</Badge>
            )}
            <span className="text-muted">
              {status.signatureMethod === "PHYSICAL"
                ? "En papel, en la oficina"
                : status.signatureChannel === "SIGN_LINK"
                  ? "En la liga para firmar el aviso, con firma digital"
                  : "En su liga, con firma digital"}{" "}
              · {formatDateTime(status.signedAt)}
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
      {verifying ? (
        <p className="mb-3 rounded-lg bg-warning-bg px-3 py-2 text-xs text-warning-text">
          Se cargó un archivo, pero cargarlo no confirma la firma. Ábrelo en la
          pestaña Documentos («Aviso de Privacidad»), comprueba que esté firmado
          por el titular y acéptalo para que quede como «Firmado».
        </p>
      ) : status?.signedDocumentId ? (
        <p className="mb-3 text-xs text-muted">
          El aviso firmado está en la pestaña Documentos como «Aviso de
          Privacidad».
        </p>
      ) : null}
      {canEdit ? (
        <div className="flex flex-wrap gap-2">
          {beforeDocuments ? (
            <Button size="sm" onClick={defer} disabled={busy}>
              <FastForward className="h-4 w-4" aria-hidden /> Continuar sin
              firmar (se firma después)
            </Button>
          ) : null}
          {!signedDigitally ? (
            <Button
              size="sm"
              variant={beforeDocuments ? "secondary" : "primary"}
              onClick={issueLink}
              disabled={busy}
            >
              <Link2 className="h-4 w-4" aria-hidden />{" "}
              {link?.usable
                ? "Generar una liga nueva"
                : "Generar liga para firmar aviso de privacidad"}
            </Button>
          ) : null}
          <Link
            href={`/expedientes/${expediente.id}/aviso-privacidad`}
            target="_blank"
            className="inline-flex min-h-9 items-center gap-2 rounded-lg border border-border bg-card px-3 text-sm font-medium text-obsessed hover:border-gold/60"
          >
            <Printer className="h-4 w-4" aria-hidden /> Imprimir para firma en
            oficina
          </Link>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setUploadOpen(true)}
            disabled={busy}
          >
            <Upload className="h-4 w-4" aria-hidden />{" "}
            {signed || verifying
              ? "Cargar otro aviso firmado"
              : "Cargar aviso firmado"}
          </Button>
        </div>
      ) : null}

      {newLink ? (
        <div className="mt-3 rounded-lg border border-border p-3">
          <p className="mb-1 text-sm font-medium text-obsessed">
            Liga para firmar el aviso de privacidad
          </p>
          <p className="mb-2 text-xs text-muted">
            Solo sirve para leer y firmar el aviso de este expediente; no
            muestra datos ni documentos. Vence el{" "}
            {formatDateTime(newLink.expiresAt)} y deja de funcionar al firmarse.
            El aviso sigue «Pendiente de firma» hasta que el cliente lo firme.
          </p>
          <p className="whitespace-pre-wrap break-all rounded-md bg-app-bg px-3 py-2 text-sm text-obsessed">
            {newLink.url}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                navigator.clipboard.writeText(shareMessage);
                showToast("Mensaje copiado con la liga.");
              }}
            >
              <Copy className="h-4 w-4" aria-hidden /> Copiar mensaje
            </Button>
            <a
              href={whatsappShareUrl(shareMessage, phone)}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex min-h-9 items-center gap-2 rounded-xl border border-border bg-card px-3 text-sm font-medium text-obsessed hover:border-gold/60"
            >
              <MessageCircle className="h-4 w-4" aria-hidden /> Abrir en
              WhatsApp
            </a>
          </div>
        </div>
      ) : link && !signedDigitally ? (
        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted">
          {link.usedAt ? (
            <span>
              Liga para firmar el aviso usada el {formatDateTime(link.usedAt)}.
            </span>
          ) : link.usable ? (
            <>
              <span>
                Hay una liga para firmar el aviso activa (generada el{" "}
                {formatDateTime(link.createdAt)}
                {link.createdByName ? ` por ${link.createdByName}` : ""}, vence
                el {formatDateTime(link.expiresAt)}
                {link.lastOpenedAt
                  ? `; el cliente la abrió el ${formatDateTime(link.lastOpenedAt)}`
                  : "; aún no se abre"}
                ). Por seguridad no se vuelve a mostrar: genera una nueva para
                reenviarla.
              </span>
              {canEdit ? (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={revokeLink}
                  disabled={busy}
                >
                  <XCircle className="h-4 w-4" aria-hidden /> Desactivar liga
                </Button>
              ) : null}
            </>
          ) : (
            <span>
              La última liga para firmar el aviso{" "}
              {link.revokedAt ? "se desactivó" : "venció"}. Genera una nueva si
              el cliente aún no lo firma.
            </span>
          )}
        </div>
      ) : null}

      <Modal
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        title="Cargar aviso firmado en papel"
      >
        <div className="flex flex-col gap-4 text-sm">
          <p className="text-muted">
            Sube el escaneo o la foto del aviso impreso con la firma del
            titular. Se guarda en los documentos del expediente como «Aviso de
            Privacidad» y queda registrado con tu usuario. Quedará «Firma por
            verificar» hasta que se acepte en Documentos.
          </p>
          <label className="flex items-start gap-2 text-obsessed">
            <input
              type="checkbox"
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
              checked={secondary}
              onChange={(e) => setSecondary(e.target.checked)}
            />
            Aceptó las finalidades secundarias (no marcó la casilla para
            negarse).
          </label>
          <input
            ref={fileRef}
            type="file"
            accept={ACCEPTED_FILE_TYPES}
            multiple
            className="hidden"
            onChange={upload}
          />
          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              onClick={() => setUploadOpen(false)}
              disabled={busy}
            >
              Cancelar
            </Button>
            <Button onClick={() => fileRef.current?.click()} disabled={busy}>
              {busy ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
              ) : (
                <Upload className="h-4 w-4" aria-hidden />
              )}{" "}
              Elegir archivo
            </Button>
          </div>
        </div>
      </Modal>
    </Card>
  );
}
