"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { PrivacyNoticeCard } from "@/components/privacy/PrivacyNoticeCard";
import { SignatureMock } from "@/components/privacy/SignatureMock";
import { Stepper } from "@/components/documents/Stepper";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ORG_OFFICE_ADDRESS, ORG_OFFICE_MAPS_URL } from "@/data/organization";
import { PRIVACY_CONSENT_TEXT, PRIVACY_SECONDARY_OPT_OUT_TEXT } from "@/data/privacy-reference";
import { ApiError } from "@/lib/api/client";
import {
  getClientData,
  getPublicExpediente,
  listPublicDocuments,
  recordPrivacyConsent,
  submitDocuments,
  updateClientData,
  uploadPublicDocumentVersion,
} from "@/lib/api/public";
import type { BackendCivilStatus, DocumentResponse, ManualClientDataResponse, PublicExpedienteResponse } from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { HelpCircle, Loader2, MapPin, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

const steps = ["Aviso de privacidad", "Datos de contacto", "Documentos", "Confirmación"];

function initialStepFor(status: PublicExpedienteResponse["status"]): number {
  if (status === "DRAFT" || status === "WAITING_PRIVACY") return 1;
  if (status === "WAITING_DOCUMENTS") return 2;
  return 4;
}

export function PublicPortal({ token }: { token: string }) {
  const [expediente, setExpediente] = useState<PublicExpedienteResponse | null>(null);
  const [step, setStep] = useState(1);
  const [maxReached, setMaxReached] = useState(1);
  const [invalid, setInvalid] = useState(false);

  useEffect(() => {
    getPublicExpediente(token)
      .then((data) => {
        setExpediente(data);
        const s = initialStepFor(data.status);
        setStep(s);
        setMaxReached(s);
      })
      .catch(() => setInvalid(true));
  }, [token]);

  const goTo = (next: number) => {
    setStep(next);
    setMaxReached((m) => Math.max(m, next));
  };

  if (invalid) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-app-bg px-4 text-center">
        <p className="text-sm text-muted">Esta liga no es válida o fue revocada.</p>
      </div>
    );
  }

  if (!expediente) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-app-bg px-4 text-center">
        <p className="text-sm text-muted">Cargando…</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-app-bg">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-4 lg:px-8">
          <BrandLogo tone="light" size="sm" />
          <span className="inline-flex items-center gap-1.5 text-sm font-medium text-obsessed/70">
            <HelpCircle className="h-4 w-4" aria-hidden />
            ¿Necesitas ayuda?
          </span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 lg:px-8 lg:py-10">
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-obsessed">{expediente.folio}</h1>
          <Badge tone="neutral">Proceso de recepción documental</Badge>
        </div>
        <p className="max-w-2xl text-sm text-muted">
          No necesitas crear una cuenta. Esta liga es tu acceso directo a este expediente.
        </p>

        <div className="mt-8 rounded-2xl border border-border bg-card p-5">
          <Stepper steps={steps} currentStep={step} maxReachedStep={maxReached} onStepClick={goTo} />
        </div>

        <div className="mt-8">
          {step === 1 ? <PrivacyStep token={token} onContinue={() => goTo(2)} /> : null}
          {step === 2 ? <ClientDataStep token={token} onContinue={() => goTo(3)} /> : null}
          {step === 3 ? <DocumentsStep token={token} onContinue={() => goTo(4)} /> : null}
          {step === 4 ? <ConfirmationStep token={token} /> : null}
        </div>
      </main>

      <footer className="border-t border-border bg-card px-4 py-4 text-center lg:px-8">
        <BrandFooter className="mx-auto" />
      </footer>
    </div>
  );
}

