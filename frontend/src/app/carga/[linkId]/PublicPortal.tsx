"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { Stepper } from "@/components/documents/Stepper";
import { PrivacyNoticeCard } from "@/components/privacy/PrivacyNoticeCard";
import { SignatureMock } from "@/components/privacy/SignatureMock";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ORG_OFFICE_ADDRESS, ORG_OFFICE_MAPS_URL } from "@/data/organization";
import { PRIVACY_CONSENT_TEXT, PRIVACY_SECONDARY_OPT_OUT_TEXT } from "@/data/privacy-reference";
import { ApiError } from "@/lib/api/client";
import {
  declareCivilStatus,
  getClientData,
  getPublicExpediente,
  listPublicDocuments,
  listPublicParticipants,
  recordPrivacyConsent,
  submitDocuments,
  updateClientData,
  uploadPublicDocumentVersion,
} from "@/lib/api/public";
import type {
  BackendCivilStatus,
  BackendMaritalRegime,
  PublicDocumentResponse,
  PublicExpedienteResponse,
  PublicParticipantResponse,
} from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";
import { civilStatusLabels, label, maritalRegimeLabels, returnReasonLabels } from "@/lib/labels";
import { AlertTriangle, CheckCircle2, HelpCircle, Loader2, MapPin, ShieldCheck, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

const steps = ["Confirmar", "Aviso de privacidad", "Tus datos", "Documentos", "Listo"];

function initialStepFor(status: PublicExpedienteResponse["status"]): number {
  if (status === "DRAFT" || status === "WAITING_PRIVACY") return 2;
  if (status === "WAITING_DOCUMENTS" || status === "CORRECTIONS_REQUESTED") return 4;
  return 5;
}

/** Qué ve el cliente sobre el avance de su trámite, en lenguaje sencillo. */
const clientStatusText: Record<PublicExpedienteResponse["status"], string> = {
  DRAFT: "Tu expediente está listo para que empieces.",
  WAITING_PRIVACY: "Falta que aceptes el aviso de privacidad.",
  WAITING_DOCUMENTS: "Falta que cargues tus documentos.",
  DOCUMENTS_RECEIVED: "Recibimos tus documentos; nuestro equipo los está revisando.",
  UNDER_REVIEW: "Nuestro equipo está revisando tus documentos.",
  CORRECTIONS_REQUESTED: "Hay documentos que necesitamos que corrijas. Revisa el motivo en cada uno.",
  DOCUMENTS_APPROVED: "Tus documentos fueron aceptados. Sigue la preparación del contrato.",
  RECEPTION_SIGNED: "Tus documentos fueron aceptados. Estamos preparando tu contrato.",
  CONTRACT_PREPARATION: "Estamos preparando tu contrato.",
  READY_FOR_SIGNATURE: "Tu contrato está listo para firmarse; cada firmante recibirá su liga personal de firma.",
  CONTRACT_SIGNED: "Tu contrato quedó firmado. Tu asesor te entregará tu tanto firmado.",
  PROPERTY_ACCEPTED: "Tu inmueble fue aceptado. ¡Gracias!",
  PROPERTY_REJECTED: "Tu asesor se pondrá en contacto contigo.",
  CLOSED: "Este expediente está cerrado.",
};

export function PublicPortal({ token }: { token: string }) {
  const [expediente, setExpediente] = useState<PublicExpedienteResponse | null>(null);
  const [step, setStep] = useState(1);
  const [maxReached, setMaxReached] = useState(1);
  const [invalid, setInvalid] = useState(false);

  const loadExpediente = useCallback(
    () =>
      getPublicExpediente(token)
        .then((data) => {
          setExpediente(data);
          return data;
        })
        .catch(() => {
          setInvalid(true);
          return null;
        }),
    [token],
  );

  useEffect(() => {
    loadExpediente();
  }, [loadExpediente]);

  const goTo = (next: number) => {
    setStep(next);
    setMaxReached((m) => Math.max(m, next));
  };

  if (invalid) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-app-bg px-4 text-center">
        <div>
          <p className="text-sm font-medium text-obsessed">Esta liga no es válida, ya venció o fue reemplazada por una nueva.</p>
          <p className="mt-2 text-sm text-muted">Pide a tu asesor una liga nueva.</p>
        </div>
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
          <a
            href="https://wa.me/527778005300"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-obsessed/70 hover:text-obsessed"
          >
            <HelpCircle className="h-4 w-4" aria-hidden />
            ¿Necesitas ayuda?
          </a>
        </div>
      </header>

      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 lg:px-8 lg:py-10">
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-obsessed">Expediente {expediente.folio}</h1>
          <Badge tone="neutral">Recepción de documentos</Badge>
        </div>
        <p className="max-w-2xl text-sm text-muted">No necesitas crear una cuenta. Esta liga es tu acceso personal: no la compartas.</p>

        <div className="mt-8 rounded-2xl border border-border bg-card p-5">
          <Stepper steps={steps} currentStep={step} maxReachedStep={maxReached} onStepClick={goTo} />
        </div>

        <div className="mt-8">
          {step === 1 ? <ConfirmStep expediente={expediente} onContinue={() => goTo(initialStepFor(expediente.status))} /> : null}
          {step === 2 ? <PrivacyStep token={token} onContinue={() => goTo(3)} /> : null}
          {step === 3 ? <ClientDataStep token={token} personType={expediente.personType} onContinue={() => goTo(4)} /> : null}
          {step === 4 ? (
            <DocumentsStep
              token={token}
              status={expediente.status}
              onSubmitted={async () => {
                await loadExpediente();
                goTo(5);
              }}
            />
          ) : null}
          {step === 5 ? <ConfirmationStep expediente={expediente} /> : null}
        </div>
      </main>

      <footer className="border-t border-border bg-card px-4 py-4 text-center lg:px-8">
        <BrandFooter className="mx-auto" />
      </footer>
    </div>
  );
}

