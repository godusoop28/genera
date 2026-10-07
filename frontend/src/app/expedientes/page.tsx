"use client";

import { ExpedienteStatusBadge } from "@/components/expediente/ExpedienteStatusBadge";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { SearchInput } from "@/components/ui/SearchInput";
import { StateMessage } from "@/components/ui/StateMessage";
import { ApiError } from "@/lib/api/client";
import {
  getExpedienteOverview,
  type DocumentProgress,
  type ExpedienteOverview,
  type ExpedienteOverviewItem,
  type ExpedienteStatusGroup,
} from "@/lib/api/expedientes";
import { propertyTypeLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { cn, initials } from "@/lib/utils";
import { ArrowRight, Calendar, CheckCircle2, Clock, FileText, FolderOpen, Home, LayoutGrid, List, MapPin, Plus, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";

// Criterio de agrupación (definido en el backend, ExpedienteOverviewController.StatusGroup):
// cada estatus pertenece a un solo grupo, así que pendientes + en revisión + completos = total.
const groups: ReadonlyArray<{ id: ExpedienteStatusGroup | null; label: string; hint: string }> = [
  { id: null, label: "Todos", hint: "Todos los expedientes que puedes ver" },
  { id: "PENDING", label: "Pendientes", hint: "Falta algo del cliente: liga, aviso de privacidad, documentos o correcciones" },
  { id: "IN_REVIEW", label: "En revisión", hint: "El cliente envió sus documentos y se están revisando" },
  { id: "COMPLETE", label: "Completos", hint: "Documentación aprobada (incluye contrato, firma, decisión y cierre)" },
];

type ViewMode = "grid" | "list";
const VIEW_KEY = "genera.expedientes.view";
const PAGE_SIZE = 18;

const dateFormat = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric" });

// Preferencia de vista (tarjetas o lista) por usuario y navegador: si no hay
// almacenamiento disponible, solo dura esta visita.
let sessionView: ViewMode | null = null;
const viewListeners = new Set<() => void>();

function readView(): ViewMode {
  if (sessionView) return sessionView;
  try {
    return window.localStorage.getItem(VIEW_KEY) === "list" ? "list" : "grid";
  } catch {
    return "grid";
  }
}

function storeView(next: ViewMode) {
  sessionView = next;
  try {
    window.localStorage.setItem(VIEW_KEY, next);
  } catch {
    // Sin almacenamiento: queda en memoria.
  }
  viewListeners.forEach((listener) => listener());
}

function subscribeView(listener: () => void) {
  viewListeners.add(listener);
  return () => {
    viewListeners.delete(listener);
  };
}

interface LoadedPage {
  key: string;
  items: ExpedienteOverviewItem[];
  counts: ExpedienteOverview["counts"];
  page: number;
  totalPages: number;
  totalElements: number;
}

function errorMessage(err: unknown): { message: string; forbidden: boolean } {
  if (err instanceof ApiError && err.status === 403) return { message: "Tu usuario no tiene permiso para consultar expedientes.", forbidden: true };
  if (err instanceof ApiError) return { message: `El servidor respondió con un error (${err.status}). Intenta de nuevo en un momento.`, forbidden: false };
  return { message: "No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.", forbidden: false };
}

export default function ExpedientesPage() {
  const can = useCan();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [group, setGroup] = useState<ExpedienteStatusGroup | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const view = useSyncExternalStore(subscribeView, readView, () => "grid" as ViewMode);
  const [data, setData] = useState<LoadedPage | null>(null);
  const [failure, setFailure] = useState<{ key: string; message: string; forbidden: boolean } | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<string | null>(null);

  // Cada combinación de búsqueda + filtro (+ "Actualizar") es una carga distinta;
  // "cargando" = la última respuesta no corresponde a lo que se pidió.
  const key = `${debouncedQuery.trim()}|${group ?? ""}|${reloadToken}`;
  const currentKey = useRef(key);
  useEffect(() => {
    currentKey.current = key;
  }, [key]);

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedQuery(query), 300);
    return () => window.clearTimeout(t);
  }, [query]);

  useEffect(() => {
    let cancelled = false;
    getExpedienteOverview({ q: debouncedQuery, group, page: 0, size: PAGE_SIZE })
      .then((res) => {
        if (cancelled) return;
        setData({ key, items: res.page.items, counts: res.counts, page: 0, totalPages: res.page.totalPages, totalElements: res.page.totalElements });
        setFailure(null);
        setMoreError(null);
      })
      .catch((err) => {
        if (!cancelled) setFailure({ key, ...errorMessage(err) });
      });
    return () => {
      cancelled = true;
    };
  }, [key, debouncedQuery, group]);

  const error = failure?.key === key ? failure : null;
  const loading = data?.key !== key && !error;
  const reload = () => setReloadToken((t) => t + 1);

  const loadMore = () => {
    if (!data) return;
    const requestedFor = data.key;
    setLoadingMore(true);
    setMoreError(null);
    getExpedienteOverview({ q: debouncedQuery, group, page: data.page + 1, size: PAGE_SIZE })
      .then((res) => {
        if (currentKey.current !== requestedFor) return;
        setData((prev) => {
          if (!prev || prev.key !== requestedFor) return prev;
          const seen = new Set(prev.items.map((i) => i.expediente.id));
          return {
            ...prev,
            items: [...prev.items, ...res.page.items.filter((i) => !seen.has(i.expediente.id))],
            counts: res.counts,
            page: prev.page + 1,
            totalPages: res.page.totalPages,
            totalElements: res.page.totalElements,
          };
        });
      })
      .catch(() => setMoreError("No se pudieron cargar más expedientes. Intenta de nuevo."))
      .finally(() => setLoadingMore(false));
  };

  const counts = data?.counts;
  const countFor = (id: ExpedienteStatusGroup | null) =>
    !counts ? null : id === null ? counts.total : id === "PENDING" ? counts.pending : id === "IN_REVIEW" ? counts.inReview : counts.complete;
  const filtering = debouncedQuery.trim().length > 0 || group !== null;
  const forbidden = error?.forbidden ?? false;

  return (
    <PageContainer
      title="Expedientes"
      subtitle="Da seguimiento a cada operación, desde los documentos del cliente hasta la firma del contrato."
      action={
        <>
          <Button variant="secondary" onClick={reload} disabled={loading} aria-label="Actualizar la lista" title="Actualizar la lista">
            <RefreshCw className={cn("h-4 w-4", loading && data ? "animate-spin" : "")} aria-hidden />
          </Button>
          {can("EXPEDIENT_CREATE") ? (
            <Link
              href="/expedientes/nuevo"
              className="inline-flex min-h-10 items-center justify-center gap-2 rounded-xl bg-gold px-4 py-2.5 text-sm font-medium text-brand-ink shadow-sm transition-colors duration-150 hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
            >
              <Plus className="h-4 w-4" aria-hidden />
              Nuevo expediente
            </Link>
          ) : null}
        </>
      }
    >
      {forbidden ? (
        <StateMessage kind="restricted" title="Acceso restringido" description={error?.message} />
      ) : (
        <>
          <section aria-label="Resumen" className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
            <StatCard icon={FolderOpen} tone="neutral" value={countFor(null)} label="Total" hint="Expedientes que puedes ver" />
            <StatCard icon={Clock} tone="warning" value={countFor("PENDING")} label="Pendientes" hint="Esperan algo del cliente" />
            <StatCard icon={RefreshCw} tone="info" value={countFor("IN_REVIEW")} label="En revisión" hint="Documentos por revisar" />
            <StatCard icon={CheckCircle2} tone="success" value={countFor("COMPLETE")} label="Completos" hint="Documentación aprobada" />
          </section>

          <div className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-center">
            <SearchInput
              value={query}
              onChange={setQuery}
              label="Buscar expedientes por cliente, folio o domicilio"
              placeholder="Buscar por cliente, folio o domicilio"
              className="lg:max-w-md lg:flex-1"
            />
            <div className="flex flex-1 flex-wrap items-center gap-2" role="group" aria-label="Filtrar por estado">
              {groups.map((g) => {
                const active = group === g.id;
                const n = countFor(g.id);
                return (
                  <button
                    key={g.label}
                    type="button"
                    onClick={() => setGroup(g.id)}
                    aria-pressed={active}
                    title={g.hint}
                    className={cn(
                      "inline-flex min-h-10 items-center gap-2 rounded-full border px-4 text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                      active ? "border-gold bg-gold text-brand-ink" : "border-border bg-card text-muted hover:border-gold/60 hover:text-obsessed",
                    )}
                  >
                    {g.label}
                    {n !== null ? (
                      <span className={cn("rounded-full px-1.5 text-xs tabular-nums", active ? "bg-white/40" : "bg-app-bg")}>{n}</span>
                    ) : null}
                  </button>
                );
              })}
            </div>
            <div className="flex shrink-0 gap-1 self-start rounded-xl border border-border bg-card p-1 lg:self-auto" role="group" aria-label="Vista">
              <ViewButton active={view === "grid"} onClick={() => storeView("grid")} label="Ver como tarjetas" icon={LayoutGrid} />
              <ViewButton active={view === "list"} onClick={() => storeView("list")} label="Ver como lista" icon={List} />
            </div>
          </div>

          {error && !data ? (
            <StateMessage
              kind="error"
              title="No se pudieron cargar los expedientes"
              description={error.message}
              action={
                <Button variant="secondary" onClick={reload}>
                  <RefreshCw className="h-4 w-4" aria-hidden /> Reintentar
                </Button>
              }
            />
          ) : !data ? (
            <StateMessage kind="loading" title="Cargando expedientes…" />
          ) : (
            <div aria-busy={loading} className={cn("transition-opacity duration-150", loading && "opacity-60")}>
              {error ? (
                <p role="alert" className="mb-4 flex flex-wrap items-center gap-3 rounded-xl border border-danger-text/20 bg-danger-bg px-4 py-3 text-sm text-danger-text">
                  {error.message}
                  <Button size="sm" variant="secondary" onClick={reload}>
                    Reintentar
                  </Button>
                </p>
              ) : null}

              {data.items.length === 0 ? (
                filtering ? (
                  <StateMessage
                    kind="no-results"
                    title="Sin resultados"
                    description={debouncedQuery.trim() ? `Ningún expediente coincide con “${debouncedQuery.trim()}” en este filtro.` : "No hay expedientes en este filtro."}
                    action={
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setQuery("");
                          setDebouncedQuery("");
                          setGroup(null);
                        }}
                      >
                        Quitar filtros
                      </Button>
                    }
                  />
                ) : (
                  <StateMessage
                    kind="empty"
                    title="Aún no hay expedientes"
                    description="Cuando crees un expediente aparecerá aquí con su avance documental."
                    action={
                      can("EXPEDIENT_CREATE") ? (
                        <Link
                          href="/expedientes/nuevo"
                          className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-gold px-4 py-2.5 text-sm font-medium text-brand-ink hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
                        >
                          <Plus className="h-4 w-4" aria-hidden /> Nuevo expediente
                        </Link>
                      ) : null
                    }
                  />
                )
              ) : view === "grid" ? (
                <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                  {data.items.map((item) => (
                    <li key={item.expediente.id} className="min-w-0">
                      <ExpedienteCard item={item} />
                    </li>
                  ))}
                </ul>
              ) : (
                <ExpedienteList items={data.items} />
              )}

              {data.items.length > 0 ? (
                <div className="mt-6 flex flex-col items-center gap-3">
                  <p className="text-xs text-muted" aria-live="polite">
                    Mostrando {data.items.length} de {data.totalElements} expediente{data.totalElements === 1 ? "" : "s"}
                    {filtering ? " que coinciden" : ""}
                  </p>
                  {moreError ? <p className="text-sm text-danger-text">{moreError}</p> : null}
                  {data.page + 1 < data.totalPages ? (
                    <Button variant="secondary" onClick={loadMore} disabled={loadingMore}>
                      {loadingMore ? "Cargando…" : "Cargar más expedientes"}
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </div>
          )}
        </>
      )}
    </PageContainer>
  );
}

const statTone = {
  neutral: "bg-gold/20 text-dark-gold",
  warning: "bg-warning-bg text-warning-text",
  info: "bg-info-bg text-info-text",
  success: "bg-success-bg text-success-text",
} as const;

function StatCard({
  icon: Icon,
  tone,
  value,
  label,
  hint,
}: {
  icon: typeof Clock;
  tone: keyof typeof statTone;
  value: number | null;
  label: string;
  hint: string;
}) {
  return (
    <div className="flex min-w-0 items-center gap-3 rounded-xl border border-border bg-card p-4 shadow-[0_1px_2px_rgba(36,39,35,0.04)] sm:gap-4 sm:p-5">
      <span className={cn("flex h-11 w-11 shrink-0 items-center justify-center rounded-full sm:h-12 sm:w-12", statTone[tone])}>
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="text-2xl font-semibold tabular-nums leading-none text-obsessed">{value ?? "—"}</p>
        <p className="mt-1 text-sm font-medium text-obsessed">{label}</p>
        <p className="hidden text-xs text-muted sm:block">{hint}</p>
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

function DocumentsProgress({ progress, compact = false }: { progress: DocumentProgress; compact?: boolean }) {
  if (progress.required === 0) {
    return <p className="text-xs text-muted">Sin documentos obligatorios registrados</p>;
  }
  const pct = Math.round((progress.accepted / progress.required) * 100);
  return (
    <div className="min-w-0">
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="text-obsessed">
          Aprobados: <strong className="font-semibold tabular-nums">{progress.accepted}</strong> de{" "}
          <span className="tabular-nums">{progress.required}</span>
        </span>
        <span className="text-xs tabular-nums text-muted">{pct}%</span>
      </div>
      <div
        className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-app-bg"
        role="progressbar"
        aria-label="Documentos obligatorios aprobados"
        aria-valuemin={0}
        aria-valuemax={progress.required}
        aria-valuenow={progress.accepted}
      >
        <div className={cn("h-full rounded-full transition-[width] duration-200", pct === 100 ? "bg-success-text" : "bg-gold")} style={{ width: `${pct}%` }} />
      </div>
      {!compact ? (
        <p className="mt-1 text-xs text-muted">
          Recibidos: <span className="tabular-nums">{progress.received}</span> de <span className="tabular-nums">{progress.required}</span> obligatorios
        </p>
      ) : null}
    </div>
  );
}

function operationLabel(item: ExpedienteOverviewItem) {
  return `Intermediación exclusiva · ${propertyTypeLabels[item.expediente.propertyCaseType]}`;
}

function ExpedienteCard({ item }: { item: ExpedienteOverviewItem }) {
  const e = item.expediente;
  return (
    <article className="group flex h-full flex-col rounded-xl border border-border bg-card p-5 shadow-[0_1px_2px_rgba(36,39,35,0.04)] transition-shadow duration-200 hover:shadow-md">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <span className="font-mono text-xs font-medium tracking-wide text-muted">{e.folio}</span>
        <ExpedienteStatusBadge status={e.status} />
      </div>

      <div className="mt-4 flex items-start gap-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-app-bg text-sm font-semibold text-obsessed" aria-hidden>
          {initials(e.ownerDisplayName)}
        </span>
        <div className="min-w-0">
          <h2 className="line-clamp-2 break-words text-base font-semibold leading-snug text-obsessed" title={e.ownerDisplayName}>
            {e.ownerDisplayName || "Sin nombre"}
          </h2>
          <p className="mt-0.5 flex items-center gap-1.5 text-sm text-muted">
            <Home className="h-3.5 w-3.5 shrink-0" aria-hidden />
            {operationLabel(item)}
          </p>
        </div>
      </div>

      <div className="mt-4 space-y-3 border-t border-border pt-4">
        <p className="flex items-start gap-2 text-sm text-muted">
          <MapPin className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <span className="line-clamp-2 break-words" title={e.propertyAddress ?? e.propertyReference ?? undefined}>
            {e.propertyAddress || e.propertyReference || "Domicilio pendiente (se toma de los documentos)"}
          </span>
        </p>
        <div className="flex items-start gap-2">
          <FileText className="mt-0.5 h-4 w-4 shrink-0 text-muted" aria-hidden />
          <div className="min-w-0 flex-1">
            <DocumentsProgress progress={item.documents} />
          </div>
        </div>
      </div>

      <div className="mt-auto flex flex-wrap items-center justify-between gap-2 pt-4">
        <span className="flex items-center gap-1.5 text-xs text-muted">
          <Calendar className="h-3.5 w-3.5" aria-hidden />
          Actualizado: {dateFormat.format(new Date(e.updatedAt))}
        </span>
        <Link
          href={`/expedientes/${e.id}`}
          className="inline-flex min-h-9 items-center gap-1 rounded-lg px-1 text-sm font-medium text-dark-gold transition-colors duration-150 hover:text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
          aria-label={`Ver expediente ${e.folio} de ${e.ownerDisplayName}`}
        >
          Ver expediente
          <ArrowRight className="h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5" aria-hidden />
        </Link>
      </div>
    </article>
  );
}

function ExpedienteList({ items }: { items: ExpedienteOverviewItem[] }) {
  return (
    <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
      <div className="hidden grid-cols-[minmax(0,2.2fr)_minmax(0,1.3fr)_minmax(0,1.4fr)_minmax(0,1fr)_auto] gap-4 border-b border-border bg-app-bg px-5 py-3 text-xs font-medium uppercase tracking-wide text-muted lg:grid">
        <span>Cliente</span>
        <span>Estado</span>
        <span>Documentos</span>
        <span>Actualizado</span>
        <span className="sr-only">Acción</span>
      </div>
      <ul className="divide-y divide-border">
        {items.map((item) => {
          const e = item.expediente;
          return (
            <li
              key={e.id}
              className="grid grid-cols-1 gap-3 px-4 py-4 sm:px-5 lg:grid-cols-[minmax(0,2.2fr)_minmax(0,1.3fr)_minmax(0,1.4fr)_minmax(0,1fr)_auto] lg:items-center lg:gap-4"
            >
              <div className="flex min-w-0 items-start gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-app-bg text-xs font-semibold text-obsessed" aria-hidden>
                  {initials(e.ownerDisplayName)}
                </span>
                <div className="min-w-0">
                  <p className="line-clamp-2 break-words font-semibold text-obsessed" title={e.ownerDisplayName}>
                    {e.ownerDisplayName || "Sin nombre"}
                  </p>
                  <p className="font-mono text-xs text-muted">{e.folio}</p>
                  <p className="truncate text-xs text-muted" title={e.propertyAddress ?? e.propertyReference ?? undefined}>
                    {e.propertyAddress || e.propertyReference || operationLabel(item)}
                  </p>
                </div>
              </div>
              <div>
                <ExpedienteStatusBadge status={e.status} />
              </div>
              <DocumentsProgress progress={item.documents} compact />
              <span className="text-sm text-muted">
                <span className="lg:sr-only">Actualizado: </span>
                {dateFormat.format(new Date(e.updatedAt))}
              </span>
              <Link
                href={`/expedientes/${e.id}`}
                className="inline-flex min-h-9 items-center gap-1 justify-self-start rounded-lg text-sm font-medium text-dark-gold hover:text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 lg:justify-self-end"
                aria-label={`Ver expediente ${e.folio} de ${e.ownerDisplayName}`}
              >
                Ver expediente <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
