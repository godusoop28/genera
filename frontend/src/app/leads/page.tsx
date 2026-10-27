"use client";

import { AdvisorChip, LoadError, PropertyMini, Segmented, SourceLabel, StageBadge, StatCard, useApi } from "@/components/crm/CrmUi";
import { LeadBoard } from "@/components/crm/LeadBoard";
import { sourceGroups, useLeadSources } from "@/components/crm/LeadForms";
import { NewLeadModal } from "@/components/crm/NewLeadModal";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { SearchInput } from "@/components/ui/SearchInput";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { listAdvisors, listLeads, type LeadGroup, type LeadItem, type LeadTemperature } from "@/lib/api/crm";
import { formatInstantMx, interestLabels, taskTypeEmoji, taskTypeLabels, temperatureDots, temperatureLabels, timeAgo, whatsappLink } from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { ArrowRight, CheckCircle2, Columns3, Inbox, List, MessageCircle, Plus, RefreshCw, UserPlus, Users } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

const groupLabels: Record<LeadGroup, string> = {
  ALL: "Todos",
  UNASSIGNED: "Sin asignar",
  PENDING: "Pendientes por horario",
  NEW: "Nuevos",
  OPEN: "En seguimiento",
  STALE: "Sin actividad reciente",
  WON: "Cierres",
  LOST: "Perdidos",
};

function initialGroup(value: string | null): LeadGroup {
  return value && value in groupLabels ? (value as LeadGroup) : "ALL";
}

export default function LeadsPage() {
  // useSearchParams necesita un límite de Suspense para el prerender.
  return (
    <Suspense fallback={null}>
      <LeadsScreen />
    </Suspense>
  );
}

