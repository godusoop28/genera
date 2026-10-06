"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { Stepper } from "@/components/documents/Stepper";
import { PrivacyNoticeCard } from "@/components/privacy/PrivacyNoticeCard";
import { SignatureMock } from "@/components/privacy/SignatureMock";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { SaveStatus } from "@/components/ui/SaveStatus";
import { ORG_OFFICE_ADDRESS, ORG_OFFICE_MAPS_URL } from "@/data/organization";
import { PRIVACY_CONSENT_TEXT, PRIVACY_SECONDARY_OPT_OUT_TEXT } from "@/data/privacy-reference";
import { ApiError } from "@/lib/api/client";
import {
  addPublicCoOwner,
  declareCivilStatus,
  getClientData,
  getPublicExpediente,
  listPublicDocuments,
  listPublicParticipants,
  recordPrivacyConsent,
  removePublicCoOwner,
  renamePublicOwner,
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
import { readDraft, writeDraft } from "@/lib/drafts";
import { DraftSync, type DraftSaveState } from "@/lib/draft-sync";
import { ACCEPTED_FILE_TYPES, UploadValidationError } from "@/lib/upload-limits";
import { publicDraftTransport, sessionDraftStore } from "@/lib/use-server-draft";
import { civilStatusLabels, label, maritalRegimeLabels, participantRoleLabels, returnReasonLabels } from "@/lib/labels";
import { AlertTriangle, CheckCircle2, HelpCircle, Loader2, MapPin, Pencil, Plus, ShieldCheck, Trash2, Upload } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const steps = ["Confirmar", "Aviso de privacidad", "Tus datos", "Documentos", "Listo"];

function initialStepFor(status: PublicExpedienteResponse["status"]): number {
  if (status === "DRAFT" || status === "WAITING_PRIVACY") return 2;
  if (status === "WAITING_DOCUMENTS" || status === "CORRECTIONS_REQUESTED") return 4;
  return 5;
}

/** Mientras se reciben o revisan sus documentos el cliente puede corregir a los propietarios (igual que el backend). */
function canEditOwners(expediente: PublicExpedienteResponse): boolean {
  return (
    expediente.personType === "FISICA" &&
    ["WAITING_DOCUMENTS", "DOCUMENTS_RECEIVED", "UNDER_REVIEW", "CORRECTIONS_REQUESTED"].includes(expediente.status)
  );
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
  // Al recargar la página el cliente vuelve al paso en el que iba. (Seguro de
  // leer en el estado inicial: nada depende del paso hasta cargar el expediente.)
  const stepKey = `portal:${token}:step`;
  const [savedStep] = useState(() => {
    const saved = readDraft<{ step: number; maxReached: number }>(stepKey);
    return saved && saved.step >= 1 && saved.step <= steps.length ? saved : null;
  });
  const [step, setStep] = useState(savedStep?.step ?? 1);
  const [maxReached, setMaxReached] = useState(savedStep ? Math.max(savedStep.step, savedStep.maxReached) : 1);
  const [invalid, setInvalid] = useState(false);
  const [connectionError, setConnectionError] = useState(false);

  const loadExpediente = useCallback(
    () =>
      getPublicExpediente(token)
        .then((data) => {
          setExpediente(data);
          setConnectionError(false);
          return data;
        })
        .catch((err) => {
          // Solo una liga inválida o vencida se trata como tal. Un error de red o
          // del servidor (p. ej. mientras despierta) se reintenta solo, sin
          // sacar al cliente de donde iba ni perder lo que capturó.
          if (err instanceof ApiError && [401, 403, 404, 410].includes(err.status)) {
            setInvalid(true);
          } else {
            setConnectionError(true);
          }
          return null;
        }),
    [token],
  );

  useEffect(() => {
    loadExpediente();
  }, [loadExpediente]);

  useEffect(() => {
    if (!connectionError) return;
    const timer = setTimeout(() => void loadExpediente(), 5000);
    return () => clearTimeout(timer);
  }, [connectionError, loadExpediente]);

  // El paso también se guarda en el servidor: al abrir la liga en otro equipo se continúa donde se iba.
  const stepTransport = useMemo(() => publicDraftTransport(token, "portal-step"), [token]);
  useEffect(() => {
    if (savedStep) return;
    stepTransport
      .load()
      .then((payload) => {
        if (!payload) return;
        const saved = JSON.parse(payload) as { step: number; maxReached: number };
        if (saved.step >= 1 && saved.step <= steps.length) {
          setStep(saved.step);
          setMaxReached(Math.max(saved.step, saved.maxReached));
        }
      })
      .catch(() => undefined);
  }, [savedStep, stepTransport]);

  const goTo = (next: number) => {
    const reached = Math.max(maxReached, next);
    setStep(next);
    setMaxReached(reached);
    writeDraft(stepKey, { step: next, maxReached: reached });
    void stepTransport.save(JSON.stringify({ step: next, maxReached: reached })).catch(() => undefined);
  };

  // El paso guardado puede ser "Listo" de un envío anterior; si el equipo
  // devolvió o rechazó un documento, el cliente cae en "Documentos" para
  // volver a subirlo.
  const shownStep = expediente?.status === "CORRECTIONS_REQUESTED" && step === 5 ? 4 : step;

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
        <div>
          <p className="text-sm text-muted">{connectionError ? "No pudimos conectarnos. Reintentando en unos segundos…" : "Cargando…"}</p>
          {connectionError ? (
            <Button variant="secondary" size="sm" className="mt-3" onClick={() => void loadExpediente()}>
              Reintentar ahora
            </Button>
          ) : null}
        </div>
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

        {connectionError ? (
          <p className="mt-4 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
            Se perdió la conexión por un momento. Lo que capturaste se conserva; reintentando…
          </p>
        ) : null}

        <div className="mt-8 rounded-2xl border border-border bg-card p-5">
          <Stepper steps={steps} currentStep={shownStep} maxReachedStep={maxReached} onStepClick={goTo} />
        </div>

        <div className="mt-8">
          {shownStep === 1 ? <ConfirmStep expediente={expediente} onContinue={() => goTo(initialStepFor(expediente.status))} /> : null}
          {shownStep === 2 ? <PrivacyStep token={token} onContinue={() => goTo(3)} /> : null}
          {shownStep === 3 ? (
            <ClientDataStep
              token={token}
              personType={expediente.personType}
              ownersEditable={canEditOwners(expediente)}
              onOwnersChanged={() => void loadExpediente()}
              onContinue={() => goTo(4)}
            />
          ) : null}
          {shownStep === 4 ? (
            <DocumentsStep
              token={token}
              status={expediente.status}
              onSubmitted={async () => {
                await loadExpediente();
                goTo(5);
              }}
            />
          ) : null}
          {shownStep === 5 ? (
            <ConfirmationStep expediente={expediente} onEditOwners={canEditOwners(expediente) ? () => goTo(3) : undefined} />
          ) : null}
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

interface ClientDataDraft {
  email: string;
  phone: string;
  civil: Record<string, Pick<PublicParticipantResponse, "civilStatus" | "maritalRegime">>;
}

function ClientDataStep({
  token,
  personType,
  ownersEditable,
  onOwnersChanged,
  onContinue,
}: {
  token: string;
  personType: PublicExpedienteResponse["personType"];
  ownersEditable: boolean;
  onOwnersChanged: () => void;
  onContinue: () => void;
}) {
  const [loaded, setLoaded] = useState(false);
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [participants, setParticipants] = useState<PublicParticipantResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Lo capturado se autoguarda en el servidor (borrador de la liga): sobrevive
  // a recargar la página, a cerrar el navegador o a cambiar de celular.
  const draftKey = `portal:${token}:data`;
  const syncRef = useRef<DraftSync | null>(null);
  const [draftStatus, setDraftStatus] = useState<DraftSaveState>("idle");
  const [draftSavedAt, setDraftSavedAt] = useState<Date | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    const sync = new DraftSync(publicDraftTransport(token, "client-data"), {
      local: sessionDraftStore(draftKey),
      onStateChange: (state, savedAt) => {
        if (!active) return;
        setDraftStatus(state);
        setDraftSavedAt(savedAt);
      },
    });
    syncRef.current = sync;
    Promise.all([getClientData(token), listPublicParticipants(token), sync.restore()])
      .then(([d, p, payload]) => {
        if (!active) return;
        let draft: ClientDataDraft | null = null;
        try {
          draft = payload ? (JSON.parse(payload) as ClientDataDraft) : null;
        } catch {
          draft = null;
        }
        setEmail(draft?.email ?? d.email ?? "");
        setPhone(draft?.phone ?? d.phone ?? "");
        setParticipants(p.map((participant) => ({ ...participant, ...(draft?.civil?.[participant.id] ?? {}) })));
        setError(null);
        setLoaded(true);
      })
      .catch(() => {
        if (!active) return;
        setError("No pudimos cargar tus datos; reintentando…");
        setTimeout(() => active && setLoadAttempt((n) => n + 1), 4000);
      });
    return () => {
      active = false;
      void sync.flush().finally(() => sync.dispose());
    };
  }, [token, draftKey, loadAttempt]);

  useEffect(() => {
    if (!loaded) return;
    syncRef.current?.schedule(
      JSON.stringify({
        email,
        phone,
        civil: Object.fromEntries(participants.map((p) => [p.id, { civilStatus: p.civilStatus, maritalRegime: p.maritalRegime }])),
      } satisfies ClientDataDraft),
    );
  }, [loaded, email, phone, participants]);

  const setParticipant = (id: string, patch: Partial<PublicParticipantResponse>) =>
    setParticipants((prev) => prev.map((p) => (p.id === id ? { ...p, ...patch } : p)));

  const civilStatusMissing = personType === "FISICA" && participants.some((p) => !p.civilStatus);

  // Corrección de propietarios: se guarda al momento en el servidor, que
  // recalcula qué documentos pedir (p. ej. los del copropietario agregado).
  const [ownerBusy, setOwnerBusy] = useState(false);
  const [ownerError, setOwnerError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const [newCoOwner, setNewCoOwner] = useState<string | null>(null);

  const runOwnerChange = async (change: () => Promise<void>) => {
    setOwnerBusy(true);
    setOwnerError(null);
    try {
      await change();
      onOwnersChanged();
    } catch (err) {
      setOwnerError(err instanceof ApiError ? err.message : "No se pudo guardar el cambio. Intenta de nuevo.");
    } finally {
      setOwnerBusy(false);
    }
  };

  const addCoOwner = (fullName: string) =>
    runOwnerChange(async () => {
      const added = await addPublicCoOwner(token, fullName.trim());
      setParticipants((prev) => [...prev, added]);
      setNewCoOwner(null);
    });

  const renameOwner = (id: string, fullName: string) =>
    runOwnerChange(async () => {
      const updated = await renamePublicOwner(token, id, fullName.trim());
      setParticipant(id, { displayName: updated.displayName });
      setRenaming(null);
    });

  const removeCoOwner = (id: string) =>
    runOwnerChange(async () => {
      await removePublicCoOwner(token, id);
      setParticipants((prev) => prev.filter((p) => p.id !== id));
    });

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
      await syncRef.current?.clear();
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
              <p className="text-sm font-medium text-obsessed">Propietarios y su estado civil</p>
              <p className="mb-3 text-xs text-muted">
                Si alguno está casado se le pedirá su acta de matrimonio.
                {ownersEditable
                  ? " Si falta o sobra un propietario, o un nombre está mal, corrígelo aquí: te pediremos los documentos que hagan falta."
                  : ""}
              </p>
              <div className="flex flex-col gap-3">
                {participants.map((p) => (
                  <div key={p.id} className="grid gap-2 rounded-lg border border-border p-3 sm:grid-cols-3 sm:items-center">
                    {renaming?.id === p.id ? (
                      <div className="flex flex-col gap-2 sm:col-span-3 sm:flex-row sm:items-center">
                        <input
                          autoFocus
                          value={renaming.name}
                          onChange={(e) => setRenaming({ id: p.id, name: e.target.value })}
                          placeholder="Nombre completo correcto"
                          className="flex-1 rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
                        />
                        <div className="flex gap-2">
                          <Button size="sm" disabled={!renaming.name.trim() || ownerBusy} onClick={() => void renameOwner(p.id, renaming.name)}>
                            Guardar
                          </Button>
                          <Button variant="secondary" size="sm" disabled={ownerBusy} onClick={() => setRenaming(null)}>
                            Cancelar
                          </Button>
                        </div>
                      </div>
                    ) : null}
                    <div className="flex items-center justify-between gap-2">
                      <div className="min-w-0">
                        <span className="block text-sm font-medium text-obsessed">{p.displayName}</span>
                        <span className="text-xs text-muted">{participantRoleLabels[p.role]}</span>
                      </div>
                      {ownersEditable && renaming?.id !== p.id ? (
                        <div className="flex shrink-0 gap-1">
                          <button
                            type="button"
                            title="Corregir nombre"
                            aria-label={`Corregir nombre de ${p.displayName}`}
                            disabled={ownerBusy}
                            onClick={() => setRenaming({ id: p.id, name: "" })}
                            className="rounded-md p-1.5 text-muted hover:bg-app-bg hover:text-obsessed"
                          >
                            <Pencil className="h-4 w-4" aria-hidden />
                          </button>
                          {p.role === "CO_OWNER" ? (
                            <button
                              type="button"
                              title="Quitar copropietario"
                              aria-label={`Quitar a ${p.displayName}`}
                              disabled={ownerBusy}
                              onClick={() => void removeCoOwner(p.id)}
                              className="rounded-md p-1.5 text-muted hover:bg-danger-bg hover:text-danger-text"
                            >
                              <Trash2 className="h-4 w-4" aria-hidden />
                            </button>
                          ) : null}
                        </div>
                      ) : null}
                    </div>
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
              {ownersEditable ? (
                newCoOwner === null ? (
                  <Button variant="secondary" size="sm" className="mt-3" disabled={ownerBusy} onClick={() => setNewCoOwner("")}>
                    <Plus className="h-4 w-4" aria-hidden />
                    Agregar copropietario
                  </Button>
                ) : (
                  <div className="mt-3 flex flex-col gap-2 rounded-lg border border-gold/40 p-3 sm:flex-row sm:items-center">
                    <input
                      autoFocus
                      value={newCoOwner}
                      onChange={(e) => setNewCoOwner(e.target.value)}
                      placeholder="Nombre completo del copropietario"
                      className="flex-1 rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
                    />
                    <div className="flex gap-2">
                      <Button size="sm" disabled={!newCoOwner.trim() || ownerBusy} onClick={() => void addCoOwner(newCoOwner)}>
                        {ownerBusy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
                        Agregar
                      </Button>
                      <Button variant="secondary" size="sm" disabled={ownerBusy} onClick={() => setNewCoOwner(null)}>
                        Cancelar
                      </Button>
                    </div>
                  </div>
                )
              ) : null}
              {ownerError ? <p className="mt-2 text-sm text-danger-text">{ownerError}</p> : null}
            </div>
          ) : null}

          {error ? <p className="text-sm text-danger-text">{error}</p> : null}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <SaveStatus status={draftStatus} lastSavedAt={draftSavedAt} />
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
  // Solo se le pide corregir al cliente lo que el equipo devolvió o lo que de verdad no se pudo leer.
  const toFix = (documents ?? []).filter((d) => d.status === "RETURNED" || d.status === "REJECTED" || d.qualityIssue);
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
          description="Sube una foto (vertical u horizontal, que se lea) o el PDF de cada documento. Puedes subir varios archivos si tiene varias páginas. Fotos del iPhone (HEIC) también funcionan."
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
  const [progress, setProgress] = useState<number | null>(null);

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    setUploading(true);
    setError(null);
    setProgress(null);
    try {
      await uploadPublicDocumentVersion(token, doc.id, Array.from(files), (p) => setProgress(p.percent));
      setJustUploaded(true);
      onUploaded();
    } catch (err) {
      setError(
        err instanceof ApiError || err instanceof UploadValidationError
          ? err.message
          : "No se pudo subir el archivo. Revisa tu conexión e intenta de nuevo.",
      );
    } finally {
      setUploading(false);
      setProgress(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const status = clientDocStatus[doc.status];
  const needsFix = doc.status === "RETURNED" || doc.status === "REJECTED" || Boolean(doc.qualityIssue);
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
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden /> Verificando el archivo
            </Badge>
          ) : (
            <Badge tone={status.tone}>{status.text}</Badge>
          )}
          <input ref={fileInputRef} type="file" accept={ACCEPTED_FILE_TYPES} multiple className="hidden" onChange={handleFileSelected} />
          {canUpload ? (
            <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Upload className="h-4 w-4" aria-hidden />}
              {uploading ? (progress !== null && progress < 100 ? `Subiendo ${progress}%` : "Preparando…") : doc.currentVersionNumber > 0 ? "Subir de nuevo" : "Subir"}
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
          <strong>El archivo no se pudo leer:</strong> {doc.qualityIssue}
        </p>
      ) : null}
      {uploading && progress !== null ? (
        <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-app-bg" aria-hidden>
          <div className="h-full bg-gold transition-all" style={{ width: `${progress}%` }} />
        </div>
      ) : null}
      {doc.looksLikeWrongDocument && !doc.qualityIssue && doc.status !== "RETURNED" && doc.status !== "REJECTED" ? (
        <p className="mt-2 rounded-md bg-app-bg px-3 py-2 text-xs text-muted">
          Recibimos tu archivo. Nuestro equipo confirmará que sea tu {documentTypeLabel(doc.type)}; si hiciera falta otro, te avisaremos aquí.
        </p>
      ) : null}
      {justUploaded && !error && !doc.qualityIssue ? (
        <p className="mt-2 flex items-center gap-1.5 text-xs text-success-text">
          <CheckCircle2 className="h-3.5 w-3.5" aria-hidden /> Archivo recibido. La verificación sigue en segundo plano: puedes continuar con los demás documentos.
        </p>
      ) : null}
      {error ? <p className="mt-2 text-sm text-danger-text">{error}</p> : null}
    </div>
  );
}

function ConfirmationStep({ expediente, onEditOwners }: { expediente: PublicExpedienteResponse; onEditOwners?: () => void }) {
  return (
    <Card>
      <CardHeader title="Estado de tu trámite" />
      <p className="text-sm text-obsessed">{clientStatusText[expediente.status]}</p>
      <p className="mt-3 text-sm text-muted">
        Te avisaremos por correo cuando haya algo que hacer. Si necesitas corregir un documento, vuelve a esta misma liga.
      </p>
      {onEditOwners ? (
        <div className="mt-4 border-t border-border pt-4">
          <p className="text-sm text-muted">¿Falta o sobra un propietario, o un nombre está mal?</p>
          <Button variant="secondary" size="sm" className="mt-2" onClick={onEditOwners}>
            <Pencil className="h-4 w-4" aria-hidden />
            Corregir propietarios
          </Button>
        </div>
      ) : null}
    </Card>
  );
}
