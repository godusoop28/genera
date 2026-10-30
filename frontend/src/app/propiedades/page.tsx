"use client";

import { AdvisorChip, LoadError, Segmented, errorText, useApi } from "@/components/crm/CrmUi";
import { TagChips } from "@/components/crm/PropertyNotes";
import { Features, ListingStatusBadge, NotConfiguredNotice, SyncBadge } from "@/components/crm/PropertyUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { SearchInput } from "@/components/ui/SearchInput";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { MoneyInput } from "@/components/ui/MoneyInput";
import {
  importInventory,
  listCaptured,
  listCapturedPage,
  listInventory,
  type CapturedProperty,
  type CapturedSort,
  type InventoryItem,
  type ListingStatus,
  type Operation,
} from "@/lib/api/properties";
import { timeAgo } from "@/lib/crm/labels";
import { listingStatusLabels, operationLabels, priceOf, subtypeLabel } from "@/lib/crm/property-labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { ArrowRight, ChevronLeft, ChevronRight, ImageOff, Plus, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

type Tab = "inventory" | "captured";

export default function PropertiesPage() {
  // useSearchParams necesita un límite de Suspense para el prerender.
  return (
    <Suspense fallback={null}>
      <PropertiesScreen />
    </Suspense>
  );
}

function PropertiesScreen() {
  const params = useSearchParams();
  const router = useRouter();
  const can = useCan();
  const [tab, setTabState] = useState<Tab>(params.get("vista") === "capturadas" ? "captured" : "inventory");
  // Al volver al inventario de 21Online, la URL deja de recordar los filtros de "En Genera".
  const setTab = (next: Tab) => {
    setTabState(next);
    if (next === "inventory") router.replace("/propiedades", { scroll: false });
  };
  const captured = useApi(() => listCaptured(), []);
  const pendingCount = captured.data?.captured.filter((p) => p.syncStatus !== "DONE").length ?? 0;

  return (
    <PageContainer
      title="Propiedades"
      subtitle="Inventario de la oficina en 21Online. Lo que des de alta aquí se publica también en 21Online."
      action={
        can("PROPERTY_MANAGE") ? (
          <Link
            href="/propiedades/nueva"
            className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-gold px-4 text-sm font-semibold text-brand-ink transition-colors hover:bg-gold/90 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
          >
            <Plus className="h-4 w-4" aria-hidden /> Nueva propiedad
          </Link>
        ) : null
      }
    >
      {captured.data && !captured.data.configured ? <NotConfiguredNotice /> : null}
      <div className="mb-5">
        <Segmented
          label="Vista"
          value={tab}
          onChange={setTab}
          options={[
            { id: "inventory", label: "Inventario 21Online" },
            { id: "captured", label: "En Genera", count: captured.data ? captured.data.captured.length : undefined },
          ]}
        />
        {pendingCount > 0 && tab === "inventory" ? (
          <p className="mt-2 text-xs text-warning-text">
            {pendingCount === 1 ? "1 propiedad capturada aún no está" : `${pendingCount} propiedades capturadas aún no están`} en 21Online.{" "}
            <button type="button" className="font-medium underline" onClick={() => setTab("captured")}>
              Revisar
            </button>
          </p>
        ) : null}
      </div>
      {tab === "inventory" ? (
        <>
          {/* CC#36: la API de 21Online no filtra ni ordena por precio; el inventario completo con esos filtros está en "En Genera". */}
          <p className="mb-4 text-sm text-muted">
            ¿Buscas por rango de precio u orden por precio o antigüedad?{" "}
            <button type="button" className="font-medium text-dark-gold underline" onClick={() => setTab("captured")}>
              Usa la vista En Genera
            </button>
            : tiene todo el inventario de la oficina (se actualiza desde 21Online cada pocos minutos).
          </p>
          <InventoryView configured={captured.data?.configured ?? true} />
        </>
      ) : (
        <CapturedView
          onChanged={captured.reload}
          canImport={can("PROPERTY_MANAGE") && can("LEAD_VIEW_ALL") && (captured.data?.configured ?? false)}
        />
      )}
    </PageContainer>
  );
}

const statusFilterOptions: Array<{ value: string; label: string }> = [
  { value: "enPromocion", label: "En promoción" },
  { value: "", label: "Todos los estatus" },
  ...(["suspendida", "conIntencion", "conContratoFirmado", "cerrada", "cancelada"] as ListingStatus[]).map((s) => ({ value: s, label: listingStatusLabels[s] })),
];

