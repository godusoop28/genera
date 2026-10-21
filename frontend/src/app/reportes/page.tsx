"use client";

import { Avatar, LoadError, StatCard, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { PropertyTypeFilters } from "@/components/property/PropertyTypeSelect";
import { UNASSIGNED_ADVISOR, UNCLASSIFIED_SUBTYPE, getPortfolioReport, type GroupCounts, type PortfolioReport } from "@/lib/api/expedientes";
import type { BackendOperationType } from "@/lib/api/types";
import { operationLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { ArrowRight, CheckCircle2, Clock, FolderOpen, RefreshCw, X } from "lucide-react";
import Link from "next/link";
import { Fragment, useState } from "react";

const operations = Object.keys(operationLabels) as BackendOperationType[];

type TypeRow = PortfolioReport["byTypeOperation"][number];

/** Filas del nivel 1 agrupadas por categoría, en el orden del catálogo (así llegan del servidor). */
function groupByCategory(rows: TypeRow[]) {
  const groups: { categoryId: string | null; categoryName: string; subtypes: { subtypeId: string | null; subtypeName: string; rows: TypeRow[] }[] }[] = [];
  for (const row of rows) {
    let group = groups.find((g) => g.categoryId === row.categoryId);
    if (!group) {
      group = { categoryId: row.categoryId, categoryName: row.categoryName, subtypes: [] };
      groups.push(group);
    }
    let subtype = group.subtypes.find((s) => s.subtypeId === row.subtypeId);
    if (!subtype) {
      subtype = { subtypeId: row.subtypeId, subtypeName: row.subtypeName, rows: [] };
      group.subtypes.push(subtype);
    }
    subtype.rows.push(row);
  }
  return groups;
}

/**
 * Control de Cambios #24/#26: cartera de expedientes en dos niveles. Nivel 1: categoría →
 * subcategoría del inmueble + operación (venta/renta). Nivel 2: asesor asignado. De cualquier
 * combinación se llega al listado de expedientes ya filtrado.
 */
export default function ReportesPage() {
  const can = useCan();
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [subtypeId, setSubtypeId] = useState<string | null>(null);
  const [operationType, setOperationType] = useState<BackendOperationType | null>(null);
  const [advisorId, setAdvisorId] = useState<string | null>(null);
  const report = useApi(
    () => getPortfolioReport({ categoryId, subtypeId, operationType, advisorId }),
    [categoryId, subtypeId, operationType, advisorId],
  );

  if (!can("EXPEDIENT_VIEW_ALL") && !can("EXPEDIENT_VIEW_OWN")) {
    return (
      <PageContainer title="Reportes de cartera">
        <StateMessage kind="restricted" title="Acceso restringido" description="Tu usuario no tiene permiso para consultar expedientes." />
      </PageContainer>
    );
  }

  const selectCell = (category: string | null, subtype: string | null, operation: BackendOperationType | null) => {
    const same = category === categoryId && subtype === subtypeId && operation === operationType;
    setCategoryId(same ? null : category);
    setSubtypeId(same ? null : subtype);
    setOperationType(same ? null : operation);
  };

  const listHref = (() => {
    const search = new URLSearchParams();
    if (categoryId) search.set("categoria", categoryId);
    if (subtypeId) search.set("subtipo", subtypeId);
    if (operationType) search.set("operacion", operationType);
    if (advisorId) search.set("asesor", advisorId);
    const qs = search.toString();
    return qs ? `/expedientes?${qs}` : "/expedientes";
  })();

  const rows = report.data?.byTypeOperation ?? [];
  const categoryName = categoryId ? (rows.find((r) => r.categoryId === categoryId)?.categoryName ?? "Categoría elegida") : null;
  const subtypeName =
    subtypeId === UNCLASSIFIED_SUBTYPE ? "Pendientes de clasificación" : subtypeId ? (rows.find((r) => r.subtypeId === subtypeId)?.subtypeName ?? "Subcategoría elegida") : null;
  const typeText = [categoryName, subtypeName].filter(Boolean).join(" → ");
  const selection = [
    typeText || null,
    operationType ? operationLabels[operationType] : null,
    advisorId ? (report.data?.byAdvisor.find((a) => (a.advisorId ?? UNASSIGNED_ADVISOR) === advisorId)?.name ?? "Asesor") : null,
  ].filter(Boolean);

  return (
    <PageContainer
      title="Reportes de cartera"
      subtitle={
        can("EXPEDIENT_VIEW_ALL")
          ? "Expedientes por categoría y subcategoría del inmueble, operación y asesor asignado. Elige una combinación para ver sus expedientes."
          : "Tus expedientes asignados por tipo de inmueble y operación."
      }
    >
      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:max-w-2xl" role="group" aria-label="Filtrar por tipo de inmueble">
        <PropertyTypeFilters
          categoryId={categoryId}
          subtypeId={subtypeId}
          onChange={(v) => {
            setCategoryId(v.categoryId);
            setSubtypeId(v.subtypeId);
          }}
        />
      </div>
      {report.error && !report.data ? (
        <LoadError message={report.error} onRetry={report.reload} />
      ) : !report.data ? (
        <StateMessage kind="loading" title="Cargando la cartera…" />
      ) : (
        <ReportContent
          data={report.data}
          categoryId={categoryId}
          subtypeId={subtypeId}
          operationType={operationType}
          advisorId={advisorId}
          typeText={typeText}
          onSelectCell={selectCell}
          onSelectAdvisor={(id) => setAdvisorId((current) => (current === id ? null : id))}
          selection={selection as string[]}
          onClear={() => {
            setCategoryId(null);
            setSubtypeId(null);
            setOperationType(null);
            setAdvisorId(null);
          }}
          listHref={listHref}
          refreshing={report.error !== null}
          onReload={report.reload}
        />
      )}
    </PageContainer>
  );
}

function ReportContent({
  data,
  categoryId,
  subtypeId,
  operationType,
  advisorId,
  typeText,
  onSelectCell,
  onSelectAdvisor,
  selection,
  onClear,
  listHref,
  refreshing,
  onReload,
}: {
  data: PortfolioReport;
  categoryId: string | null;
  subtypeId: string | null;
  operationType: BackendOperationType | null;
  advisorId: string | null;
  typeText: string;
  onSelectCell: (category: string | null, subtype: string | null, operation: BackendOperationType | null) => void;
  onSelectAdvisor: (id: string) => void;
  selection: string[];
  onClear: () => void;
  listHref: string;
  refreshing: boolean;
  onReload: () => void;
}) {
  const groups = groupByCategory(data.byTypeOperation);
  const sum = (rows: TypeRow[], operation?: BackendOperationType) =>
    rows.filter((r) => !operation || r.operationType === operation).reduce((total, r) => total + r.counts.total, 0);
  const allRows = data.byTypeOperation;
  const grandTotal = sum(allRows);
  const maxAdvisor = Math.max(1, ...data.byAdvisor.map((a) => a.counts.total));
  const subtypeKey = (id: string | null) => id ?? UNCLASSIFIED_SUBTYPE;

  return (
    <div className="space-y-5">
      {refreshing ? (
        <p role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-danger-text/20 bg-danger-bg px-4 py-3 text-sm text-danger-text">
          No se pudo actualizar el reporte.
          <button type="button" onClick={onReload} className="inline-flex items-center gap-1 font-medium underline">
            <RefreshCw className="h-3.5 w-3.5" aria-hidden /> Reintentar
          </button>
        </p>
      ) : null}

      <div className="flex flex-col gap-3 rounded-xl border border-border bg-card px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-obsessed">
          {selection.length === 0 ? (
            "Toda la cartera"
          ) : (
            <>
              Filtro: <strong className="font-semibold">{selection.join(" · ")}</strong>
            </>
          )}
        </p>
        <div className="flex shrink-0 flex-wrap gap-2">
          {selection.length > 0 ? (
            <button
              type="button"
              onClick={onClear}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-border px-3 py-1.5 text-sm font-medium text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
            >
              <X className="h-4 w-4" aria-hidden /> Quitar filtros
            </button>
          ) : null}
          <Link
            href={listHref}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-gold px-3 py-1.5 text-sm font-medium text-brand-ink hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
          >
            Ver {data.total.total} expediente{data.total.total === 1 ? "" : "s"} <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        </div>
      </div>

      <section aria-label="Indicadores" className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <StatCard icon={FolderOpen} value={data.total.total} label="Expedientes" hint="Con el filtro elegido" />
        <StatCard icon={Clock} tone="warning" value={data.total.pending} label="Pendientes" hint="Esperan algo del cliente" />
        <StatCard icon={RefreshCw} tone="info" value={data.total.inReview} label="En revisión" hint="Documentos por revisar" />
        <StatCard icon={CheckCircle2} tone="success" value={data.total.complete} label="Completos" hint="Documentación aprobada" />
      </section>

      <Card>
        <CardHeader
          title="Nivel 1 · Tipo de inmueble y operación"
          description="Categorías con sus subcategorías. Elige una celda para filtrar; vuelve a tocarla para quitar el filtro."
        />
        {groups.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin expedientes con este filtro</p>
        ) : (
          <div className="-mx-1 overflow-x-auto px-1">
            <table className="w-full min-w-[420px] border-separate border-spacing-1.5 text-sm">
              <thead>
                <tr>
                  <th scope="col" className="px-2 py-1 text-left text-xs font-medium uppercase tracking-wide text-muted">
                    Tipo de inmueble
                  </th>
                  {operations.map((op) => (
                    <th key={op} scope="col" className="px-2 py-1 text-center text-xs font-medium uppercase tracking-wide text-muted">
                      <CellButton
                        active={categoryId === null && subtypeId === null && operationType === op}
                        onClick={() => onSelectCell(null, null, op)}
                        label={`Todos los inmuebles en ${operationLabels[op].toLowerCase()}`}
                      >
                        {operationLabels[op]}
                      </CellButton>
                    </th>
                  ))}
                  <th scope="col" className="px-2 py-1 text-center text-xs font-medium uppercase tracking-wide text-muted">
                    Total
                  </th>
                </tr>
              </thead>
              <tbody>
                {groups.map((group) => {
                  const groupRows = group.subtypes.flatMap((s) => s.rows);
                  return (
                    <Fragment key={group.categoryId ?? "none"}>
                      <tr>
                        <th scope="row" className="pt-2 text-left font-semibold text-obsessed">
                          <CellButton
                            active={categoryId === group.categoryId && subtypeId === null && operationType === null}
                            onClick={() => onSelectCell(group.categoryId, null, null)}
                            label={`${group.categoryName} en venta y renta`}
                            align="left"
                          >
                            {group.categoryName}
                          </CellButton>
                        </th>
                        {operations.map((op) => (
                          <td key={op} className="pt-2 text-center">
                            <CellButton
                              active={categoryId === group.categoryId && subtypeId === null && operationType === op}
                              onClick={() => onSelectCell(group.categoryId, null, op)}
                              label={`${group.categoryName} en ${operationLabels[op].toLowerCase()}: ${sum(groupRows, op)}`}
                              muted={sum(groupRows, op) === 0}
                            >
                              <span className="font-semibold tabular-nums">{sum(groupRows, op)}</span>
                            </CellButton>
                          </td>
                        ))}
                        <td className="pt-2 text-center font-semibold tabular-nums text-obsessed">{sum(groupRows)}</td>
                      </tr>
                      {group.subtypes.map((subtype) => {
                        const key = subtypeKey(subtype.subtypeId);
                        const label = `${group.categoryName} → ${subtype.subtypeName}`;
                        return (
                          <tr key={key}>
                            <th scope="row" className="text-left font-normal text-obsessed">
                              <CellButton
                                active={subtypeId === key && categoryId === group.categoryId && operationType === null}
                                onClick={() => onSelectCell(group.categoryId, key, null)}
                                label={`${label} en venta y renta`}
                                align="left"
                              >
                                <span className={cn("pl-4", subtype.subtypeId === null && "italic text-muted")}>{subtype.subtypeName}</span>
                              </CellButton>
                            </th>
                            {operations.map((op) => {
                              const n = sum(subtype.rows, op);
                              return (
                                <td key={op} className="text-center">
                                  <CellButton
                                    active={subtypeId === key && categoryId === group.categoryId && operationType === op}
                                    onClick={() => onSelectCell(group.categoryId, key, op)}
                                    label={`${label} en ${operationLabels[op].toLowerCase()}: ${n}`}
                                    filled
                                    muted={n === 0}
                                  >
                                    <span className="text-lg font-semibold tabular-nums">{n}</span>
                                  </CellButton>
                                </td>
                              );
                            })}
                            <td className="text-center text-base font-semibold tabular-nums text-obsessed">{sum(subtype.rows)}</td>
                          </tr>
                        );
                      })}
                    </Fragment>
                  );
                })}
                <tr>
                  <th scope="row" className="px-2 pt-2 text-left text-xs font-medium uppercase tracking-wide text-muted">
                    Total
                  </th>
                  {operations.map((op) => (
                    <td key={op} className="pt-2 text-center text-base font-semibold tabular-nums text-obsessed">
                      {sum(allRows, op)}
                    </td>
                  ))}
                  <td className="pt-2 text-center text-base font-semibold tabular-nums text-obsessed">{grandTotal}</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <CardHeader
          title="Nivel 2 · Asesor asignado"
          description={
            typeText || operationType
              ? `Expedientes de ${[typeText || "todos los inmuebles", operationType ? `en ${operationLabels[operationType].toLowerCase()}` : ""].join(" ").trim()} por asesor.`
              : "Expedientes por asesor. Elige un asesor para filtrar."
          }
        />
        {data.byAdvisor.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin expedientes con este filtro</p>
        ) : (
          <ul className="divide-y divide-border">
            {data.byAdvisor.map((row) => {
              const id = row.advisorId ?? UNASSIGNED_ADVISOR;
              const active = advisorId === id;
              return (
                <li key={id}>
                  <button
                    type="button"
                    onClick={() => onSelectAdvisor(id)}
                    aria-pressed={active}
                    className={cn(
                      "grid w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 rounded-lg px-2 py-3 text-left transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                      active ? "bg-gold/15" : "hover:bg-app-bg",
                    )}
                  >
                    <Avatar name={row.name} className={cn("h-8 w-8 text-[0.7rem]", row.advisorId === null && "bg-app-bg text-muted")} />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium text-obsessed">{row.name}</span>
                      <GroupBreakdown counts={row.counts} />
                      <span className="mt-1.5 block h-1.5 overflow-hidden rounded-full bg-app-bg">
                        <span className="block h-full rounded-full bg-gold" style={{ width: `${(row.counts.total / maxAdvisor) * 100}%` }} />
                      </span>
                    </span>
                    <span className="text-lg font-semibold tabular-nums text-obsessed">{row.counts.total}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </Card>
    </div>
  );
}

function GroupBreakdown({ counts }: { counts: GroupCounts }) {
  if (counts.total === 0) return <span className="block text-xs text-muted">Sin expedientes</span>;
  return (
    <span className="block text-xs text-muted">
      {counts.pending} pendiente{counts.pending === 1 ? "" : "s"} · {counts.inReview} en revisión · {counts.complete} completo{counts.complete === 1 ? "" : "s"}
    </span>
  );
}

function CellButton({
  active,
  onClick,
  label,
  children,
  filled = false,
  muted = false,
  align = "center",
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  children: React.ReactNode;
  filled?: boolean;
  muted?: boolean;
  align?: "left" | "center";
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      aria-label={label}
      title={label}
      className={cn(
        "w-full rounded-lg px-2 py-2 transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
        align === "left" ? "text-left" : "text-center",
        filled && "border border-border",
        active ? "border-gold bg-gold text-brand-ink" : filled ? (muted ? "text-muted hover:border-gold/60" : "bg-card text-obsessed hover:border-gold/60") : "hover:bg-app-bg",
      )}
    >
      {children}
    </button>
  );
}
