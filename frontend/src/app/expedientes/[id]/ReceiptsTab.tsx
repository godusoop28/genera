"use client";

import { SignaturePad } from "@/components/signature/SignaturePad";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { errorText } from "@/lib/errors";
import {
  generateReceipt,
  getReceiptDownload,
  getReceiptPreview,
  listReceipts,
  requestReceiptSignature,
  sendReceipt,
  signReceipt,
  uploadSignedReceipt,
  type Receipt,
  type ReceiptPreview,
  type ReceiptStatus,
  type ReceivedItem,
} from "@/lib/api/receipts";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, Download, FilePlus2, FileSignature, Mail, PenLine, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ExpedienteContext } from "./page";

const statusLabels: Record<ReceiptStatus, string> = { DRAFT: "Borrador", PENDING_SIGNATURE: "Pendiente de firma", SIGNED: "Firmado", SENT: "Enviado" };
const statusTones: Record<ReceiptStatus, "neutral" | "warning" | "success" | "info"> = { DRAFT: "neutral", PENDING_SIGNATURE: "warning", SIGNED: "success", SENT: "info" };

const dateTime = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "America/Mexico_City" });
const when = (iso: string | null) => (iso ? dateTime.format(new Date(iso)) : "—");

/**
 * Control de Cambios #31: acuse de recepción de documentos y responsabilidad
 * de resguardo, firmado por el representante legal. Se arma con los
 * documentos realmente recibidos; cada emisión se conserva.
 */