function InventoryView({ configured }: { configured: boolean }) {
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [status, setStatus] = useState("enPromocion");
  const [operation, setOperation] = useState<Operation | "">("");
  const [page, setPage] = useState(1);

  useEffect(() => {
    const t = window.setTimeout(() => {
      setDebounced(query.trim());
      setPage(1);
    }, 400);
    return () => window.clearTimeout(t);
  }, [query]);

  const inventory = useApi(() => listInventory({ q: debounced, status, operation, page }), [debounced, status, operation, page]);
  const data = inventory.data;

  if (!configured || (data && !data.configured)) {
    return (
      <StateMessage
        kind="empty"
        title="Sin conexión con 21Online"
        description="Cuando se configure la llave de 21Online aquí verás todo el inventario de la oficina."
      />
    );
  }

  return (
    <>
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end">
        <SearchInput value={query} onChange={setQuery} label="Buscar por número de 21Online o ubicación (colonia, municipio)" className="lg:max-w-md lg:flex-1" />
        <Select
          aria-label="Filtrar por estatus"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(1);
          }}
          options={statusFilterOptions}
          containerClassName="lg:w-56"
          className="h-11"
        />
        <Select
          aria-label="Filtrar por operación"
          value={operation}
          onChange={(e) => {
            setOperation(e.target.value as Operation | "");
            setPage(1);
          }}
          options={[{ value: "", label: "Venta y renta" }, ...(Object.keys(operationLabels) as Operation[]).map((o) => ({ value: o, label: operationLabels[o] }))]}
          containerClassName="lg:w-44"
          className="h-11"
        />
        <Button variant="secondary" onClick={inventory.reload} aria-label="Actualizar" title="Actualizar">
          <RefreshCw className="h-4 w-4" aria-hidden />
        </Button>
      </div>

      {inventory.error && !data ? (
        <LoadError message={inventory.error} onRetry={inventory.reload} />
      ) : !data ? (
        <StateMessage kind="loading" title="Consultando 21Online…" />
      ) : data.error ? (
        <LoadError message={data.error} onRetry={inventory.reload} />
      ) : data.items.length === 0 ? (
        <StateMessage kind="no-results" title="Sin propiedades" description="Ninguna propiedad de 21Online coincide con estos filtros." />
      ) : (
        <>
          <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {data.items.map((item) => (
              <li key={item.listing.id}>
                <ListingCard item={item} />
              </li>
            ))}
          </ul>
          <div className="mt-5 flex items-center justify-between gap-3 text-sm text-muted">
            <span>
              Página {data.page}
              {data.totalItems !== null ? ` · ${data.totalItems} propiedades` : ""}
            </span>
            <div className="flex gap-2">
              <Button variant="secondary" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))} aria-label="Página anterior">
                <ChevronLeft className="h-4 w-4" aria-hidden />
              </Button>
              <Button variant="secondary" disabled={!data.hasNext} onClick={() => setPage((p) => p + 1)} aria-label="Página siguiente">
                <ChevronRight className="h-4 w-4" aria-hidden />
              </Button>
            </div>
          </div>
        </>
      )}
    </>
  );
}

function ListingCard({ item }: { item: InventoryItem }) {
  const l = item.listing;
  const place = [l.neighborhood, l.municipality].filter(Boolean).join(", ");
  return (
    <Link
      href={`/propiedades/21online/${l.id}`}
      className="flex h-full flex-col overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)] transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
    >
      <div className="relative aspect-[16/9] bg-app-bg">
        {l.photoUrl ? (
          // Las fotos viven en el CDN de 21Online; no pasan por el optimizador de Next.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={l.photoUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center text-muted">
            <ImageOff className="h-6 w-6" aria-hidden />
          </div>
        )}
        <div className="absolute left-3 top-3 flex flex-wrap gap-1.5">
          <ListingStatusBadge status={l.status} />
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <p className="text-xs text-muted">
          {l.internalKey ? `${l.internalKey} · ` : ""}
          {l.id} · {l.subtypeLabel} en {l.operation === "renta" ? "renta" : "venta"}
        </p>
        <p className="line-clamp-2 font-semibold text-obsessed">{l.title}</p>
        <p className="text-lg font-semibold tabular-nums text-dark-gold">{priceOf(l)}</p>
        {place ? <p className="truncate text-sm text-muted">{place}</p> : null}
        <Features bedrooms={l.bedrooms} bathrooms={l.bathrooms} builtM2={l.builtM2} landM2={l.landM2} />
        <div className="mt-auto pt-2">
          <AdvisorChip advisor={item.advisor} emptyLabel={l.advisorOnline21Id ? "Asesor sin vincular" : "Sin asesor"} />
        </div>
      </div>
    </Link>
  );
}

