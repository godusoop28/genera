"use client";

import { useCanSeeCrm } from "@/components/crm/CrmUi";
import { ClosedBadge, formatClosingDate } from "@/components/expediente/ClosedBadge";
import { PropertyKeyModal } from "@/components/expediente/PropertyKeyField";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { archiveExpediente, getExpediente, getParticipants, unarchiveExpediente } from "@/lib/api/expedientes";
import { backendStatusLabels, backendStatusNextStep, backendStatusTone } from "@/lib/api/status-labels";
import type { ExpedienteResponse, ParticipantResponse, SigningLinkResponse } from "@/lib/api/types";
import { readDraft, writeDraft } from "@/lib/drafts";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { Archive, ArchiveRestore, ArrowRight, Flag, KeyRound, RefreshCw } from "lucide-react";
import { use, useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { ConsistencyTab } from "./ConsistencyTab";
import { ContractDataTab } from "./ContractDataTab";
import { ContractTab } from "./ContractTab";
import { DocumentsPanel } from "./DocumentsPanel";
import { FeasibilityTab } from "./FeasibilityTab";
import { FollowUpTab } from "./FollowUpTab";
import { HistoryTab } from "./HistoryTab";
import { LeadsTab } from "./LeadsTab";
import { ReceiptsTab } from "./ReceiptsTab";
import { SurveysTab } from "./SurveysTab";
import { ParticipantsTab } from "./ParticipantsTab";
import { SummaryTab } from "./SummaryTab";

const tabs = [
  { id: "resumen", label: "Resumen" },
  { id: "participantes", label: "Participantes" },
  { id: "documentos", label: "Documentos" },
  { id: "consistencia", label: "Consistencia" },
  { id: "factibilidad", label: "Factibilidad IA" },
  { id: "datos-contrato", label: "Datos del contrato" },
  { id: "contrato", label: "Contrato y firmas" },
  { id: "seguimiento", label: "Seguimiento" },
  { id: "historial", label: "Bitácora" },
  // Control de Cambios #31: acuse de recepción firmado por el representante legal.
  { id: "acuse", label: "Acuse de recepción" },
  // Control de Cambios #28: encuestas NPS del cliente.
  { id: "satisfaccion", label: "Satisfacción" },
  // Control de Cambios #30: solo para quien entra al CRM.
  { id: "leads", label: "Leads" },
] as const;

type TabId = (typeof tabs)[number]["id"];

export interface ExpedienteContext {
  expediente: ExpedienteResponse;
  participants: ParticipantResponse[];
  /** Vuelve a cargar el expediente (estatus, participantes) después de una acción. */
  reload: () => Promise<void>;
  /**
   * Ligas recién generadas (solo se muestran una vez). Viven aquí y no en cada
   * pestaña porque reload() vuelve a montar la pestaña y se perderían.
   */
  freshPublicLink: string | null;
  setFreshPublicLink: (url: string | null) => void;
  freshSigningLinks: SigningLinkResponse[];
  setFreshSigningLinks: Dispatch<SetStateAction<SigningLinkResponse[]>>;
}

export default function ExpedienteDetailPage({ params }: PageProps<"/expedientes/[id]">) {
  const { id } = use(params);
  // Al recargar se vuelve a la pestaña en la que se estaba trabajando. (Seguro
  // de leer en el estado inicial: las pestañas no se pintan hasta cargar el expediente.)
  const tabKey = `expediente:${id}:tab`;
  const [tab, setTabState] = useState<TabId>(() => {
    const saved = readDraft<TabId>(tabKey);
    return saved && tabs.some((t) => t.id === saved) ? saved : "resumen";
  });
  const setTab = (next: TabId) => {
    setTabState(next);
    writeDraft(tabKey, next);
  };
  const { showToast } = useToast();
  const can = useCan();
  const canSeeCrm = useCanSeeCrm();
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [keyOpen, setKeyOpen] = useState(false);
  const [unarchiving, setUnarchiving] = useState(false);
  const [expediente, setExpediente] = useState<ExpedienteResponse | null>(null);
  const [participants, setParticipants] = useState<ParticipantResponse[]>([]);
  const [notFound, setNotFound] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [version, setVersion] = useState(0);
  const loadedOnce = useRef(false);
  const [freshPublicLink, setFreshPublicLink] = useState<string | null>(null);
  const [freshSigningLinks, setFreshSigningLinks] = useState<SigningLinkResponse[]>([]);

  const reload = useCallback(
    () =>
      Promise.all([getExpediente(id), getParticipants(id)])
        .then(([e, p]) => {
          loadedOnce.current = true;
          setExpediente(e);
          setParticipants(p);
          setLoadError(null);
          setVersion((v) => v + 1);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.status === 404) {
            setNotFound(true);
          } else if (loadedOnce.current) {
            // Ya hay datos en pantalla: un error temporal de red no debe sacar al usuario ni perder lo que capturaba.
            showToast("No se pudo actualizar la información (revisa tu conexión). Lo que tienes en pantalla se conserva.");
          } else {
            setLoadError("No se pudo cargar el expediente. Revisa tu conexión e intenta de nuevo.");
          }
        }),
    [id, showToast],
  );

  useEffect(() => {
    reload();
  }, [reload]);

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await reload();
      showToast("Información actualizada.");
    } finally {
      setRefreshing(false);
    }
  };

  const archive = async (reason: string) => {
    try {
      await archiveExpediente(id, reason);
      setArchiveOpen(false);
      showToast("Expediente archivado. Ya no aparece en el listado de trabajo.");
      await reload();
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "No se pudo archivar el expediente.");
    }
  };

  const unarchive = async () => {
    setUnarchiving(true);
    try {
      await unarchiveExpediente(id);
      showToast("Expediente desarchivado. Vuelve a aparecer en el listado.");
      await reload();
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "No se pudo desarchivar el expediente.");
    } finally {
      setUnarchiving(false);
    }
  };

  if (notFound) {
    return (
      <PageContainer title="Expediente no encontrado">
        <p className="text-sm text-muted">Este expediente no existe o no tienes permiso para consultarlo.</p>
      </PageContainer>
    );
  }
  if (loadError && !expediente) {
    return (
      <PageContainer title="Error">
        <p className="text-sm text-danger-text">{loadError}</p>
        <Button
          variant="secondary"
          size="sm"
          className="mt-3"
          onClick={() => {
            setLoadError(null);
            void reload();
          }}
        >
          <RefreshCw className="h-4 w-4" aria-hidden /> Reintentar
        </Button>
      </PageContainer>
    );
  }
  if (!expediente) {
    return (
      <PageContainer title="Cargando…">
        <p className="text-sm text-muted">Cargando expediente…</p>
      </PageContainer>
    );
  }

  const next = backendStatusNextStep[expediente.status];
  const context: ExpedienteContext = {
    expediente,
    participants,
    reload,
    freshPublicLink,
    setFreshPublicLink,
    freshSigningLinks,
    setFreshSigningLinks,
  };

  return (
    <PageContainer title={expediente.ownerDisplayName} subtitle={expediente.propertyAddress ?? undefined}>
      <Card className="mb-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <span className="rounded-lg bg-app-bg px-2.5 py-1 font-mono text-xs font-medium tracking-wide text-obsessed/70">
              {expediente.folio}
            </span>
            {/* Control de Cambios #27: clave estructurada del inmueble. */}
            {expediente.propertyKey ? (
              <span
                className="inline-flex items-center gap-1.5 rounded-lg border border-gold/50 bg-gold/10 px-2.5 py-1 font-mono text-xs font-semibold tracking-wide text-obsessed"
                title="Clave del inmueble"
              >
                <KeyRound className="h-3.5 w-3.5 text-dark-gold" aria-hidden />
                {expediente.propertyKey}
              </span>
            ) : can("EXPEDIENT_KEY_ASSIGN") ? (
              <Badge tone="warning">Sin clave</Badge>
            ) : null}
            <Badge tone={backendStatusTone[expediente.status]}>{backendStatusLabels[expediente.status]}</Badge>
            {!expediente.correctable ? <Badge tone="neutral">Datos bloqueados (contrato firmado o expediente decidido)</Badge> : null}
            {expediente.archivedAt ? <Badge tone="neutral">Archivado</Badge> : null}
            {expediente.closingCompletedOn ? <ClosedBadge closedOn={expediente.closingCompletedOn} withDate={false} /> : null}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {can("EXPEDIENT_KEY_ASSIGN") ? (
              <Button variant="secondary" size="sm" onClick={() => setKeyOpen(true)}>
                <KeyRound className="h-4 w-4" aria-hidden />
                {expediente.propertyKey ? "Corregir clave" : "Asignar clave"}
              </Button>
            ) : null}
            {can("EXPEDIENT_ARCHIVE") ? (
              expediente.archivedAt ? (
                <Button variant="secondary" size="sm" onClick={unarchive} disabled={unarchiving}>
                  <ArchiveRestore className="h-4 w-4" aria-hidden />
                  {unarchiving ? "Desarchivando…" : "Desarchivar"}
                </Button>
              ) : (
                <Button variant="secondary" size="sm" onClick={() => setArchiveOpen(true)}>
                  <Archive className="h-4 w-4" aria-hidden />
                  Archivar
                </Button>
              )
            ) : null}
            <Button variant="secondary" size="sm" onClick={handleRefresh} disabled={refreshing}>
              <RefreshCw className={cn("h-4 w-4", refreshing && "animate-spin")} aria-hidden />
              {refreshing ? "Actualizando…" : "Actualizar"}
            </Button>
          </div>
        </div>
        {expediente.archivedAt ? (
          <p className="mt-4 flex items-start gap-2 rounded-xl border border-border bg-app-bg p-3 text-sm text-muted">
            <Archive className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <span>
              Archivado el {new Intl.DateTimeFormat("es-MX", { dateStyle: "long" }).format(new Date(expediente.archivedAt))}
              {expediente.archiveReason ? `. Motivo: ${expediente.archiveReason}` : ""}. No aparece en el listado de trabajo ni en los totales y la liga
              del cliente quedó revocada.
            </span>
          </p>
        ) : null}
        {/* CC#32: la operación ya tiene el seguimiento de cierre completado. */}
        {expediente.closingCompletedOn ? (
          <p className="mt-4 flex items-start gap-2 rounded-xl border border-success-text/30 bg-success-bg p-3 text-sm text-success-text">
            <Flag className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <span>
              <strong>Operación cerrada</strong> el {formatClosingDate(expediente.closingCompletedOn, "long")} · Asesor responsable:{" "}
              {expediente.assignedAdvisorName ?? "sin asesor asignado"}. El historial y los datos anteriores se conservan en la pestaña Seguimiento y la Bitácora.
            </span>
          </p>
        ) : null}
        <div className="mt-4 grid gap-2 rounded-xl bg-app-bg p-3 text-sm sm:grid-cols-3">
          <p className="text-muted">
            <span className="block text-xs font-medium uppercase tracking-wide">Qué significa</span>
            <span className="text-obsessed">{next.meaning}</span>
          </p>
          <p className="text-muted">
            <span className="block text-xs font-medium uppercase tracking-wide">Siguiente paso</span>
            <span className="inline-flex items-start gap-1 text-obsessed">
              <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
              {next.next}
            </span>
          </p>
          <p className="text-muted">
            <span className="block text-xs font-medium uppercase tracking-wide">Quién lo hace</span>
            <span className="text-obsessed">{next.who}</span>
          </p>
        </div>
      </Card>

      <div className="mb-6 flex gap-1 overflow-x-auto border-b border-border">
        {tabs.filter((t) => t.id !== "leads" || canSeeCrm).map((t) => (
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

      <ReasonModal
        open={archiveOpen}
        title={`Archivar ${expediente.folio}`}
        description="El expediente sale del listado de trabajo y de los totales, sin cambiar su estatus ni borrar documentos. La liga del cliente se revoca. Puedes desarchivarlo cuando quieras desde el filtro “Archivados”."
        label="Motivo (opcional)"
        placeholder="Ej. el propietario desistió de la venta"
        confirmLabel="Archivar"
        onCancel={() => setArchiveOpen(false)}
        onConfirm={archive}
      />

      {keyOpen ? (
        <PropertyKeyModal
          expediente={expediente}
          onClose={() => setKeyOpen(false)}
          onSaved={async (updated) => {
            setKeyOpen(false);
            showToast(`Clave ${updated.propertyKey} asignada.`);
            await reload();
          }}
        />
      ) : null}

      <div key={version}>
        {tab === "resumen" ? <SummaryTab {...context} /> : null}
        {tab === "participantes" ? <ParticipantsTab {...context} /> : null}
        {tab === "documentos" ? <DocumentsPanel {...context} /> : null}
        {tab === "consistencia" ? <ConsistencyTab {...context} /> : null}
        {tab === "factibilidad" ? <FeasibilityTab {...context} /> : null}
        {tab === "datos-contrato" ? <ContractDataTab {...context} /> : null}
        {tab === "contrato" ? <ContractTab {...context} /> : null}
        {tab === "seguimiento" ? <FollowUpTab {...context} /> : null}
        {tab === "historial" ? <HistoryTab {...context} /> : null}
        {tab === "acuse" ? <ReceiptsTab {...context} /> : null}
        {tab === "satisfaccion" ? <SurveysTab {...context} /> : null}
        {tab === "leads" && canSeeCrm ? <LeadsTab {...context} /> : null}
      </div>
    </PageContainer>
  );
}
