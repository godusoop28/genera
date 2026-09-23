"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { DocumentsPanel } from "./DocumentsPanel";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { getActivity } from "@/lib/api/activity";
import { getClosingCase, getClosingNotes, addClosingNote } from "@/lib/api/closing";
import { getComplianceChecklist } from "@/lib/api/compliance";
import {
  acceptProperty,
  getExpediente,
  getParticipants,
  getRequirements,
  rejectProperty,
  signReception,
} from "@/lib/api/expedientes";
import { generatePublicLink } from "@/lib/api/public-link";
import { backendStatusLabels, backendStatusTone } from "@/lib/api/status-labels";
import type {
  ActivityResponse,
  BackendExpedienteStatus,
  ClosingCaseResponse,
  ClosingNoteResponse,
  ComplianceChecklistResponse,
  ExpedienteResponse,
  ParticipantResponse,
  RequirementResponse,
} from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { Copy, FileSignature, Link2, RefreshCw, ThumbsDown, ThumbsUp } from "lucide-react";
import { use, useCallback, useEffect, useState } from "react";

const PROPERTY_DECIDABLE_STATUSES: BackendExpedienteStatus[] = [
  "RECEPTION_SIGNED",
  "CONTRACT_PREPARATION",
  "READY_FOR_SIGNATURE",
];

const tabs = [
  { id: "resumen", label: "Resumen" },
  { id: "requisitos", label: "Participantes y requisitos" },
  { id: "cumplimiento", label: "Cumplimiento" },
  { id: "cierre", label: "Cierre de venta" },
  { id: "actividad", label: "Actividad" },
  { id: "documentos", label: "Documentos y contrato" },
] as const;

type TabId = (typeof tabs)[number]["id"];

