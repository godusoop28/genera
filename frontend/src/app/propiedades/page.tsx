"use client";

import { AdvisorChip, LoadError, Segmented, useApi } from "@/components/crm/CrmUi";
import { Features, ListingStatusBadge, NotConfiguredNotice, SyncBadge } from "@/components/crm/PropertyUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { SearchInput } from "@/components/ui/SearchInput";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { listCaptured, listInventory, type CapturedProperty, type InventoryItem, type ListingStatus, type Operation } from "@/lib/api/properties";
import { timeAgo } from "@/lib/crm/labels";
import { listingStatusLabels, operationLabels, priceOf, subtypeLabel } from "@/lib/crm/property-labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { ArrowRight, ChevronLeft, ChevronRight, ImageOff, Plus, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
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
  const can = useCan();
  const [tab, setTab] = useState<Tab>(params.get("vista") === "capturadas" ? "captured" : "inventory");
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
            { id: "captured", label: "Capturadas en Genera", count: captured.data ? captured.data.captured.length : undefined },
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
        <InventoryView configured={captured.data?.configured ?? true} />
      ) : (
        <CapturedView data={captured.data?.captured ?? null} error={captured.error} onRetry={captured.reload} />
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
          {item.generaId ? <Badge tone="gold">Capturada en Genera</Badge> : null}
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

function CapturedView({ data, error, onRetry }: { data: CapturedProperty[] | null; error: string | null; onRetry: () => void }) {
  if (error && !data) return <LoadError message={error} onRetry={onRetry} />;
  if (!data) return <StateMessage kind="loading" title="Cargando propiedades…" />;
  if (data.length === 0) {
    return <StateMessage kind="empty" title="Aún no hay propiedades capturadas" description="Las que des de alta en Genera aparecerán aquí con su estado de envío a 21Online." />;
  }
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
            <div className="min-w-0">
              <p className="truncate font-semibold text-obsessed">{p.data.headline}</p>
              <p className="truncate text-xs text-muted">
                {subtypeLabel(p.data.subtype)} · {p.data.neighborhood}, {p.data.municipality} · {timeAgo(p.createdAt)}
              </p>
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
