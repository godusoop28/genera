"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { SignaturePad } from "@/components/signature/SignaturePad";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ApiError } from "@/lib/api/client";
import { getSigningView, signContract } from "@/lib/api/public";
import type { SigningViewResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/labels";
import { CheckCircle2, FileText, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";

/**
 * Firma electrónica del contrato por liga personal. Queda registrado quién
 * firmó, la versión y la huella del documento que vio, la fecha y hora, la IP
 * y el navegador. La liga deja de funcionar una vez usada.
 */
export function SigningPage({ token }: { token: string }) {
  const [view, setView] = useState<SigningViewResponse | null>(null);
  const [invalid, setInvalid] = useState<string | null>(null);
  const [opened, setOpened] = useState(false);
  const [typedName, setTypedName] = useState("");
  const [signature, setSignature] = useState<string | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [signedAt, setSignedAt] = useState<string | null>(null);

  useEffect(() => {
    getSigningView(token)
      .then(setView)
      .catch((err) =>
        setInvalid(err instanceof ApiError ? err.message : "No se pudo cargar el contrato. Revisa tu conexión e intenta de nuevo."),
      );
  }, [token]);

  const sign = async () => {
    if (!view || !signature) return;
    setBusy(true);
    setError(null);
    try {
      const result = await signContract(token, {
        typedName,
        signatureImageBase64: signature,
        documentSha256: view.documentSha256,
        accepted,
      });
      setSignedAt(result.signedAt);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo registrar tu firma. Intenta de nuevo.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-app-bg">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex max-w-3xl items-center px-4 py-4 lg:px-8">
          <BrandLogo tone="light" size="sm" />
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 lg:px-8">
        {invalid ? (
          <Card>
            <p className="text-sm font-medium text-obsessed">{invalid}</p>
          </Card>
        ) : !view ? (
          <p className="text-sm text-muted">Cargando contrato…</p>
        ) : signedAt ? (
          <Card>
            <p className="flex items-center gap-2 text-lg font-semibold text-obsessed">
              <CheckCircle2 className="h-6 w-6 text-success-text" aria-hidden /> Firma registrada
            </p>
            <p className="mt-2 text-sm text-muted">
              Firmaste el contrato del expediente {view.folio} (versión {view.versionNumber}) el {formatDateTime(signedAt)}. Cuando firmen todas las
              partes, tu asesor te entregará tu tanto firmado. Ya puedes cerrar esta página.
            </p>
          </Card>
        ) : (
          <div className="flex flex-col gap-6">
            <div>
              <h1 className="text-2xl font-semibold text-obsessed">Firma de tu contrato de intermediación</h1>
              <p className="mt-1 text-sm text-muted">
                Expediente {view.folio} · versión {view.versionNumber} · generado {formatDateTime(view.generatedAt)}
              </p>
            </div>

            <Card>
              <CardHeader title="1. Lee el contrato completo" description={`Firmarás como ${view.signerName} (${view.signerCapacity}).`} />
              <a href={view.pdfUrl} target="_blank" rel="noopener noreferrer" onClick={() => setOpened(true)}>
                <Button variant="secondary">
                  <FileText className="h-4 w-4" aria-hidden /> Abrir el contrato (PDF)
                </Button>
              </a>
              <p className="mt-3 break-all text-xs text-muted">Huella digital del documento: {view.documentSha256}</p>
            </Card>

            <Card>
              <CardHeader title="2. Escribe tu nombre y firma" />
              <label className="mb-1 block text-sm font-medium text-obsessed">Nombre completo, tal como aparece en el contrato</label>
              <input
                value={typedName}
                onChange={(e) => setTypedName(e.target.value)}
                placeholder={view.signerName}
                className="mb-4 w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold"
              />
              <SignaturePad onChange={setSignature} />
              <label className="mt-4 flex items-start gap-2 text-sm text-obsessed">
                <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{view.consentText}</span>
              </label>
              {!opened ? <p className="mt-3 text-xs text-warning-text">Abre y lee el contrato antes de firmar.</p> : null}
              {error ? <p className="mt-3 text-sm text-danger-text">{error}</p> : null}
              <div className="mt-4 flex justify-end">
                <Button size="lg" onClick={sign} disabled={!opened || !typedName.trim() || !signature || !accepted || busy}>
                  {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
                  Firmar contrato
                </Button>
              </div>
            </Card>
            <p className="text-xs text-muted">
              Al firmar se registran la fecha y hora, tu dirección IP, tu navegador y la huella del documento, como evidencia de que firmaste
              exactamente esta versión.
            </p>
          </div>
        )}
      </main>

      <footer className="border-t border-border bg-card px-4 py-4 text-center lg:px-8">
        <BrandFooter className="mx-auto" />
      </footer>
    </div>
  );
}