/** Trae ya todo el inventario de 21Online (el backend también lo hace solo cada pocos minutos). */
function ImportBar({ onDone }: { onDone: () => void }) {
  const { showToast } = useToast();
  const [busy, setBusy] = useState(false);
  return (
    <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-muted">Todo el inventario de la oficina en 21Online se trae solo cada pocos minutos, con sus fotos y características.</p>
      <Button
        variant="secondary"
        disabled={busy}
        className="shrink-0"
        onClick={async () => {
          setBusy(true);
          try {
            const r = await importInventory();
            showToast(r.imported || r.updated ? `21Online: ${r.imported} nuevas y ${r.updated} actualizadas de ${r.total}.` : `Todo al día: ${r.total} propiedades en 21Online.`);
            onDone();
          } catch (err) {
            showToast(errorText(err));
          } finally {
            setBusy(false);
          }
        }}
      >
        <RefreshCw className="h-4 w-4" aria-hidden /> {busy ? "Trayendo de 21Online…" : "Traer de 21Online ahora"}
      </Button>
    </div>
  );
}

const sortOptions: Array<{ value: CapturedSort; label: string }> = [
  { value: "NEWEST", label: "Más recientes primero" },
  { value: "OLDEST", label: "Más antiguas primero" },
  { value: "PRICE_ASC", label: "Precio: de menor a mayor" },
  { value: "PRICE_DESC", label: "Precio: de mayor a menor" },
];

const CAPTURED_PAGE_SIZE = 20;

/** Filtros en la URL: se conservan al paginar, al abrir una propiedad y al regresar. */
interface CapturedFilters {
  q: string;
  tag: string;
  status: string;
  operation: Operation | "";
  minPrice: string;
  maxPrice: string;
  currency: string;
  sort: CapturedSort;
  page: number;
}

function filtersFrom(params: URLSearchParams): CapturedFilters {
  const sort = params.get("orden") as CapturedSort | null;
  const operation = params.get("operacion");
  return {
    q: params.get("q") ?? "",
    tag: params.get("etiqueta") ?? "",
    status: params.get("estatus") ?? "",
    operation: operation === "venta" || operation === "renta" ? operation : "",
    minPrice: (params.get("min") ?? "").replace(/[^\d.]/g, ""),
    maxPrice: (params.get("max") ?? "").replace(/[^\d.]/g, ""),
    currency: params.get("moneda") === "USD" ? "USD" : "MXN",
    sort: sortOptions.some((o) => o.value === sort) ? (sort as CapturedSort) : "NEWEST",
    page: Math.max(1, Number(params.get("pagina")) || 1),
  };
}

function urlOf(f: CapturedFilters): string {
  const search = new URLSearchParams({ vista: "capturadas" });
  if (f.q.trim()) search.set("q", f.q.trim());
  if (f.tag) search.set("etiqueta", f.tag);
  if (f.status) search.set("estatus", f.status);
  if (f.operation) search.set("operacion", f.operation);
  if (f.minPrice) search.set("min", f.minPrice);
  if (f.maxPrice) search.set("max", f.maxPrice);
  if (f.currency !== "MXN") search.set("moneda", f.currency);
  if (f.sort !== "NEWEST") search.set("orden", f.sort);
  if (f.page > 1) search.set("pagina", String(f.page));
  return `/propiedades?${search.toString()}`;
}

/** Importe crudo sin el punto final que puede quedar mientras se escribe ("5000." → "5000"). */
const amount = (raw: string) => raw.replace(/\.$/, "");

/**
 * CC#36: todo el inventario que hay en Genera (capturadas aquí e importadas de 21Online) con
 * búsqueda, filtros de precio y un criterio de orden, aplicados en el servidor sobre el conjunto
 * completo, no solo sobre la página visible.
 */
