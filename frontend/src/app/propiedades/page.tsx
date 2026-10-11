"use client";

import { AdvisorChip, PreviewNotice, Segmented } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { SearchInput } from "@/components/ui/SearchInput";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { ONLINE21_BASE_URL } from "@/data/crm/properties";
import { formatDate, propertyPrice } from "@/lib/crm/labels";
import { useCrm, type CrmState } from "@/lib/crm/store";
import { ArrowRight, ExternalLink, MapPin, Users } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";

type Operation = "all" | "venta" | "renta";
type Sort = "leads" | "recent" | "price";

export default function PropiedadesPage() {
  const crm = useCrm();
  return (
    <PageContainer
      title="Propiedades"
      subtitle="Inventario en promoción consultado en 21Online, con los leads que ha generado cada propiedad."
      action={
        <a
          href={`${ONLINE21_BASE_URL}/propiedades`}
          target="_blank"
          rel="noreferrer"
          className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-medium text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-obsessed/15"
        >
          Abrir 21Online <ExternalLink className="h-4 w-4" aria-hidden />
        </a>
      }
    >
      <PreviewNotice>
        Muestra de 20 propiedades tomadas de 21Online el 11/10/2026. Aquí solo se consultan: el alta y la edición siguen haciéndose en 21Online (eso es el Módulo 2).
      </PreviewNotice>
      {crm ? <PropertiesContent crm={crm} /> : <StateMessage kind="loading" title="Cargando propiedades…" />}
    </PageContainer>
  );
}

function PropertiesContent({ crm }: { crm: CrmState }) {
  const [query, setQuery] = useState("");
  const [operation, setOperation] = useState<Operation>("all");
  const [advisorId, setAdvisorId] = useState("");
  const [sort, setSort] = useState<Sort>("leads");

  const leadCount = useMemo(() => {
    const map = new Map<number, number>();
    for (const l of crm.leads) if (l.propertyId) map.set(l.propertyId, (map.get(l.propertyId) ?? 0) + 1);
    return map;
  }, [crm.leads]);
  const advisorById = useMemo(() => new Map(crm.advisors.map((a) => [a.id, a])), [crm.advisors]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return crm.properties
      .filter((p) => operation === "all" || p.operation === operation)
      .filter((p) => !advisorId || p.advisorId === advisorId)
      .filter((p) => !q || [p.key, p.internalKey, p.street, p.neighborhood, p.municipality, p.subtype].some((v) => v.toLowerCase().includes(q)))
      .sort((a, b) =>
        sort === "leads" ? (leadCount.get(b.id) ?? 0) - (leadCount.get(a.id) ?? 0) : sort === "recent" ? b.updatedAt.localeCompare(a.updatedAt) : b.price - a.price,
      );
  }, [crm.properties, query, operation, advisorId, sort, leadCount]);

  const advisorsWithProperties = crm.advisors.filter((a) => crm.properties.some((p) => p.advisorId === a.id));

  return (
    <>
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end">
        <SearchInput value={query} onChange={setQuery} label="Buscar por clave, colonia o municipio" className="lg:max-w-md lg:flex-1" />
        <Select
          aria-label="Filtrar por asesor"
          value={advisorId}
          onChange={(e) => setAdvisorId(e.target.value)}
          options={[{ value: "", label: "Todos los asesores" }, ...advisorsWithProperties.map((a) => ({ value: a.id, label: a.name }))]}
          containerClassName="lg:w-64"
          className="h-11"
        />
        <Select
          aria-label="Ordenar"
          value={sort}
          onChange={(e) => setSort(e.target.value as Sort)}
          options={[
            { value: "leads", label: "Más leads primero" },
            { value: "recent", label: "Modificadas recientemente" },
            { value: "price", label: "Mayor precio" },
          ]}
          containerClassName="lg:w-60"
          className="h-11"
        />
      </div>
      <div className="mb-5">
        <Segmented
          label="Tipo de operación"
          value={operation}
          onChange={setOperation}
          options={[
            { id: "all", label: "Todas", count: crm.properties.length },
            { id: "venta", label: "Venta", count: crm.properties.filter((p) => p.operation === "venta").length },
            { id: "renta", label: "Renta", count: crm.properties.filter((p) => p.operation === "renta").length },
          ]}
        />
      </div>

      {visible.length === 0 ? (
        <StateMessage
          kind="no-results"
          title="Sin resultados"
          description="Ninguna propiedad coincide con estos filtros."
          action={
            <Button
              variant="secondary"
              onClick={() => {
                setQuery("");
                setOperation("all");
                setAdvisorId("");
              }}
            >
              Quitar filtros
            </Button>
          }
        />
      ) : (
        <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
          {visible.map((p) => {
            const leads = leadCount.get(p.id) ?? 0;
            return (
              <li key={p.id} className="min-w-0">
                <article className="group flex h-full flex-col rounded-xl border border-border bg-card p-5 shadow-[0_1px_2px_rgba(36,39,35,0.04)] transition-shadow duration-200 hover:shadow-md">
                  <div className="flex items-start justify-between gap-2">
                    <span className="font-mono text-xs font-medium tracking-wide text-muted">
                      {p.internalKey} · {p.key}
                    </span>
                    <span className="rounded-full bg-app-bg px-2.5 py-1 text-xs font-medium capitalize text-obsessed">{p.operation}</span>
                  </div>
                  <p className="mt-3 text-xl font-semibold tabular-nums text-obsessed">{propertyPrice(p)}</p>
                  <p className="text-sm font-medium text-obsessed">{p.subtype}</p>
                  <p className="mt-2 flex items-start gap-2 text-sm text-muted">
                    <MapPin className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                    <span className="line-clamp-2">
                      {p.street}, {p.neighborhood}, {p.municipality}
                    </span>
                  </p>
                  <div className="mt-4 flex items-center justify-between gap-3 border-t border-border pt-4">
                    <AdvisorChip advisor={advisorById.get(p.advisorId)} />
                    <span className={leads ? "inline-flex shrink-0 items-center gap-1 text-sm font-medium text-obsessed" : "inline-flex shrink-0 items-center gap-1 text-sm text-muted"}>
                      <Users className="h-4 w-4" aria-hidden />
                      {leads} lead{leads === 1 ? "" : "s"}
                    </span>
                  </div>
                  <div className="mt-auto flex items-center justify-between gap-2 pt-4">
                    <span className="text-xs text-muted">Modificada: {formatDate(p.updatedAt + "T12:00:00")}</span>
                    <Link
                      href={`/propiedades/${p.id}`}
                      className="inline-flex min-h-9 items-center gap-1 rounded-lg px-1 text-sm font-medium text-dark-gold hover:text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
                      aria-label={`Ver propiedad ${p.internalKey}`}
                    >
                      Ver <ArrowRight className="h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5" aria-hidden />
                    </Link>
                  </div>
                </article>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}