export function ReceiptsTab({ expediente }: ExpedienteContext) {
  const can = useCan();
  const { showToast } = useToast();
  const [preview, setPreview] = useState<ReceiptPreview | null>(null);
  const [receipts, setReceipts] = useState<Receipt[] | null>(null);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [signing, setSigning] = useState<Receipt | null>(null);
  const [sending, setSending] = useState<Receipt | null>(null);

  const load = useCallback(() => {
    Promise.all([getReceiptPreview(expediente.id), listReceipts(expediente.id)])
      .then(([p, r]) => {
        setPreview(p);
        setReceipts(r);
        setError(false);
      })
      .catch(() => setError(true));
  }, [expediente.id]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      await fn();
      showToast(message);
      load();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const open = async (r: Receipt, which: "draft" | "signed") => {
    try {
      const { url } = await getReceiptDownload(r.id, which);
      window.open(url, "_blank", "noopener");
    } catch (err) {
      showToast(errorText(err));
    }
  };

  if (error) return <StateMessage kind="error" title="No se pudo cargar" description="Intenta de nuevo en un momento." />;
  if (!preview || !receipts) return <StateMessage kind="loading" title="Cargando acuses…" />;

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader
          title="Acuse de recepción de documentos"
          description={`Constancia para el cliente de los documentos que entregó, el aviso de privacidad aplicable y la responsabilidad de su resguardo. Lo firma el representante legal de ${preview.responsibleEntity}.`}
          action={
            can("RECEIPT_GENERATE") ? (
              <Button size="sm" disabled={busy || preview.items.length === 0} onClick={() => void act(() => generateReceipt(expediente.id), "Acuse generado (borrador)")}>
                <FilePlus2 className="h-4 w-4" aria-hidden /> {preview.hasReceipts ? "Generar nuevo acuse" : "Generar acuse"}
              </Button>
            ) : null
          }
        />
        <ul className="space-y-1.5 text-sm">
          <li>
            <strong className="font-semibold tabular-nums">{preview.items.length}</strong> archivo{preview.items.length === 1 ? "" : "s"} recibido{preview.items.length === 1 ? "" : "s"} hasta hoy
            {preview.hasReceipts ? (
              preview.newSinceLast > 0 ? (
                <span className="text-warning-text"> · {preview.newSinceLast} nuevo{preview.newSinceLast === 1 ? "" : "s"} desde el último acuse: conviene emitir uno nuevo.</span>
              ) : (
                <span className="text-muted"> · el último acuse ya los incluye.</span>
              )
            ) : null}
          </li>
          {!preview.textsApproved ? (
            <li className="flex items-start gap-2 rounded-xl bg-warning-bg px-3 py-2 text-warning-text">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              Los textos del acuse aún no están aprobados por el área legal: se puede generar el borrador para revisión, pero no firmarse ni enviarse al cliente.
            </li>
          ) : null}
          {!preview.representativeName ? <li className="text-xs text-muted">Falta capturar el nombre del representante legal en Configuración → Acuse de recepción.</li> : null}
        </ul>
        {preview.items.length > 0 ? (
          <details className="mt-3">
            <summary className="cursor-pointer text-sm font-medium text-dark-gold">Ver documentos que incluiría</summary>
            <ItemList items={preview.items} />
          </details>
        ) : null}
      </Card>

      {receipts.length === 0 ? (
        <p className="text-sm text-muted">Todavía no se ha emitido ningún acuse.</p>
      ) : (
        receipts.map((r) => (
          <Card key={r.id}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold text-obsessed">Acuse {r.number}</span>
              <Badge tone={statusTones[r.status]}>{statusLabels[r.status]}</Badge>
              {!r.textsApproved ? <Badge tone="danger">Textos sin aprobación legal</Badge> : null}
              <span className="ml-auto text-xs text-muted">Emitido {when(r.generatedAt)} por {r.generatedByName}</span>
            </div>
            <p className="mt-1 text-xs text-muted">
              {r.items.length} archivo{r.items.length === 1 ? "" : "s"}
              {r.privacyNoticeVersion ? ` · Aviso de privacidad v${r.privacyNoticeVersion}` : ""}
              {r.requestedSignatureAt ? ` · Enviado a firma ${when(r.requestedSignatureAt)} por ${r.requestedByName}` : ""}
              {r.signedAt ? ` · Firmado ${when(r.signedAt)} por ${r.signedByName} (${r.signatureMethod === "ELECTRONIC_SIMPLE" ? "firma en el sistema" : "archivo firmado cargado"})` : ""}
              {r.sentAt ? ` · Enviado a ${r.sentTo} el ${when(r.sentAt)} por ${r.sentByName}` : ""}
            </p>
            {r.sendDetail ? <p className="mt-1 text-xs text-muted">{r.sendDetail}</p> : null}
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="sm" variant="ghost" onClick={() => void open(r, "draft")}>
                <Download className="h-4 w-4" aria-hidden /> Borrador
              </Button>
              {r.status === "SIGNED" || r.status === "SENT" ? (
                <Button size="sm" variant="secondary" onClick={() => void open(r, "signed")}>
                  <Download className="h-4 w-4" aria-hidden /> Acuse firmado
                </Button>
              ) : null}
              {r.status === "DRAFT" && can("RECEIPT_GENERATE") ? (
                <Button size="sm" variant="secondary" disabled={busy || !r.textsApproved} onClick={() => void act(() => requestReceiptSignature(r.id), "Enviado a firma del representante legal")}>
                  <FileSignature className="h-4 w-4" aria-hidden /> Enviar a firma
                </Button>
              ) : null}
              {(r.status === "DRAFT" || r.status === "PENDING_SIGNATURE") && can("RECEIPT_SIGN") ? (
                <>
                  <Button size="sm" disabled={busy || !r.textsApproved} onClick={() => setSigning(r)}>
                    <PenLine className="h-4 w-4" aria-hidden /> Firmar como representante
                  </Button>
                  <UploadSignedButton receipt={r} disabled={busy || !r.textsApproved} onDone={() => { showToast("Acuse firmado cargado"); load(); }} />
                </>
              ) : null}
              {(r.status === "SIGNED" || r.status === "SENT") && can("RECEIPT_GENERATE") ? (
                <Button size="sm" disabled={busy} onClick={() => setSending(r)}>
                  <Mail className="h-4 w-4" aria-hidden /> {r.status === "SENT" ? "Reenviar al cliente" : "Enviar al cliente"}
                </Button>
              ) : null}
            </div>
            <details className="mt-3">
              <summary className="cursor-pointer text-sm font-medium text-dark-gold">Documentos que cubre este acuse</summary>
              <ItemList items={r.items} />
            </details>
          </Card>
        ))
      )}

      {signing ? <SignModal receipt={signing} onClose={() => setSigning(null)} onSigned={() => { setSigning(null); showToast("Acuse firmado"); load(); }} /> : null}
      {sending ? (
        <SendModal
          receipt={sending}
          defaultEmail={sending.clientEmail ?? preview.clientEmail ?? ""}
          onClose={() => setSending(null)}
          onSent={() => {
            setSending(null);
            showToast("Acuse enviado al cliente");
            load();
          }}
        />
      ) : null}
    </div>
  );
}