/** El cliente confirma que la liga es suya antes de ver o cargar nada (evita cargar documentos en el expediente equivocado). */
function ConfirmStep({ expediente, onContinue }: { expediente: PublicExpedienteResponse; onContinue: () => void }) {
  return (
    <Card>
      <CardHeader title="Confirma que este es tu expediente" description="Mostramos los datos parcialmente para proteger tu privacidad." />
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div className="rounded-lg bg-app-bg p-3">
          <dt className="text-xs text-muted">Titular(es)</dt>
          <dd className="font-medium text-obsessed">{expediente.maskedOwnerName}</dd>
        </div>
        <div className="rounded-lg bg-app-bg p-3">
          <dt className="text-xs text-muted">Inmueble</dt>
          <dd className="font-medium text-obsessed">{expediente.maskedPropertyAddress}</dd>
        </div>
      </dl>
      <p className="mt-4 text-sm text-muted">{clientStatusText[expediente.status]}</p>
      <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:justify-between">
        <p className="flex items-start gap-2 text-sm text-muted">
          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
          ¿No reconoces estos datos? No continúes y avisa a tu asesor: la liga podría no ser para ti.
        </p>
        <Button size="lg" onClick={onContinue}>
          Sí, es mi expediente
        </Button>
      </div>
    </Card>
  );
}

function PrivacyStep({ token, onContinue }: { token: string; onContinue: () => void }) {
  const [mainConsent, setMainConsent] = useState(false);
  const [secondaryOptOut, setSecondaryOptOut] = useState(false);
  const [signatureDataUrl, setSignatureDataUrl] = useState<string | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
          <p className="mt-1.5 pl-7 text-xs text-muted">Esta preferencia es independiente del consentimiento principal: no bloquea el proceso.</p>
        </div>
        <div className="mt-5 border-t border-border pt-4">
          <p className="mb-2 text-sm font-medium text-obsessed">Firma del titular</p>
          <SignatureMock onSign={setSignatureDataUrl} signed={Boolean(signatureDataUrl)} />
        </div>
      </Card>
      {error ? <p className="text-sm text-danger-text">{error}</p> : null}
      <div className="flex justify-end">
        <Button size="lg" disabled={!mainConsent || !signatureDataUrl || submitting} onClick={handleContinue}>
          {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
          Continuar
        </Button>
      </div>
    </div>
  );
}

