"use client";

import { LoadError, StatCard, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { ExternalOperationModal } from "@/components/operations/OperationModals";
import { OperationStatusBadge, formatDay } from "@/components/operations/OperationUi";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { SearchInput } from "@/components/ui/SearchInput";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { formatMoneyWithCurrency } from "@/lib/money";
import { docStatusLabels, listOperations, operationStatusLabels, operationTypeLabels, originLabels, type DocStatus, type OperationStatus } from "@/lib/api/operations";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, CheckCircle2, ClipboardCheck, Globe2, Plus, Settings2 } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";

/**
 * Control de Cambios #32: operaciones de cierre, propias y externas. Se filtran por estado de la
 * operación, estado documental, origen, asesor y fecha de cierre; los filtros quedan en la URL.
 */
export default function OperationsPage() {
  return (
    <Suspense fallback={null}>
      <OperationsView />
    </Suspense>
  );
}

function OperationsView() {
  const can = useCan();
  const router = useRouter();
  const params = useSearchParams();
  const [creating, setCreating] = useState(false);
  const filters = {
    status: params.get("estado") ?? "",
    docStatus: params.get("documental") ?? "",
    origin: params.get("origen") ?? "",
    advisorId: params.get("asesor") ?? "",
    closedFrom: params.get("desde") ?? "",
    closedTo: params.get("hasta") ?? "",
    q: params.get("q") ?? "",
    pendingDocs: params.get("pendientes") === "1",
  };
  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params.toString());
    if (value) next.set(key, value);
    else next.delete(key);
    router.replace(`/operaciones${next.toString() ? `?${next}` : ""}`);
  };
  const result = useApi(() => listOperations(filters), [JSON.stringify(filters)]);
  const data = result.data;
  const rangeError = filters.closedFrom && filters.closedTo && filters.closedFrom > filters.closedTo ? "La fecha inicial es posterior a la final." : undefined;

  return (
    <PageContainer
      title="Operaciones y cierres"
      subtitle="Cierre comercial y revisión documental (operación, PLD y PROFECO) de inmuebles de la cartera propia y de propiedades externas."
      action={
        <>
          {can("OPERATION_MATRIX_MANAGE") || can("OPERATION_REVIEW") ? (
            <Link href="/operaciones/matriz">
              <Button variant="secondary">
                <Settings2 className="h-4 w-4" aria-hidden /> Matriz documental
              </Button>
            </Link>
          ) : null}
          {can("OPERATION_MANAGE") ? (
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" aria-hidden /> Registrar operación de propiedad externa
            </Button>
          ) : null}
        </>
      }
    >
      {data ? (
        <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatCard icon={CheckCircle2} tone="success" value={data.totals.closed} label="Cerradas" />
          <StatCard icon={AlertTriangle} tone="warning" value={data.totals.closedPendingDocs} label="Cerradas con documentación pendiente" />
          <StatCard icon={ClipboardCheck} tone="info" value={data.totals.inReview} label="En revisión de cierre" />
          <StatCard icon={Globe2} value={data.totals.external} label="Propiedades externas" />
        </div>
      ) : null}

      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <SearchInput value={filters.q} onChange={(v) => set("q", v)} placeholder="Folio, inmueble, partes o asesor" label="Buscar operaciones" />
        <Select
          aria-label="Estado de la operación"
          value={filters.status}
          onChange={(e) => set("estado", e.target.value)}
          options={[{ value: "", label: "Todos los estados" }, ...(Object.keys(operationStatusLabels) as OperationStatus[]).map((s) => ({ value: s, label: operationStatusLabels[s] }))]}
        />
        <Select
          aria-label="Estado documental"
          value={filters.docStatus}
          onChange={(e) => set("documental", e.target.value)}
          options={[{ value: "", label: "Toda revisión documental" }, ...(Object.keys(docStatusLabels) as DocStatus[]).map((s) => ({ value: s, label: `Documentación: ${docStatusLabels[s]}` }))]}
        />
        <Select
          aria-label="Origen"
          value={filters.origin}
          onChange={(e) => set("origen", e.target.value)}
          options={[
            { value: "", label: "Cartera propia y externas" },
            { value: "OWN", label: originLabels.OWN },
            { value: "EXTERNAL", label: originLabels.EXTERNAL },
          ]}
        />
        <Select
          aria-label="Asesor"
          value={filters.advisorId}
          onChange={(e) => set("asesor", e.target.value)}
          options={[{ value: "", label: "Todos los asesores" }, ...(data?.advisors ?? []).map((a) => ({ value: a.id, label: a.name }))]}
        />
        <Input label="Cerradas desde" type="date" value={filters.closedFrom} onChange={(e) => set("desde", e.target.value)} error={rangeError} />
        <Input label="Cerradas hasta" type="date" value={filters.closedTo} onChange={(e) => set("hasta", e.target.value)} />
        <label className="mt-6 inline-flex items-center gap-2 text-sm text-obsessed">
          <input type="checkbox" checked={filters.pendingDocs} onChange={(e) => set("pendientes", e.target.checked ? "1" : "")} />
          Solo con documentación pendiente
        </label>
      </div>

      {result.error && !data ? (
        <LoadError message={result.error} onRetry={result.reload} />
      ) : !data ? (
        <StateMessage kind="loading" title="Cargando operaciones…" />
      ) : data.items.length === 0 ? (
        <StateMessage kind={data.totals.total === 0 ? "empty" : "no-results"} title={data.totals.total === 0 ? "Aún no hay operaciones" : "Sin resultados"} description={data.totals.total === 0 ? "Inicia el cierre desde un expediente o registra una operación de propiedad externa." : "Ninguna operación coincide con estos filtros."} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border bg-card">
          <table className="w-full min-w-[900px] text-sm">
            <thead className="bg-app-bg text-left text-xs uppercase tracking-wide text-muted">
              <tr>
                <th className="px-4 py-3">Operación</th>
                <th className="px-4 py-3">Inmueble</th>
                <th className="px-4 py-3">Partes</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3">Cierre</th>
                <th className="px-4 py-3">Requisitos</th>
                <th className="px-4 py-3 text-right">Monto</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((o) => (
                <tr key={o.id} className="border-t border-border align-top hover:bg-app-bg/60">
                  <td className="px-4 py-3">
                    <Link href={`/operaciones/${o.id}`} className="font-semibold text-obsessed hover:underline">
                      {o.folio}
                    </Link>
                    <div className="mt-1 flex flex-wrap gap-1">
                      <Badge tone={o.origin === "EXTERNAL" ? "info" : "neutral"}>{originLabels[o.origin]}</Badge>
                      <Badge tone="neutral">{operationTypeLabels[o.operationType]}</Badge>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <p className="text-obsessed">{o.propertyDescription}</p>
                    <p className="text-xs text-muted">Asesor: {o.advisorName ?? "—"}</p>
                  </td>
                  <td className="px-4 py-3 text-xs">
                    <p>{o.sellerName ?? "—"}</p>
                    <p className="text-muted">{o.buyerName ?? "—"}</p>
                  </td>
                  <td className="px-4 py-3">
                    <OperationStatusBadge item={o} />
                  </td>
                  <td className="px-4 py-3 text-xs">
                    {o.closedOn ? (
                      <>
                        <p>{formatDay(o.closedOn)}</p>
                        <p className="text-muted">{o.closedByName}</p>
                      </>
                    ) : (
                      "—"
                    )}
                    {o.status === "CLOSED" && o.docStatus !== "COMPLETE_VALIDATED" && o.docDueDate ? (
                      <p className="mt-1 text-warning-text">
                        Compromiso {formatDay(o.docDueDate)} · {o.docFollowUpOwner}
                      </p>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 text-xs">
                    {o.requirementsSatisfied}/{o.requirementsTotal} cumplidos
                    {o.requirementsMissing > 0 ? <p className="text-warning-text">{o.requirementsMissing} por resolver</p> : null}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{o.amount != null ? formatMoneyWithCurrency(o.amount, o.currency ?? "MXN") : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ExternalOperationModal open={creating} onClose={() => setCreating(false)} onCreated={(d) => router.push(`/operaciones/${d.operation.id}`)} />
    </PageContainer>
  );
}