function ItemList({ items }: { items: ReceivedItem[] }) {
  return (
    <ul className="mt-2 divide-y divide-border rounded-xl border border-border text-sm">
      {items.map((i, n) => (
        <li key={`${i.name}-${i.participant}-${i.versionNumber}-${n}`} className="flex flex-wrap items-baseline gap-x-2 px-3 py-2">
          <span className={i.current ? "text-obsessed" : "text-muted"}>
            {i.name}
            {i.participant ? <span className="text-muted"> · {i.participant}</span> : null}
          </span>
          <span className="text-xs text-muted">V{i.versionNumber}{i.current ? "" : " (anterior)"}</span>
          <span className="ml-auto text-xs text-muted">{when(i.receivedAt)}</span>
        </li>
      ))}
    </ul>
  );
}

function UploadSignedButton({ receipt, disabled, onDone }: { receipt: Receipt; disabled: boolean; onDone: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const { showToast } = useToast();
  const [uploading, setUploading] = useState(false);
  return (
    <>
      <input
        ref={input}
        type="file"
        accept="application/pdf"
        className="hidden"
        onChange={async (e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (!file) return;
          setUploading(true);
          try {
            await uploadSignedReceipt(receipt.id, file);
            onDone();
          } catch (err) {
            showToast(errorText(err));
          } finally {
            setUploading(false);
          }
        }}
      />
      <Button size="sm" variant="secondary" disabled={disabled || uploading} onClick={() => input.current?.click()}>
        <Upload className="h-4 w-4" aria-hidden /> {uploading ? "Subiendo…" : "Cargar acuse firmado"}
      </Button>
    </>
  );
}

function SignModal({ receipt, onClose, onSigned }: { receipt: Receipt; onClose: () => void; onSigned: () => void }) {
  const [signature, setSignature] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [title, setTitle] = useState("Representante legal");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal
      open
      onClose={onClose}
      title={`Firmar acuse ${receipt.number}`}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={saving || !signature || !password}
            onClick={async () => {
              if (!signature) return;
              setSaving(true);
              setError(null);
              try {
                await signReceipt(receipt.id, password, signature, title.trim() || undefined);
                onSigned();
              } catch (err) {
                setError(errorText(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Firmando…" : "Firmar"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-muted">
          Al firmar, como representante legal de {receipt.responsibleEntity}, confirmas la recepción de los {receipt.items.length} archivos relacionados y la responsabilidad de su resguardo. Revisa antes el borrador.
        </p>
        <Input label="Cargo" value={title} onChange={(e) => setTitle(e.target.value)} />
        <SignaturePad onChange={setSignature} hint="Firma con el dedo o el mouse" />
        <Input label="Confirma con tu contraseña" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}

function SendModal({ receipt, defaultEmail, onClose, onSent }: { receipt: Receipt; defaultEmail: string; onClose: () => void; onSent: () => void }) {
  const [email, setEmail] = useState(defaultEmail);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Modal
      open
      onClose={onClose}
      title={`Enviar acuse ${receipt.number} al cliente`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={saving || !email.trim()}
            onClick={async () => {
              setSaving(true);
              setError(null);
              try {
                await sendReceipt(receipt.id, email.trim());
                onSent();
              } catch (err) {
                setError(errorText(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Enviando…" : "Enviar"}
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <p className="text-sm text-muted">Se envía por correo el acuse firmado y el aviso de privacidad. El resultado de la entrega queda en el historial de notificaciones del expediente.</p>
        <Input label="Correo del cliente" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}