function PrivacyStep({ token, onContinue }: { token: string; onContinue: () => void }) {
  const [mainConsent, setMainConsent] = useState(false);
  const [secondaryOptOut, setSecondaryOptOut] = useState(false);
  const [signatureDataUrl, setSignatureDataUrl] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canContinue = mainConsent && Boolean(signatureDataUrl);

  const handleContinue = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await recordPrivacyConsent(token, mainConsent, !secondaryOptOut, signatureDataUrl);
      onContinue();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo registrar tu consentimiento. Intenta de nuevo.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PrivacyNoticeCard />

      <Card>
        <label className="flex items-start gap-3">
          <input
            type="checkbox"
            checked={mainConsent}
            onChange={(e) => setMainConsent(e.target.checked)}
            className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
          />
          <span className="text-sm text-obsessed">{PRIVACY_CONSENT_TEXT}</span>
        </label>

        <div className="mt-4 border-t border-border pt-4">
          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              checked={secondaryOptOut}
              onChange={(e) => setSecondaryOptOut(e.target.checked)}
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
            />
            <span className="text-sm text-obsessed">{PRIVACY_SECONDARY_OPT_OUT_TEXT}</span>
          </label>
          <p className="mt-1.5 pl-7 text-xs text-muted">
            Esta preferencia es independiente del consentimiento principal: no bloquea el proceso.
          </p>
        </div>

        <div className="mt-5 border-t border-border pt-4">
          <p className="mb-2 text-sm font-medium text-obsessed">Firma del titular</p>
          <SignatureMock onSign={setSignatureDataUrl} signed={Boolean(signatureDataUrl)} />
        </div>
      </Card>

      {error ? <p className="text-sm text-danger-text">{error}</p> : null}

      <div className="flex justify-end">
        <Button size="lg" disabled={!canContinue || submitting} onClick={handleContinue}>
          {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
          Continuar
        </Button>
      </div>
    </div>
  );
}

const civilStatusOptions: { value: BackendCivilStatus; label: string }[] = [
  { value: "SOLTERO", label: "Soltero(a)" },
  { value: "CASADO", label: "Casado(a)" },
  { value: "UNION_LIBRE", label: "Unión libre" },
  { value: "DIVORCIADO", label: "Divorciado(a)" },
  { value: "VIUDO", label: "Viudo(a)" },
];