function LeadsScreen() {
  const params = useSearchParams();
  const can = useCan();
  const seesAll = can("LEAD_VIEW_ALL");
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [group, setGroup] = useState<LeadGroup>(() => initialGroup(params.get("grupo")));
  const [advisorId, setAdvisorId] = useState(params.get("asesor") ?? "");
  // ?fuente=<código del catálogo> (CC#29): viene del dashboard de fuentes.
  const [sourceCode, setSourceCode] = useState(params.get("fuente") ?? "");
  const [temperature, setTemperature] = useState<LeadTemperature | "">("");
  const sources = useLeadSources();
  // ?propiedad=<núm. de 21Online>: viene de "Leads de esta propiedad".
  const [propertyId, setPropertyId] = useState<number | undefined>(() => Number(params.get("propiedad")) || undefined);
  const [view, setView] = useState<"list" | "board">("list");
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(query.trim()), 300);
    return () => window.clearTimeout(t);
  }, [query]);

  const leads = useApi(
    () => listLeads({ q: debounced, group: view === "board" ? "ALL" : group, advisorId, sourceCode, temperature, propertyId, size: view === "board" ? 200 : 100 }),
    [debounced, group, advisorId, sourceCode, temperature, view, propertyId],
  );
  const advisors = useApi(() => (seesAll ? listAdvisors() : Promise.resolve([])), [seesAll]);
  const counts = leads.data?.counts;
  const filtering = debounced !== "" || advisorId !== "" || sourceCode !== "" || temperature !== "" || group !== "ALL" || propertyId !== undefined;

  return (
    <PageContainer
      title="Leads"
      subtitle={seesAll ? "Prospectos que llegan por WhatsApp, portales, campañas y la oficina, con su propiedad y su asesor." : "Tus prospectos asignados."}
      action={
        <>
          <Button variant="secondary" onClick={leads.reload} aria-label="Actualizar la lista" title="Actualizar la lista">
            <RefreshCw className="h-4 w-4" aria-hidden />
          </Button>
          <div className="flex shrink-0 gap-1 rounded-xl border border-border bg-card p-1" role="group" aria-label="Vista">
            <ViewButton active={view === "list"} onClick={() => setView("list")} label="Ver como lista" icon={List} />
            <ViewButton active={view === "board"} onClick={() => setView("board")} label="Ver como tablero por etapa" icon={Columns3} />
          </div>
          {can("LEAD_MANAGE") ? (
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" aria-hidden /> Registrar lead
            </Button>
          ) : null}
        </>
      }
    >
      <section aria-label="Resumen" className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <StatCard icon={Users} value={counts?.ALL ?? "—"} label="Leads" hint={filtering ? "Con los filtros actuales" : "Todos los registrados"} />
        <StatCard icon={Inbox} tone="warning" value={counts?.NEW ?? "—"} label="Nuevos" hint="Aún sin primer contacto" />
        <StatCard icon={UserPlus} tone="danger" value={counts ? counts.UNASSIGNED + counts.PENDING : "—"} label="Sin asesor" hint={counts ? `${counts.PENDING} esperan turno` : "Necesitan un asesor"} />
        <StatCard icon={CheckCircle2} tone="success" value={counts?.WON ?? "—"} label="Cierres" hint={counts ? `${counts.STALE} sin actividad reciente` : "Transacciones concretadas"} />
      </section>

      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end">
        <SearchInput value={query} onChange={setQuery} label="Buscar por nombre, teléfono o propiedad" className="lg:max-w-md lg:flex-1" />
        {seesAll ? (
          <Select
            aria-label="Filtrar por asesor"
            value={advisorId}
            onChange={(e) => setAdvisorId(e.target.value)}
            options={[{ value: "", label: "Todos los asesores" }, ...(advisors.data ?? []).map((a) => ({ value: a.userId, label: a.name }))]}
            containerClassName="lg:w-64"
            className="h-11"
          />
        ) : null}
        <Select
          aria-label="Filtrar por fuente"
          value={sourceCode}
          onChange={(e) => setSourceCode(e.target.value)}
          options={[{ value: "", label: "Todas las fuentes" }]}
          groups={sources ? sourceGroups(sources, sourceCode, true) : []}
          containerClassName="lg:w-56"
          className="h-11"
        />
        <Select
          aria-label="Filtrar por temperatura"
          value={temperature}
          onChange={(e) => setTemperature(e.target.value as LeadTemperature | "")}
          options={[{ value: "", label: "Cualquier temperatura" }, ...(Object.keys(temperatureLabels) as LeadTemperature[]).map((t) => ({ value: t, label: `${temperatureDots[t]} ${temperatureLabels[t]}` }))]}
          containerClassName="lg:w-48"
          className="h-11"
        />
      </div>
      {propertyId !== undefined ? (
        <p className="-mt-1 mb-4 flex flex-wrap items-center gap-2 text-sm text-muted">
          Solo los que preguntaron por la propiedad {propertyId} de 21Online.
          <button type="button" className="font-medium text-dark-gold underline hover:text-obsessed" onClick={() => setPropertyId(undefined)}>
            Ver todos
          </button>
        </p>
      ) : null}

      {view === "list" ? (
        <div className="mb-5">
          <Segmented
            label="Filtrar por estado"
            value={group}
            onChange={setGroup}
            options={(Object.keys(groupLabels) as LeadGroup[]).map((id) => ({ id, label: groupLabels[id], count: counts?.[id] }))}
          />
        </div>
      ) : null}

      {leads.error && !leads.data ? (
        <LoadError message={leads.error} onRetry={leads.reload} />
      ) : !leads.data ? (
        <StateMessage kind="loading" title="Cargando leads…" />
      ) : leads.data.items.length === 0 ? (
        <StateMessage
          kind={filtering ? "no-results" : "empty"}
          title={filtering ? "Sin resultados" : "Aún no hay leads"}
          description={filtering ? "Ningún lead coincide con estos filtros." : "Cuando el asistente de WhatsApp canalice a un cliente, o registres uno a mano, aparecerá aquí."}
          action={
            filtering ? (
              <Button
                variant="secondary"
                onClick={() => {
                  setQuery("");
                  setAdvisorId("");
                  setSourceCode("");
                  setTemperature("");
                  setGroup("ALL");
                  setPropertyId(undefined);
                }}
              >
                Quitar filtros
              </Button>
            ) : null
          }
        />
      ) : view === "list" ? (
        <>
          <LeadTable leads={leads.data.items} />
          {leads.data.total > leads.data.items.length ? (
            <p className="mt-4 text-center text-xs text-muted">
              Mostrando {leads.data.items.length} de {leads.data.total}. Usa la búsqueda o los filtros para encontrar el resto.
            </p>
          ) : null}
        </>
      ) : (
        <LeadBoard
          leads={leads.data.items}
          canMove={can("LEAD_MANAGE")}
          onMoved={(moved) => leads.setData((page) => (page ? { ...page, items: page.items.map((l) => (l.id === moved.id ? moved : l)) } : page))}
        />
      )}

      {creating ? <NewLeadModal open={creating} onClose={() => setCreating(false)} advisors={advisors.data ?? []} /> : null}
    </PageContainer>
  );
}

