"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { getExpediente, getParticipants } from "@/lib/api/expedientes";
import { backendStatusLabels, backendStatusNextStep, backendStatusTone } from "@/lib/api/status-labels";
import type { ExpedienteResponse, ParticipantResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { ArrowRight, RefreshCw } from "lucide-react";
import { use, useCallback, useEffect, useState } from "react";
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
}

export default function ExpedienteDetailPage({ params }: PageProps<"/expedientes/[id]">) {
  const { id } = use(params);
  const [tab, setTab] = useState<TabId>("resumen");
  const { showToast } = useToast();
  const [expediente, setExpediente] = useState<ExpedienteResponse | null>(null);
  const [participants, setParticipants] = useState<ParticipantResponse[]>([]);
  const [notFound, setNotFound] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [version, setVersion] = useState(0);

  const reload = useCallback(
    () =>
      Promise.all([getExpediente(id), getParticipants(id)])
        .then(([e, p]) => {
          setExpediente(e);
          setParticipants(p);
          setLoadError(null);
          setVersion((v) => v + 1);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.status === 404) {
            setNotFound(true);
          } else {
            setLoadError("No se pudo cargar el expediente. Revisa tu conexión e intenta de nuevo.");
          }
        }),
    [id],
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
        <p className="text-sm text-muted">Cargando expediente…</p>
      </PageContainer>
    );
  }

  const next = backendStatusNextStep[expediente.status];
  const context: ExpedienteContext = { expediente, participants, reload };

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