function ClientDataStep({
  token,
  personType,
  onContinue,
}: {
  token: string;
  personType: PublicExpedienteResponse["personType"];
  onContinue: () => void;
}) {
  const [loaded, setLoaded] = useState(false);
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [participants, setParticipants] = useState<PublicParticipantResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([getClientData(token), listPublicParticipants(token)])
      .then(([d, p]) => {
        setEmail(d.email ?? "");
        setPhone(d.phone ?? "");
        setParticipants(p);
        setLoaded(true);
      })
      .catch(() => setError("No se pudieron cargar tus datos. Recarga la página."));
  }, [token]);

  const setParticipant = (id: string, patch: Partial<PublicParticipantResponse>) =>
    setParticipants((prev) => prev.map((p) => (p.id === id ? { ...p, ...patch } : p)));

  const civilStatusMissing = personType === "FISICA" && participants.some((p) => !p.civilStatus);

  const handleContinue = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await updateClientData(token, { email, phone });
      for (const p of participants) {
        if (p.civilStatus) {
          await declareCivilStatus(token, p.id, p.civilStatus, p.civilStatus === "CASADO" ? p.maritalRegime : null);
        }
      }
      onContinue();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo guardar tu información. Intenta de nuevo.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Tus datos" description="Los usamos para avisarte del avance de tu expediente y para saber qué documentos pedirte." />
      {!loaded ? (
        <p className="text-sm text-muted">{error ?? "Cargando…"}</p>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
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
                placeholder="777 000 0000"
                className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
              />
            </div>
          </div>

          {personType === "FISICA" && participants.length > 0 ? (
            <div className="border-t border-border pt-4">
              <p className="text-sm font-medium text-obsessed">Estado civil de cada propietario</p>
              <p className="mb-3 text-xs text-muted">Si alguno está casado se le pedirá su acta de matrimonio.</p>
              <div className="flex flex-col gap-3">
                {participants.map((p) => (
                  <div key={p.id} className="grid gap-2 rounded-lg border border-border p-3 sm:grid-cols-3 sm:items-center">
                    <span className="text-sm font-medium text-obsessed">{p.displayName}</span>
                    <select
                      value={p.civilStatus ?? ""}
                      onChange={(e) => setParticipant(p.id, { civilStatus: (e.target.value || null) as BackendCivilStatus | null })}
                      className="rounded-lg border border-border bg-card px-3 py-2 text-sm"
                    >
                      <option value="">Estado civil…</option>
                      {Object.entries(civilStatusLabels).map(([v, l]) => (
                        <option key={v} value={v}>
                          {l}
                        </option>
                      ))}
                    </select>
                    {p.civilStatus === "CASADO" ? (
                      <select
                        value={p.maritalRegime ?? ""}
                        onChange={(e) => setParticipant(p.id, { maritalRegime: (e.target.value || null) as BackendMaritalRegime | null })}
                        className="rounded-lg border border-border bg-card px-3 py-2 text-sm"
                      >
                        <option value="">Régimen (si lo sabes)…</option>
                        {Object.entries(maritalRegimeLabels).map(([v, l]) => (
                          <option key={v} value={v}>
                            {l}
                          </option>
                        ))}
                      </select>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          {error ? <p className="text-sm text-danger-text">{error}</p> : null}
          <div className="flex justify-end">
            <Button size="lg" disabled={!email.trim() || !phone.trim() || civilStatusMissing || submitting} onClick={handleContinue}>
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
              Continuar
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

function DocumentsStep({
  token,
  status,
  onSubmitted,
}: {
  token: string;
  status: PublicExpedienteResponse["status"];
  onSubmitted: () => Promise<void>;
}) {
  const [documents, setDocuments] = useState<PublicDocumentResponse[] | null>(null);
  const [participants, setParticipants] = useState<PublicParticipantResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    listPublicDocuments(token)
      .then(setDocuments)
      .catch(() => undefined);
  }, [token]);

  useEffect(() => {
    load();
    listPublicParticipants(token).then(setParticipants).catch(() => undefined);
    const timer = setInterval(load, 6000);
    return () => clearInterval(timer);
  }, [load, token]);

  const names = Object.fromEntries(participants.map((p) => [p.id, p.displayName]));
  const required = (documents ?? []).filter((d) => d.required);
  const toFix = (documents ?? []).filter(
    (d) => d.status === "RETURNED" || d.status === "REJECTED" || d.qualityIssue || d.looksLikeWrongDocument,
  );
  const allRequiredUploaded = documents !== null && required.every((d) => d.status !== "PENDING");
  const canSubmit = status === "WAITING_DOCUMENTS";

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await submitDocuments(token);
      await onSubmitted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo enviar tu documentación. Intenta de nuevo.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      {toFix.length > 0 ? (
        <div className="flex items-start gap-2 rounded-2xl border border-warning-text/30 bg-warning-bg p-4 text-sm text-warning-text">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden />
          <p>
            {toFix.length === 1 ? "Hay 1 documento que necesitamos que corrijas." : `Hay ${toFix.length} documentos que necesitamos que corrijas.`} Abajo
            te decimos cuál y por qué.
          </p>
        </div>
      ) : null}

      <Card>
        <CardHeader
          title="Documentos"
          description="Sube una foto clara (vertical, con buena luz, el documento completo y sin reflejos) de cada documento. Puedes subir varias fotos si tiene varias páginas."
        />
        {documents === null ? (
          <p className="text-sm text-muted">Cargando…</p>
        ) : (
          <div className="flex flex-col gap-3">
            {documents.map((doc) => (
              <PublicDocumentRow
                key={doc.id}
                token={token}
                document={doc}
                participantName={doc.participantId ? names[doc.participantId] : undefined}
                onUploaded={load}
              />
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
              Puedes acudir a nuestra oficina con tus documentos originales y nosotros los escaneamos por ti. También puedes pedirle ayuda a tu asesor.
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
      {canSubmit ? (
        <div className="flex flex-col items-end gap-2">
          {!allRequiredUploaded ? <p className="text-xs text-muted">Carga todos los documentos obligatorios para poder enviarlos.</p> : null}
          <Button size="lg" disabled={!allRequiredUploaded || submitting} onClick={handleSubmit}>
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
            Enviar documentación
          </Button>
        </div>
      ) : (
        <p className="text-right text-sm text-muted">
          Tu documentación ya fue enviada. Si corriges un documento, nuestro equipo lo revisará automáticamente; no necesitas volver a enviar.
        </p>
      )}
    </div>
  );
}

const clientDocStatus: Record<PublicDocumentResponse["status"], { text: string; tone: "neutral" | "info" | "warning" | "success" | "danger" }> = {
  PENDING: { text: "Pendiente", tone: "neutral" },
  UPLOADED: { text: "Recibido, verificando", tone: "info" },
  READY_FOR_REVIEW: { text: "Recibido, en revisión", tone: "info" },
  ACCEPTED: { text: "Aceptado", tone: "success" },
  RETURNED: { text: "Necesita corrección", tone: "warning" },
  REJECTED: { text: "Rechazado", tone: "danger" },
  REPLACED: { text: "Reemplazado", tone: "neutral" },
  NOT_APPLICABLE: { text: "No se requiere", tone: "neutral" },
};

function PublicDocumentRow({
  token,
  document: doc,
  participantName,
  onUploaded,
}: {
  token: string;
  document: PublicDocumentResponse;
  participantName?: string;
  onUploaded: () => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [justUploaded, setJustUploaded] = useState(false);

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploading(true);
    setError(null);
    try {
      await uploadPublicDocumentVersion(token, doc.id, Array.from(files));
      setJustUploaded(true);
      onUploaded();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo subir el archivo. Revisa tu conexión e intenta de nuevo.");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const status = clientDocStatus[doc.status];
  const needsFix = doc.status === "RETURNED" || doc.status === "REJECTED" || Boolean(doc.qualityIssue) || doc.looksLikeWrongDocument;
  const canUpload = doc.status !== "ACCEPTED" && doc.status !== "NOT_APPLICABLE";

  return (
    <div className={`rounded-lg border p-3 ${needsFix ? "border-warning-text/40" : "border-border"}`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <span className="text-sm font-medium text-obsessed">{documentTypeLabel(doc.type)}</span>
          {participantName ? <span className="text-sm text-muted"> — {participantName}</span> : null}
          {!doc.required ? <span className="ml-2 text-xs text-muted">(opcional)</span> : null}
        </div>
        <div className="flex items-center gap-2">
          {doc.processing ? (
            <Badge tone="info">
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden /> Verificando la foto
            </Badge>
          ) : (
            <Badge tone={status.tone}>{status.text}</Badge>
          )}
          <input ref={fileInputRef} type="file" accept="image/jpeg,image/png" multiple className="hidden" onChange={handleFileSelected} />
          {canUpload ? (
            <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />}
              {doc.currentVersionNumber > 0 ? "Subir de nuevo" : "Subir"}
            </Button>
          ) : null}
        </div>
      </div>
      {doc.status === "RETURNED" || doc.status === "REJECTED" ? (
        <p className="mt-2 rounded-md bg-warning-bg px-3 py-2 text-sm text-warning-text">
          <strong>Qué corregir:</strong> {label(returnReasonLabels, doc.correctionReasonCode)}
          {doc.correctionComment ? ` — ${doc.correctionComment}` : ""}
        </p>
      ) : null}
      {doc.qualityIssue ? (
        <p className="mt-2 rounded-md bg-warning-bg px-3 py-2 text-sm text-warning-text">
          <strong>La foto no se pudo leer:</strong> {doc.qualityIssue}
        </p>
      ) : null}
      {doc.looksLikeWrongDocument ? (
        <p className="mt-2 rounded-md bg-warning-bg px-3 py-2 text-sm text-warning-text">
          El archivo que subiste no parece ser tu {documentTypeLabel(doc.type)}. Revisa que sea el documento correcto y súbelo de nuevo.
        </p>
      ) : null}
      {justUploaded && !error && !doc.qualityIssue ? (
        <p className="mt-2 flex items-center gap-1.5 text-xs text-success-text">
          <CheckCircle2 className="h-3.5 w-3.5" aria-hidden /> Archivo recibido.
        </p>
      ) : null}
      {error ? <p className="mt-2 text-sm text-danger-text">{error}</p> : null}
    </div>
  );
}

function ConfirmationStep({ expediente }: { expediente: PublicExpedienteResponse }) {
  return (
    <Card>
      <CardHeader title="Estado de tu trámite" />
      <p className="text-sm text-obsessed">{clientStatusText[expediente.status]}</p>
      <p className="mt-3 text-sm text-muted">
        Te avisaremos por correo cuando haya algo que hacer. Si necesitas corregir un documento, vuelve a esta misma liga.
      </p>
    </Card>
  );
}