function LeadTable({ leads }: { leads: LeadItem[] }) {
  const cols = "lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1.6fr)_minmax(0,1.3fr)_minmax(0,0.9fr)_minmax(0,1.1fr)_auto]";
  return (
    <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
      <div className={cn("hidden gap-4 border-b border-border bg-app-bg px-5 py-3 text-xs font-medium uppercase tracking-wide text-muted lg:grid", cols)}>
        <span>Lead</span>
        <span>Propiedad</span>
        <span>Asesor</span>
        <span>Etapa y siguiente actividad</span>
        <span>Fuente</span>
        <span className="sr-only">Acción</span>
      </div>
      <ul className="divide-y divide-border">
        {leads.map((lead) => (
          <li key={lead.id} className={cn("grid grid-cols-1 gap-3 px-4 py-4 sm:px-5 lg:items-center lg:gap-4", cols)}>
            <div className="min-w-0">
              <p className="truncate font-semibold text-obsessed">
                {lead.temperature ? <span title={temperatureLabels[lead.temperature]}>{temperatureDots[lead.temperature]} </span> : null}
                {lead.name}
              </p>
              <p className="truncate text-xs text-muted">
                {interestLabels[lead.interest]} · {timeAgo(lead.createdAt)}
              </p>
            </div>
            <PropertyMini property={lead.property} />
            <div className="min-w-0 space-y-1">
              <AdvisorChip advisor={lead.advisor} emptyLabel={lead.assignmentStatus === "PENDING_SCHEDULE" ? "Pendiente por horario" : "Sin asignar"} />
              {lead.assignmentStatus === "PENDING_OWNER" ? <Badge tone="info">Espera su turno</Badge> : null}
              {lead.coordinationNote ? <Badge tone="warning">Coordinar</Badge> : null}
            </div>
            <div className="min-w-0 space-y-1">
              <StageBadge stage={lead.stage} />
              {lead.nextTask ? (
                <p className={cn("truncate text-xs", lead.nextTask.overdue ? "font-medium text-danger-text" : "text-muted")} title={taskTypeLabels[lead.nextTask.type]}>
                  {taskTypeEmoji[lead.nextTask.type]} {lead.nextTask.overdue ? "Vencida · " : ""}
                  {formatInstantMx(lead.nextTask.scheduledAt)}
                </p>
              ) : lead.stale ? (
                <p className="text-xs font-medium text-warning-text">Sin actividad reciente</p>
              ) : null}
            </div>
            <SourceLabel source={lead.source} label={lead.sourceLabel} detail={lead.sourceDetail} />
            <div className="flex items-center gap-1 justify-self-start lg:justify-self-end">
              {lead.phone ? (
                <a
                  href={whatsappLink(lead.phone)}
                  target="_blank"
                  rel="noreferrer"
                  aria-label={`WhatsApp a ${lead.name}`}
                  title="WhatsApp"
                  className="flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-app-bg hover:text-success-text focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
                >
                  <MessageCircle className="h-4 w-4" aria-hidden />
                </a>
              ) : null}
              <Link
                href={`/leads/${lead.id}`}
                className="inline-flex min-h-9 items-center gap-1 rounded-lg text-sm font-medium text-dark-gold hover:text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
                aria-label={`Ver lead de ${lead.name}`}
              >
                Ver <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ViewButton({ active, onClick, label, icon: Icon }: { active: boolean; onClick: () => void; label: string; icon: typeof List }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      aria-label={label}
      title={label}
      className={cn(
        "flex h-9 w-9 items-center justify-center rounded-lg transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
        active ? "bg-gold text-brand-ink" : "text-muted hover:bg-app-bg hover:text-obsessed",
      )}
    >
      <Icon className="h-4 w-4" aria-hidden />
    </button>
  );
}
