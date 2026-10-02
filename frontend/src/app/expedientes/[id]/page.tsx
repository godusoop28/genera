"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { getExpediente, getParticipants } from "@/lib/api/expedientes";
import { backendStatusLabels, backendStatusNextStep, backendStatusTone } from "@/lib/api/status-labels";
import type { ExpedienteResponse, ParticipantResponse, SigningLinkResponse } from "@/lib/api/types";
import { readDraft, writeDraft } from "@/lib/drafts";
import { cn } from "@/lib/utils";
import { ArrowRight, RefreshCw } from "lucide-react";
import { use, useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { ConsistencyTab } from "./ConsistencyTab";
import { ContractDataTab } from "./ContractDataTab";
import { ContractTab } from "./ContractTab";
import { DocumentsPanel } from "./DocumentsPanel";
import { FollowUpTab } from "./FollowUpTab";
import { HistoryTab } from "./HistoryTab";
import { ParticipantsTab } from "./ParticipantsTab";
import { SummaryTab } from "./SummaryTab";

const tabs = [
  { id: "resumen", label: "Resumen" },
  { id: "participantes", label: "Participantes" },
  { id: "documentos", label: "Documentos" },
  { id: "consistencia", label: "Consistencia" },
  { id: "datos-contrato", label: "Datos del contrato" },
  { id: "contrato", label: "Contrato y firmas" },
  { id: "seguimiento", label: "Seguimiento" },
  { id: "historial", label: "Bitácora" },
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
            <Badge tone={backendStatusTone[expediente.status]}>{backendStatusLabels[expediente.status]}</Badge>
            {!expediente.correctable ? <Badge tone="neutral">Datos bloqueados (contrato firmado o expediente decidido)</Badge> : null}
          </div>
          <Button variant="secondary" size="sm" onClick={handleRefresh} disabled={refreshing}>
            <RefreshCw className={cn("h-4 w-4", refreshing && "animate-spin")} aria-hidden />
            {refreshing ? "Actualizando…" : "Actualizar"}
          </Button>
        </div>
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

      <div key={version}>
        {tab === "resumen" ? <SummaryTab {...context} /> : null}
        {tab === "participantes" ? <ParticipantsTab {...context} /> : null}
        {tab === "documentos" ? <DocumentsPanel {...context} /> : null}
        {tab === "consistencia" ? <ConsistencyTab {...context} /> : null}
        {tab === "datos-contrato" ? <ContractDataTab {...context} /> : null}
        {tab === "contrato" ? <ContractTab {...context} /> : null}
        {tab === "seguimiento" ? <FollowUpTab {...context} /> : null}
        {tab === "historial" ? <HistoryTab {...context} /> : null}
      </div>
    </PageContainer>
  );
}