function ClientDataStep({ token, onContinue }: { token: string; onContinue: () => void }) {
  const [data, setData] = useState<ManualClientDataResponse | null>(null);
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [civilStatus, setCivilStatus] = useState<BackendCivilStatus | "">("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getClientData(token)
      .then((d) => {
        setData(d);
        setEmail(d.email ?? "");
        setPhone(d.phone ?? "");
        setCivilStatus(d.civilStatus ?? "");
      })
      .catch(() => undefined);
  }, [token]);

  const handleContinue = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await updateClientData(token, { email, phone, civilStatus: civilStatus || null });
      onContinue();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo guardar tu información. Intenta de nuevo.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Datos de contacto" description="Los usamos para avisarte sobre el avance de tu expediente." />
      {data === null ? (
        <p className="text-sm text-muted">Cargando…</p>
      ) : (
        <div className="flex flex-col gap-4">
          <div>
            <label className="mb-1 block text-xs font-medium tracking-wide text-muted uppercase">Correo electrónico</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="tu@correo.com"
              className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium tracking-wide text-muted uppercase">Teléfono</label>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="55 0000 0000"
              className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium tracking-wide text-muted uppercase">Estado civil</label>
            <select
              value={civilStatus}
              onChange={(e) => setCivilStatus(e.target.value as BackendCivilStatus)}
              className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
            >
              <option value="">Selecciona una opción</option>
              {civilStatusOptions.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            {civilStatus === "CASADO" ? (
              <p className="mt-1 text-xs text-muted">Si estás casado(a), más adelante se te pedirá tu acta de matrimonio.</p>
            ) : null}
          </div>
          {error ? <p className="text-sm text-danger-text">{error}</p> : null}
          <div className="flex justify-end">
            <Button size="lg" disabled={!email.trim() || !phone.trim() || submitting} onClick={handleContinue}>
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
              Continuar
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

function DocumentsStep({ token, onContinue }: { token: string; onContinue: () => void }) {
  const [documents, setDocuments] = useState<DocumentResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(() => {
    listPublicDocuments(token).then(setDocuments).catch(() => undefined);
  }, [token]);

  useEffect(() => {
    load();
    pollRef.current = setInterval(load, 6000);
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [load]);

  const allRequiredUploaded =
    documents !== null && documents.filter((d) => d.required).every((d) => d.currentVersionNumber > 0);

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await submitDocuments(token);
      onContinue();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo enviar tu documentación. Intenta de nuevo.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader title="Documentos" description="Sube una foto o PDF legible de cada documento requerido." />
        {documents === null ? (
          <p className="text-sm text-muted">Cargando…</p>
        ) : (
          <div className="flex flex-col gap-3">
            {documents.map((doc) => (
              <PublicDocumentRow key={doc.id} token={token} document={doc} onUploaded={load} />
            ))}
          </div>
        )}
      </Card>

      <Card>
        <div className="flex items-start gap-3">
          <MapPin className="mt-0.5 h-5 w-5 shrink-0 text-dark-gold" aria-hidden />
          <div>
            <p className="text-sm font-medium text-obsessed">¿No tienes tus documentos a la mano o no puedes subirlos?</p>
            <p className="mt-1 text-sm text-muted">
              Puedes acudir a nuestra oficina con tus documentos originales y nosotros los escaneamos por ti.
              También puedes pedirle ayuda a tu asesor.
            </p>
            <p className="mt-2 text-sm font-medium text-obsessed">{ORG_OFFICE_ADDRESS}</p>
            <a
              href={ORG_OFFICE_MAPS_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-1 inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:underline"
            >
              Ver ubicación en el mapa
            </a>
          </div>
        </div>
      </Card>

      {error ? <p className="text-sm text-danger-text">{error}</p> : null}

      <div className="flex justify-end">
        <Button size="lg" disabled={!allRequiredUploaded || submitting} onClick={handleSubmit}>
          {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
          Enviar documentación
        </Button>
      </div>
    </div>
  );
}

const documentStatusLabels: Record<DocumentResponse["status"], string> = {
  PENDING: "Pendiente",
  UPLOADED: "Recibido, en revisión",
  READY_FOR_REVIEW: "Recibido, en revisión",
  ACCEPTED: "Aceptado",
  RETURNED: "Necesita corrección",
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

function PublicDocumentRow({
  token,
  document,
  onUploaded,
}: {
  token: string;
  document: DocumentResponse;
  onUploaded: () => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploading(true);
    try {
      await uploadPublicDocumentVersion(token, document.id, Array.from(files));
      onUploaded();
    } catch {
      // El estado sigue reflejando "pendiente"; el cliente puede reintentar.
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border p-3">
      <div>
        <span className="text-sm font-medium text-obsessed">{documentTypeLabel(document.type)}</span>
        {!document.required ? <span className="ml-2 text-xs text-muted">(condicional)</span> : null}
      </div>
      <div className="flex items-center gap-2">
        <Badge tone={documentStatusTone[document.status]}>{documentStatusLabels[document.status]}</Badge>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png"
          className="hidden"
          onChange={handleFileSelected}
        />
        <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
          {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />}
          {document.currentVersionNumber > 0 ? "Subir de nuevo" : "Subir"}
        </Button>
      </div>
    </div>
  );
}

function ConfirmationStep({ token }: { token: string }) {
  const [expediente, setExpediente] = useState<PublicExpedienteResponse | null>(null);

  useEffect(() => {
    getPublicExpediente(token).then(setExpediente).catch(() => undefined);
  }, [token]);

  return (
    <Card>
      <CardHeader title="¡Listo!" description="Recibimos tu información." />
      <p className="text-sm text-obsessed">
        Tu documentación quedó registrada{expediente ? ` (estado actual: ${expediente.status})` : ""}. Nuestro equipo la
        revisará y te contactaremos con los datos de contacto que nos diste si hace falta algo más.
      </p>
    </Card>
  );
}