function CapturedView({ onChanged, canImport }: { onChanged: () => void; canImport: boolean }) {
  const params = useSearchParams();
  const router = useRouter();
  const [filters, setFilters] = useState<CapturedFilters>(() => filtersFrom(new URLSearchParams(params.toString())));
  // Texto y precios se aplican tras una pausa al escribir.
  const [applied, setApplied] = useState(filters);

  useEffect(() => {
    const t = window.setTimeout(() => setApplied(filters), 400);
    return () => window.clearTimeout(t);
  }, [filters]);

  const priceError =
    applied.minPrice && applied.maxPrice && Number(amount(applied.minPrice)) > Number(amount(applied.maxPrice))
      ? "El precio mínimo no puede ser mayor que el máximo."
      : null;
  const shownPriceError =
    filters.minPrice && filters.maxPrice && Number(amount(filters.minPrice)) > Number(amount(filters.maxPrice))
      ? "El precio mínimo no puede ser mayor que el máximo."
      : undefined;

  useEffect(() => {
    router.replace(urlOf(applied), { scroll: false });
  }, [applied, router]);

  const result = useApi(
    () =>
      priceError
        ? Promise.resolve(null)
        : listCapturedPage({
            q: applied.q.trim(),
            tag: applied.tag,
            status: applied.status,
            operation: applied.operation,
            minPrice: amount(applied.minPrice),
            maxPrice: amount(applied.maxPrice),
            currency: applied.minPrice || applied.maxPrice || applied.sort === "PRICE_ASC" || applied.sort === "PRICE_DESC" ? applied.currency : undefined,
            sort: applied.sort,
            page: applied.page,
            size: CAPTURED_PAGE_SIZE,
          }),
    [applied, Boolean(priceError)],
  );
  const data = result.data;

  // Cambiar un filtro regresa a la primera página; paginar conserva los filtros.
  const change = (patch: Partial<CapturedFilters>, immediate = false) => {
    const next = { ...filters, ...patch, page: patch.page ?? 1 };
    setFilters(next);
    if (immediate) setApplied(next);
  };
  const usesPrice = Boolean(filters.minPrice || filters.maxPrice || filters.sort === "PRICE_ASC" || filters.sort === "PRICE_DESC");
  const anyFilter = Boolean(filters.q.trim() || filters.tag || filters.status || filters.operation || filters.minPrice || filters.maxPrice);

  return (
    <>
      {canImport ? (
        <ImportBar
          onDone={() => {
            onChanged();
            result.reload();
          }}
        />
      ) : null}
      <div className="mb-3 flex flex-col gap-3 lg:flex-row lg:items-end">
        <SearchInput value={filters.q} onChange={(q) => change({ q })} label="Buscar por título, colonia, asesor, número o etiqueta" className="lg:max-w-md lg:flex-1" />
        <Select
          aria-label="Filtrar por estatus"
          value={filters.status}
          onChange={(e) => change({ status: e.target.value }, true)}
          options={[{ value: "", label: "Todos los estatus" }, ...statusFilterOptions.filter((o) => o.value)]}
          containerClassName="lg:w-52"
          className="h-11"
        />
        <Select
          aria-label="Filtrar por operación"
          value={filters.operation}
          onChange={(e) => change({ operation: e.target.value as Operation | "" }, true)}
          options={[{ value: "", label: "Venta y renta" }, ...(Object.keys(operationLabels) as Operation[]).map((o) => ({ value: o, label: operationLabels[o] }))]}
          containerClassName="lg:w-40"
          className="h-11"
        />
        {data && data.tags.length > 0 ? (
          <Select
            aria-label="Filtrar por etiqueta"
            value={filters.tag}
            onChange={(e) => change({ tag: e.target.value }, true)}
            options={[{ value: "", label: "Todas las etiquetas" }, ...data.tags.map((tag) => ({ value: tag, label: tag }))]}
            containerClassName="lg:w-48"
            className="h-11"
          />
        ) : null}
      </div>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_8rem_minmax(0,1.2fr)] lg:items-start">
        <MoneyInput
          label="Precio mínimo"
          placeholder="Sin mínimo"
          currency={filters.currency}
          value={filters.minPrice}
          onChange={(minPrice) => change({ minPrice })}
          error={shownPriceError}
        />
        <MoneyInput
          label="Precio máximo"
          placeholder="Sin máximo"
          currency={filters.currency}
          value={filters.maxPrice}
          onChange={(maxPrice) => change({ maxPrice })}
        />
        <Select
          label="Moneda"
          value={filters.currency}
          onChange={(e) => change({ currency: e.target.value }, true)}
          options={[
            { value: "MXN", label: "MXN" },
            { value: "USD", label: "USD" },
          ]}
        />
        <Select
          label="Ordenar por"
          value={filters.sort}
          onChange={(e) => change({ sort: e.target.value as CapturedSort }, true)}
          options={sortOptions}
        />
      </div>
      {usesPrice ? (
        <p className="-mt-2 mb-4 text-xs text-muted">
          Los precios se comparan en {filters.currency} con el precio de la operación (venta o renta mensual); los límites incluyen el importe indicado. Las propiedades
          sin precio quedan fuera del rango y al final del orden por precio.
        </p>
      ) : null}

      {priceError ? (
        <StateMessage kind="no-results" title="Revisa el rango de precios" description={priceError} />
      ) : result.error && !data ? (
        <LoadError message={result.error} onRetry={result.reload} />
      ) : !data ? (
        <StateMessage kind="loading" title="Cargando propiedades…" />
      ) : data.totalItems === 0 ? (
        anyFilter ? (
          <StateMessage
            kind="no-results"
            title="Sin resultados"
            description="Ninguna propiedad coincide con estos filtros."
            action={
              <Button
                variant="secondary"
                onClick={() => change({ q: "", tag: "", status: "", operation: "", minPrice: "", maxPrice: "" }, true)}
              >
                Quitar filtros
              </Button>
            }
          />
        ) : (
          <StateMessage kind="empty" title="Aún no hay propiedades en Genera" description="Aquí aparecen las que des de alta en Genera y todo el inventario que se trae de 21Online." />
        )
      ) : (
        <>
          <CapturedTable data={data.captured} />
          <div className="mt-5 flex items-center justify-between gap-3 text-sm text-muted">
            <span>
              Página {data.page} de {Math.max(1, data.totalPages)} · {data.totalItems} {data.totalItems === 1 ? "propiedad" : "propiedades"}
            </span>
            <div className="flex gap-2">
              <Button variant="secondary" disabled={data.page <= 1} onClick={() => change({ page: data.page - 1 }, true)} aria-label="Página anterior">
                <ChevronLeft className="h-4 w-4" aria-hidden />
              </Button>
              <Button variant="secondary" disabled={data.page >= data.totalPages} onClick={() => change({ page: data.page + 1 }, true)} aria-label="Página siguiente">
                <ChevronRight className="h-4 w-4" aria-hidden />
              </Button>
            </div>
          </div>
        </>
      )}
    </>
  );
}

