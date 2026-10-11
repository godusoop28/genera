"use client";

import { AdvisorChip, PreviewNotice, PropertyMini, Segmented, SourceLabel, StageBadge, StatCard } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { SearchInput } from "@/components/ui/SearchInput";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { interestLabels, openStages, sourceLabels, stageLabels, stageOrder, timeAgo } from "@/lib/crm/labels";
import { useCrm, type CrmState } from "@/lib/crm/store";
import { cn } from "@/lib/utils";
import type { CrmLead, LeadSource } from "@/types/crm";
import { ArrowRight, CheckCircle2, Columns3, Inbox, List, UserPlus, Users } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";

type Group = "all" | "unassigned" | "new" | "open" | "won" | "lost";

const groupMatchers: Record<Group, (l: CrmLead) => boolean> = {
  all: () => true,
  unassigned: (l) => !l.advisorId,
  new: (l) => l.stage === "NEW",
  open: (l) => openStages.includes(l.stage),
  won: (l) => l.stage === "WON",
  lost: (l) => l.stage === "LOST",
};

const groupLabels: Record<Group, string> = {
  all: "Todos",
  unassigned: "Sin asignar",
  new: "Nuevos",
  open: "En seguimiento",
  won: "Ganados",
  lost: "Perdidos",
};

function initialGroup(value: string | null): Group {
  return value && value in groupMatchers ? (value as Group) : "all";
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
  const crm = useCrm();
  const params = useSearchParams();
  const [query, setQuery] = useState("");
  const [group, setGroup] = useState<Group>(() => initialGroup(params.get("grupo")));
  const [advisorId, setAdvisorId] = useState(params.get("asesor") ?? "");
  const [source, setSource] = useState<LeadSource | "">((params.get("fuente") as LeadSource | null) ?? "");
  const [view, setView] = useState<"list" | "board">("list");

  return (
    <PageContainer
      title="Leads"
      subtitle="Prospectos que llegan por WhatsApp, portales, campañas y la oficina, con su propiedad y su asesor."
      action={
        <div className="flex shrink-0 gap-1 rounded-xl border border-border bg-card p-1" role="group" aria-label="Vista">
          <ViewButton active={view === "list"} onClick={() => setView("list")} label="Ver como lista" icon={List} />
          <ViewButton active={view === "board"} onClick={() => setView("board")} label="Ver como tablero por etapa" icon={Columns3} />
        </div>
      }
    >
      <PreviewNotice />
      {!crm ? (
        <StateMessage kind="loading" title="Cargando leads…" />
      ) : (
        <LeadsContent
          crm={crm}
          query={query}
          setQuery={setQuery}
          group={group}
          setGroup={setGroup}
          advisorId={advisorId}
          setAdvisorId={setAdvisorId}
          source={source}
          setSource={setSource}
          view={view}
        />
      )}
    </PageContainer>
  );
}

