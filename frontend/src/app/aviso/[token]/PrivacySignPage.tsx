"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { PrivacyNoticeCard } from "@/components/privacy/PrivacyNoticeCard";
import { SignatureMock } from "@/components/privacy/SignatureMock";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import {
  PRIVACY_CONSENT_TEXT,
  PRIVACY_SECONDARY_CONSENT_TEXT,
} from "@/data/privacy-reference";
import { ApiError } from "@/lib/api/client";
import {
  getPrivacySignLink,
  signPrivacyViaLink,
  type PrivacySignLinkView,
} from "@/lib/api/public";
import { formatDateTime } from "@/lib/labels";
import { CheckCircle2, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";

/**
 * Control de Cambios #44: liga exclusiva para que el titular lea y firme el aviso de privacidad de
 * su expediente. No muestra datos del expediente ni permite cargar documentos. Al firmar, el aviso
 * firmado se guarda solo en su expediente y la liga deja de funcionar.
 */
export function PrivacySignPage({ token }: { token: string }) {
  const [view, setView] = useState<PrivacySignLinkView | null>(null);
  const [invalid, setInvalid] = useState<string | null>(null);
  const [mainConsent, setMainConsent] = useState(false);
  const [secondaryConsent, setSecondaryConsent] = useState(false);
  const [signature, setSignature] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [signedAt, setSignedAt] = useState<string | null>(null);

  useEffect(() => {
    getPrivacySignLink(token)
      .then(setView)
      .catch((err) =>
        setInvalid(
          err instanceof ApiError && [401, 403, 404, 410].includes(err.status)
            ? "Esta liga no es válida, ya venció, ya se usó para firmar o fue reemplazada por una nueva. Pide a tu asesor una liga nueva si aún no firmas."
            : "No se pudo cargar el aviso. Revisa tu conexión e intenta de nuevo.",
        ),
      );
  }, [token]);

  const sign = async () => {
    if (!signature || !mainConsent) return;
    setBusy(true);
    setError(null);
    try {
      const result = await signPrivacyViaLink(
        token,
        secondaryConsent,
        signature,
      );
      setSignedAt(result.signedAt);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "No se pudo registrar tu firma. Intenta de nuevo.",
      );
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
          <p className="text-sm text-muted">Cargando aviso de privacidad…</p>
        ) : signedAt ? (
          <Card>
            <p className="flex items-center gap-2 text-lg font-semibold text-obsessed">
              <CheckCircle2 className="h-6 w-6 text-success-text" aria-hidden />{" "}
              Aviso de privacidad firmado
            </p>
            <p className="mt-2 text-sm text-muted">
              Firmaste el aviso de privacidad (versión {view.noticeVersion}) el{" "}
              {formatDateTime(signedAt)}. Quedó guardado en tu expediente y tu
              asesor ya puede verlo. Ya puedes cerrar esta página.
            </p>
          </Card>
        ) : (
          <div className="flex flex-col gap-6">
            <div>
              <h1 className="text-xl font-semibold text-obsessed">
                Firma de tu aviso de privacidad
              </h1>
              <p className="mt-1 text-sm text-muted">
                Tu asesor de CENTURY 21 Genera te compartió esta liga para que
                leas y firmes el aviso de privacidad. Solo sirve para este
                trámite. Vence el {formatDateTime(view.expiresAt)}.
              </p>
            </div>
            <PrivacyNoticeCard />
            <Card>
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  checked={mainConsent}
                  onChange={(e) => {
                    // CC#12: aceptar el aviso marca también las finalidades secundarias; quitarlo las desmarca.
                    setMainConsent(e.target.checked);
                    setSecondaryConsent(e.target.checked);
                  }}
                  className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
                />
                <span className="text-sm text-obsessed">
                  {PRIVACY_CONSENT_TEXT}
                </span>
              </label>
              <div className="mt-4 border-t border-border pt-4">
                <label className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    checked={secondaryConsent}
                    disabled={!mainConsent}
                    onChange={(e) => setSecondaryConsent(e.target.checked)}
                    className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
                  />
                  <span className="text-sm text-obsessed">
                    {PRIVACY_SECONDARY_CONSENT_TEXT}
                  </span>
                </label>
                <p className="mt-1.5 pl-7 text-xs text-muted">
                  Puedes desmarcarla si no autorizas esas finalidades. No afecta
                  tu trámite.
                </p>
              </div>
              <div className="mt-5 border-t border-border pt-4">
                <p className="mb-2 text-sm font-medium text-obsessed">
                  Firma del titular
                </p>
                <SignatureMock
                  onSign={setSignature}
                  signed={Boolean(signature)}
                />
              </div>
            </Card>
            {error ? <p className="text-sm text-danger-text">{error}</p> : null}
            <div className="flex justify-end">
              <Button
                size="lg"
                disabled={!mainConsent || !signature || busy}
                onClick={sign}
              >
                {busy ? (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                ) : null}
                Firmar aviso de privacidad
              </Button>
            </div>
          </div>
        )}
      </main>
      <BrandFooter />
    </div>
  );
}