export default function ExpedienteDetailPage({ params }: PageProps<"/expedientes/[id]">) {
  const { id } = use(params);
  const [tab, setTab] = useState<TabId>("resumen");
  const { showToast } = useToast();

  const [expediente, setExpediente] = useState<ExpedienteResponse | null>(null);
  const [participants, setParticipants] = useState<ParticipantResponse[]>([]);
  const [requirements, setRequirements] = useState<RequirementResponse[]>([]);
  const [activity, setActivity] = useState<ActivityResponse[]>([]);
  const [compliance, setCompliance] = useState<ComplianceChecklistResponse | null>(null);
  const [closingCase, setClosingCase] = useState<ClosingCaseResponse | "not_started" | null>(null);
  const [closingNotes, setClosingNotes] = useState<ClosingNoteResponse[]>([]);
  const [newNote, setNewNote] = useState("");
  const [publicLink, setPublicLink] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [signingReception, setSigningReception] = useState(false);
  const [decidingProperty, setDecidingProperty] = useState(false);
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(() => {
    const main = getExpediente(id)
      .then((data) => {
        setExpediente(data);
        setLoadError(null);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true);
        } else {
          setLoadError("No se pudo cargar el expediente desde el backend.");
        }
      });
    getParticipants(id).then(setParticipants).catch(() => undefined);
    getRequirements(id).then(setRequirements).catch(() => undefined);
    getActivity(id).then(setActivity).catch(() => undefined);
    getComplianceChecklist(id).then(setCompliance).catch(() => undefined);
    getClosingCase(id)
      .then(setClosingCase)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setClosingCase("not_started");
        }
      });
    getClosingNotes(id).then(setClosingNotes).catch(() => undefined);
    return main;
  }, [id]);

  // Antes el botón recargaba en silencio y parecía no hacer nada: ahora
  // muestra que está trabajando y confirma cuando terminó.
  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await load();
      showToast("Información del expediente actualizada.");
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    load();
  }, [load]);

  const handleGenerateLink = async () => {
    try {
      const link = await generatePublicLink(id);
      setPublicLink(link.url);
      showToast("Liga generada.");
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo generar la liga (${err.status}).` : "Error de conexión.");
    }
  };

  const handleAddNote = async () => {
    if (!newNote.trim()) return;
    try {
      await addClosingNote(id, newNote.trim());
      setNewNote("");
      getClosingNotes(id).then(setClosingNotes).catch(() => undefined);
      showToast("Nota agregada.");
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo agregar la nota (${err.status}).` : "Error de conexión.");
    }
  };

  const handleSignReception = async () => {
    setSigningReception(true);
    try {
      await signReception(id);
      showToast("Recepción firmada.");
      load();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo firmar la recepción (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setSigningReception(false);
    }
  };

  const handleAcceptProperty = async () => {
    setDecidingProperty(true);
    try {
      await acceptProperty(id);
      showToast("Inmueble aceptado.");
      load();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo aceptar el inmueble (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setDecidingProperty(false);
    }
  };

  const handleRejectProperty = async () => {
    if (!rejectReason.trim()) return;
    setDecidingProperty(true);
    try {
      await rejectProperty(id, rejectReason.trim());
      showToast("Inmueble rechazado.");
      setShowRejectForm(false);
      setRejectReason("");
      load();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo rechazar el inmueble (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setDecidingProperty(false);
    }
  };

  if (notFound) {
    return (
      <PageContainer title="Expediente no encontrado">
        <p className="text-sm text-muted">Este expediente no existe en el backend.</p>
      </PageContainer>
    );
  }

  if (loadError) {
    return (
      <PageContainer title="Error">
        <p className="text-sm text-danger-text">{loadError}</p>
      </PageContainer>
    );
  }

  if (!expediente) {
    return (
      <PageContainer title="Cargando…">
        <p className="text-sm text-muted">Cargando expediente desde el backend…</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer title={expediente.ownerDisplayName} subtitle="Detalle del expediente documental (datos reales del backend).">
      <Card className="mb-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="rounded-lg bg-app-bg px-2.5 py-1 font-mono text-xs font-medium tracking-wide text-obsessed/70">
              {expediente.folio}
            </span>
            <Badge tone={backendStatusTone[expediente.status]}>{backendStatusLabels[expediente.status]}</Badge>
          </div>
          <Button variant="secondary" size="sm" onClick={handleRefresh} disabled={refreshing}>
            <RefreshCw className={cn("h-4 w-4", refreshing && "animate-spin")} aria-hidden />
            {refreshing ? "Actualizando…" : "Recargar información"}
          </Button>
        </div>
        {expediente.propertyAddress ? (
          <p className="mt-3 text-sm text-muted">{expediente.propertyAddress}</p>
        ) : null}
      </Card>

      <div className="mb-6 flex gap-1 overflow-x-auto border-b border-border">
        {tabs.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              "shrink-0 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
              tab === t.id ? "border-gold text-obsessed" : "border-transparent text-muted hover:text-obsessed",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "resumen" ? (
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader title="Datos del expediente" />
            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Field label="Tipo de persona" value={expediente.personType} />
              <Field label="Carácter del firmante" value={expediente.signerCharacter} />
              <Field label="Acreditación" value={expediente.accreditationType} />
              <Field label="Régimen de condominio" value={expediente.condominiumRegime ? "Sí" : "No"} />
              <Field label="Tipo de inmueble" value={expediente.propertyCaseType} />
              <Field label="Situación jurídica declarada" value={expediente.declaredLegalStatus} />
              <Field label="Creado" value={new Date(expediente.createdAt).toLocaleString("es-MX")} />
              <Field label="Última actualización" value={new Date(expediente.updatedAt).toLocaleString("es-MX")} />
              {expediente.decisionReason ? <Field label="Motivo de decisión" value={expediente.decisionReason} /> : null}
            </dl>
          </Card>

          <Card>
            <CardHeader title="Liga pública para el cliente" description="El cliente no tiene cuenta: accede solo con esta liga." />
            <div className="flex flex-wrap items-center gap-3">
              <Button onClick={handleGenerateLink}>
                <Link2 className="h-4 w-4" aria-hidden />
                Generar liga
              </Button>
              {publicLink ? (
                <div className="flex items-center gap-2 rounded-lg border border-border bg-app-bg px-3 py-2 text-sm">
                  <code className="max-w-xs truncate">{publicLink}</code>
                  <button
                    type="button"
                    onClick={() => navigator.clipboard.writeText(publicLink)}
                    aria-label="Copiar liga"
                    className="text-muted hover:text-obsessed"
                  >
                    <Copy className="h-4 w-4" aria-hidden />
                  </button>
                </div>
              ) : null}
            </div>
          </Card>

          {expediente.status === "DOCUMENTS_APPROVED" ? (
            <Card>
              <CardHeader
                title="Recepción documental"
                description="Los documentos ya fueron aprobados; firma la recepción para continuar con el contrato."
              />
              <Button onClick={handleSignReception} disabled={signingReception}>
                <FileSignature className="h-4 w-4" aria-hidden />
                Firmar recepción
              </Button>
            </Card>
          ) : null}

          {PROPERTY_DECIDABLE_STATUSES.includes(expediente.status) ? (
            <Card>
              <CardHeader title="Decisión sobre el inmueble" description="Cierra el ciclo del expediente: aceptado o rechazado." />
              <div className="flex flex-wrap items-center gap-3">
                <Button onClick={handleAcceptProperty} disabled={decidingProperty}>
                  <ThumbsUp className="h-4 w-4" aria-hidden />
                  Aceptar inmueble
                </Button>
                <Button variant="danger" onClick={() => setShowRejectForm((v) => !v)} disabled={decidingProperty}>
                  <ThumbsDown className="h-4 w-4" aria-hidden />
                  Rechazar inmueble
                </Button>
              </div>
              {showRejectForm ? (
                <div className="mt-3 flex flex-col gap-2 rounded-lg bg-app-bg p-3">
                  <input
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Motivo del rechazo…"
                    className="rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
                  />
                  <div className="flex gap-2">
                    <Button size="sm" variant="danger" onClick={handleRejectProperty} disabled={decidingProperty || !rejectReason.trim()}>
                      Confirmar rechazo
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setShowRejectForm(false)}>
                      Cancelar
                    </Button>
                  </div>
                </div>
              ) : null}
            </Card>
          ) : null}
        </div>
      ) : null}

      {tab === "requisitos" ? (
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader title="Participantes" />
            {participants.length === 0 ? (
              <p className="text-sm text-muted">Sin participantes.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {participants.map((p) => (
                  <li key={p.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm">
                    <span className="font-medium text-obsessed">{p.fullName}</span>
                    <Badge tone="neutral">{p.role}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card>
            <CardHeader title="Documentos requeridos" description="Calculado por el backend, no por el frontend." />
            {requirements.length === 0 ? (
              <p className="text-sm text-muted">Aún no se han calculado requisitos.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {requirements.map((r) => (
                  <li
                    key={r.requirementCode}
                    className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm"
                  >
                    <span className="text-obsessed">{r.type}</span>
                    <div className="flex items-center gap-2">
                      {r.conditional ? <Badge tone="neutral">Condicional</Badge> : null}
                      <Badge tone={r.required ? "gold" : "neutral"}>{r.required ? "Obligatorio" : "No aplica"}</Badge>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      ) : null}

      {tab === "cumplimiento" ? (
        <Card>
          <CardHeader
            title="Control documental de cumplimiento"
            description="Verificación interna, no constituye una garantía legal frente al cliente."
          />
          {!compliance ? (
            <p className="text-sm text-muted">Cargando checklist…</p>
          ) : (
            <div className="flex flex-col gap-2">
              <Badge tone={compliance.allPassed ? "success" : "warning"} className="mb-2 w-fit">
                {compliance.allPassed ? "Todo en orden" : "Pendientes por resolver"}
              </Badge>
              {compliance.items.map((item) => (
                <div
                  key={item.code}
                  className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm"
                >
                  <div>
                    <p className="font-medium text-obsessed">{item.code}</p>
                    <p className="text-xs text-muted">{item.detail}</p>
                  </div>
                  <Badge tone={item.passed ? "success" : "warning"}>{item.passed ? "OK" : "Pendiente"}</Badge>
                </div>
              ))}
            </div>
          )}
        </Card>
      ) : null}

      {tab === "cierre" ? (
        <Card>
          <CardHeader title="Seguimiento de cierre" />
          {closingCase === null ? (
            <p className="text-sm text-muted">Cargando…</p>
          ) : closingCase === "not_started" ? (
            <p className="text-sm text-muted">
              El seguimiento de cierre se habilita automáticamente cuando el inmueble es aceptado.
            </p>
          ) : (
            <div className="flex flex-col gap-4">
              <Badge tone="info" className="w-fit">
                {closingCase.status}
              </Badge>
              <div>
                <p className="mb-2 text-sm font-medium text-obsessed">Notas</p>
                <ul className="mb-3 flex flex-col gap-2">
                  {closingNotes.map((n) => (
                    <li key={n.id} className="rounded-lg border border-border px-3 py-2 text-sm text-obsessed">
                      {n.note}
                      <p className="mt-1 text-xs text-muted">{new Date(n.createdAt).toLocaleString("es-MX")}</p>
                    </li>
                  ))}
                </ul>
                <div className="flex gap-2">
                  <input
                    value={newNote}
                    onChange={(e) => setNewNote(e.target.value)}
                    placeholder="Agregar nota…"
                    className="flex-1 rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
                  />
                  <Button size="sm" onClick={handleAddNote}>
                    Agregar
                  </Button>
                </div>
              </div>
            </div>
          )}
        </Card>
      ) : null}

      {tab === "actividad" ? (
        <Card>
          <CardHeader title="Línea de tiempo" />
          {activity.length === 0 ? (
            <p className="text-sm text-muted">Sin actividad todavía.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {activity.map((a) => (
                <li key={a.id} className="flex gap-3 border-l-2 border-gold pl-3">
                  <div>
                    <p className="text-sm text-obsessed">{a.message}</p>
                    <p className="text-xs text-muted">{new Date(a.occurredAt).toLocaleString("es-MX")}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      ) : null}

      {tab === "documentos" ? <DocumentsPanel expedienteId={id} /> : null}
    </PageContainer>
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