function LeadsContent({
  crm,
  query,
  setQuery,
  group,
  setGroup,
  advisorId,
  setAdvisorId,
  source,
  setSource,
  view,
}: {
  crm: CrmState;
  query: string;
  setQuery: (v: string) => void;
  group: Group;
  setGroup: (g: Group) => void;
  advisorId: string;
  setAdvisorId: (v: string) => void;
  source: LeadSource | "";
  setSource: (v: LeadSource | "") => void;
  view: "list" | "board";
}) {
  const propertyById = useMemo(() => new Map(crm.properties.map((p) => [p.id, p])), [crm.properties]);
  const advisorById = useMemo(() => new Map(crm.advisors.map((a) => [a.id, a])), [crm.advisors]);

  // Filtros que no son de grupo: los conteos de las pestañas respetan búsqueda, asesor y fuente.
  const base = useMemo(() => {
    const q = query.trim().toLowerCase();
    return crm.leads
      .filter((l) => !advisorId || l.advisorId === advisorId)
      .filter((l) => !source || l.source === source)
      .filter((l) => {
        if (!q) return true;
        const p = l.propertyId ? propertyById.get(l.propertyId) : undefined;
        return [l.name, l.phone, l.summary, p?.internalKey, p?.key, p?.neighborhood].some((v) => v?.toLowerCase().includes(q));
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [crm.leads, query, advisorId, source, propertyById]);

  const visible = base.filter(groupMatchers[group]);
  const filtering = query.trim() !== "" || advisorId !== "" || source !== "" || group !== "all";
  const total = crm.leads.length;
  const unassigned = crm.leads.filter(groupMatchers.unassigned).length;
  const fresh = crm.leads.filter(groupMatchers.new).length;
  const won = crm.leads.filter(groupMatchers.won).length;

  return (
    <>
      <section aria-label="Resumen" className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <StatCard icon={Users} value={total} label="Leads" hint="Todos los registrados" />
        <StatCard icon={Inbox} tone="warning" value={fresh} label="Nuevos" hint="Aún sin primer contacto" />
        <StatCard icon={UserPlus} tone="danger" value={unassigned} label="Sin asignar" hint="Necesitan un asesor" />
        <StatCard icon={CheckCircle2} tone="success" value={won} label="Ganados" hint="Cerraron operación" />
      </section>

      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end">
        <SearchInput value={query} onChange={setQuery} label="Buscar por nombre, teléfono o clave de propiedad" className="lg:max-w-md lg:flex-1" />
        <Select
          aria-label="Filtrar por asesor"
          value={advisorId}
          onChange={(e) => setAdvisorId(e.target.value)}
          options={[{ value: "", label: "Todos los asesores" }, ...crm.advisors.map((a) => ({ value: a.id, label: a.name }))]}
          containerClassName="lg:w-64"
          className="h-11"
        />
        <Select
          aria-label="Filtrar por fuente"
          value={source}
          onChange={(e) => setSource(e.target.value as LeadSource | "")}
          options={[{ value: "", label: "Todas las fuentes" }, ...Object.entries(sourceLabels).map(([value, label]) => ({ value, label }))]}
          containerClassName="lg:w-56"
          className="h-11"
        />
      </div>

      {view === "list" ? (
        <div className="mb-5">
          <Segmented
            label="Filtrar por estado"
            value={group}
            onChange={setGroup}
            options={(Object.keys(groupLabels) as Group[]).map((id) => ({ id, label: groupLabels[id], count: base.filter(groupMatchers[id]).length }))}
          />
        </div>
      ) : null}

      {(view === "list" ? visible : base).length === 0 ? (
        <StateMessage
          kind={filtering ? "no-results" : "empty"}
          title={filtering ? "Sin resultados" : "Aún no hay leads"}
          description={filtering ? "Ningún lead coincide con estos filtros." : "Cuando el bot de WhatsApp o un portal registre un prospecto aparecerá aquí."}
          action={
            filtering ? (
              <Button
                variant="secondary"
                onClick={() => {
                  setQuery("");
                  setAdvisorId("");
                  setSource("");
                  setGroup("all");
                }}
              >
                Quitar filtros
              </Button>
            ) : null
          }
        />
      ) : view === "list" ? (
        <LeadTable leads={visible} propertyById={propertyById} advisorById={advisorById} />
      ) : (
        <LeadBoard leads={base} advisorById={advisorById} propertyById={propertyById} />
      )}
    </>
  );
}

type Maps = {
  propertyById: Map<number, CrmState["properties"][number]>;
  advisorById: Map<string, CrmState["advisors"][number]>;
};

function LeadTable({ leads, propertyById, advisorById }: { leads: CrmLead[] } & Maps) {
  const cols = "lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1.6fr)_minmax(0,1.3fr)_minmax(0,0.9fr)_minmax(0,1.1fr)_auto]";
  return (
    <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
      <div className={cn("hidden gap-4 border-b border-border bg-app-bg px-5 py-3 text-xs font-medium uppercase tracking-wide text-muted lg:grid", cols)}>
        <span>Lead</span>
        <span>Propiedad</span>
        <span>Asesor</span>
        <span>Etapa</span>
        <span>Fuente</span>
        <span className="sr-only">Acción</span>
      </div>
      <ul className="divide-y divide-border">
        {leads.map((lead) => (
          <li key={lead.id} className={cn("grid grid-cols-1 gap-3 px-4 py-4 sm:px-5 lg:items-center lg:gap-4", cols)}>
            <div className="min-w-0">
              <p className="truncate font-semibold text-obsessed">{lead.name}</p>
              <p className="truncate text-xs text-muted">
                {interestLabels[lead.interest]} · {timeAgo(lead.createdAt)}
              </p>
            </div>
            <PropertyMini property={lead.propertyId ? propertyById.get(lead.propertyId) : undefined} />
            <AdvisorChip advisor={lead.advisorId ? advisorById.get(lead.advisorId) : undefined} />
            <div>
              <StageBadge stage={lead.stage} />
            </div>
            <SourceLabel source={lead.source} detail={lead.sourceDetail} />
            <Link
              href={`/leads/${lead.id}`}
              className="inline-flex min-h-9 items-center gap-1 justify-self-start rounded-lg text-sm font-medium text-dark-gold hover:text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 lg:justify-self-end"
              aria-label={`Ver lead de ${lead.name}`}
            >
              Ver <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Tablero por etapa (solo lectura: el cambio de etapa se hace en el detalle del lead). */
function LeadBoard({ leads, advisorById, propertyById }: { leads: CrmLead[] } & Maps) {
  return (
    <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
      <div className="grid min-w-[1100px] grid-cols-7 gap-3">
        {stageOrder.map((stage) => {
          const items = leads.filter((l) => l.stage === stage);
          return (
            <section key={stage} aria-label={stageLabels[stage]} className="flex min-w-0 flex-col rounded-xl border border-border bg-app-bg/60">
              <header className="flex items-center justify-between gap-2 border-b border-border px-3 py-2.5">
                <StageBadge stage={stage} />
                <span className="text-xs tabular-nums text-muted">{items.length}</span>
              </header>
              <ul className="flex flex-col gap-2 p-2">
                {items.map((lead) => {
                  const property = lead.propertyId ? propertyById.get(lead.propertyId) : undefined;
                  const advisor = lead.advisorId ? advisorById.get(lead.advisorId) : undefined;
                  return (
                    <li key={lead.id}>
                      <Link
                        href={`/leads/${lead.id}`}
                        className="block rounded-lg border border-border bg-card p-3 text-left shadow-[0_1px_2px_rgba(36,39,35,0.04)] transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
                      >
                        <p className="truncate text-sm font-semibold text-obsessed">{lead.name}</p>
                        <p className="mt-0.5 truncate text-xs text-muted">{property ? `${property.internalKey} · ${property.neighborhood}` : interestLabels[lead.interest]}</p>
                        <p className={cn("mt-2 truncate text-xs", advisor ? "text-muted" : "font-medium text-warning-text")}>{advisor?.name ?? "Sin asignar"}</p>
                      </Link>
                    </li>
                  );
                })}
                {items.length === 0 ? <li className="px-1 py-4 text-center text-xs text-muted">Sin leads</li> : null}
              </ul>
            </section>
          );
        })}
      </div>
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