function CapturedTable({ data }: { data: CapturedProperty[] }) {
  const cols = "lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.1fr)_auto]";
  return (
    <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
      <div className={cn("hidden gap-4 border-b border-border bg-app-bg px-5 py-3 text-xs font-medium uppercase tracking-wide text-muted lg:grid", cols)}>
        <span>Propiedad</span>
        <span>Precio</span>
        <span>Asesor</span>
        <span>21Online</span>
        <span className="sr-only">Acción</span>
      </div>
      <ul className="divide-y divide-border">
        {data.map((p) => (
          <li key={p.id} className={cn("grid grid-cols-1 gap-3 px-4 py-4 sm:px-5 lg:items-center lg:gap-4", cols)}>
            <div className="flex min-w-0 items-center gap-3">
              <div className="h-12 w-16 shrink-0 overflow-hidden rounded-lg bg-app-bg">
                {p.photos?.[0] ? (
                  // Las fotos viven en el CDN de 21Online; no pasan por el optimizador de Next.
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={p.photos[0]} alt="" loading="lazy" className="h-full w-full object-cover" />
                ) : (
                  <div className="flex h-full items-center justify-center text-muted">
                    <ImageOff className="h-4 w-4" aria-hidden />
                  </div>
                )}
              </div>
              <div className="min-w-0">
                <p className="truncate font-semibold text-obsessed">{p.data.headline}</p>
                <p className="truncate text-xs text-muted">
                  {subtypeLabel(p.data.subtype)} · {p.data.neighborhood}, {p.data.municipality} · {p.origin === "ONLINE21" ? "de 21Online" : "capturada"} · alta{" "}
                  {timeAgo(p.listedAt ?? p.createdAt)}
                </p>
                <TagChips tags={p.tags ?? []} className="mt-1 flex flex-wrap gap-1" />
              </div>
            </div>
            <p className="text-sm font-medium tabular-nums text-obsessed">{priceOf(p.data)}</p>
            <AdvisorChip advisor={p.advisor} emptyLabel="Sin asesor" />
            <div className="min-w-0 space-y-1">
              <SyncBadge status={p.syncStatus} />
              {p.online21Id ? <p className="text-xs text-muted">Núm. {p.online21Id}</p> : null}
            </div>
            <Link
              href={`/propiedades/${p.id}`}
              className="inline-flex min-h-9 items-center gap-1 justify-self-start rounded-lg text-sm font-medium text-dark-gold hover:text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 lg:justify-self-end"
              aria-label={`Ver ${p.data.headline}`}
            >
              Ver <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
