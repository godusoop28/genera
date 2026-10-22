"use client";

import { LoadError, useApi } from "@/components/crm/CrmUi";
import { PropertyKeyModal } from "@/components/expediente/PropertyKeyField";
import { PageContainer } from "@/components/layout/PageContainer";
import { PropertyTypeFilters } from "@/components/property/PropertyTypeSelect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { getPropertyKeys, listAssignableAdvisors, type AdvisorOption, type PropertyKeyOverview } from "@/lib/api/expedientes";
import type { BackendOperationType } from "@/lib/api/types";
import { operationLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { KeyRound, Pencil } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

const pad = (n: number) => String(n).padStart(2, "0");

type Editing = { id: string; folio: string; propertyKey: string | null; suggestion: string | null };

/**
 * Control de Cambios #27 (adenda): claves estructuradas en uso, filtrables por tipo, operación y
 * asesor; consecutivos en uso y disponibles por combinación; y los expedientes que aún no tienen
 * clave, para migrarlos uno por uno con la clave que se capture (se valida antes de guardar).
 */
export default function ClavesPage() {
  const can = useCan();
  const { showToast } = useToast();
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [subtypeId, setSubtypeId] = useState<string | null>(null);
  const [operationType, setOperationType] = useState<BackendOperationType | null>(null);
  const [advisorId, setAdvisorId] = useState<string | null>(null);
  const [prefix, setPrefix] = useState("");
  const [debouncedPrefix, setDebouncedPrefix] = useState("");
  const [advisors, setAdvisors] = useState<AdvisorOption[]>([]);
  const [editing, setEditing] = useState<Editing | null>(null);
  const allowed = can("EXPEDIENT_KEY_ASSIGN");

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedPrefix(prefix.trim().toUpperCase()), 300);
    return () => clearTimeout(timer);
  }, [prefix]);

  useEffect(() => {
    if (!allowed) return;
    listAssignableAdvisors()
      .then(setAdvisors)
      .catch(() => setAdvisors([]));
  }, [allowed]);

  const data = useApi(
    () => (allowed ? getPropertyKeys({ categoryId, subtypeId, operationType, advisorId, prefix: debouncedPrefix }) : Promise.resolve(null)),
    [allowed, categoryId, subtypeId, operationType, advisorId, debouncedPrefix],
  );

  if (!allowed) {
    return (
      <PageContainer title="Claves de inmuebles">
        <StateMessage kind="restricted" title="Acceso restringido" description="Solo el administrador o el directivo consultan y asignan claves." />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="Claves de inmuebles"
      subtitle="Tipo + operación + iniciales del asesor + consecutivo (p. ej. CVEA03). Consulta qué consecutivos están en uso y asigna la clave a las propiedades que aún no la tienen."
    >
      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5" role="group" aria-label="Filtros">
        <PropertyTypeFilters
          categoryId={categoryId}
          subtypeId={subtypeId}
          onChange={(v) => {
            setCategoryId(v.categoryId);
            setSubtypeId(v.subtypeId);
          }}
        />
        <Select
          aria-label="Filtrar por operación"
          value={operationType ?? ""}
          onChange={(e) => setOperationType((e.target.value || null) as BackendOperationType | null)}
          options={[{ value: "", label: "Venta y renta" }, ...(Object.keys(operationLabels) as BackendOperationType[]).map((op) => ({ value: op, label: operationLabels[op] }))]}
        />
        <Select
          aria-label="Filtrar por asesor"
          value={advisorId ?? ""}
          onChange={(e) => setAdvisorId(e.target.value || null)}
          options={[{ value: "", label: "Todos los asesores" }, ...advisors.map((a) => ({ value: a.id, label: a.name }))]}
        />
        <Input
          aria-label="Consultar una combinación"
          placeholder="Combinación, p. ej. CVEA"
          value={prefix}
          maxLength={18}
          className="font-mono uppercase"
          onChange={(e) => setPrefix(e.target.value.toUpperCase().replace(/[^A-Z]/g, ""))}
        />
      </div>

      {data.error && !data.data ? (
        <LoadError message={data.error} onRetry={data.reload} />
      ) : !data.data ? (
        <StateMessage kind="loading" title="Cargando claves…" />
      ) : (
        <KeysContent
          data={data.data}
          onEdit={(e) => setEditing(e)}
        />
      )}

      {editing ? (
        <PropertyKeyModal
          expediente={editing}
          suggestion={editing.suggestion}
          onClose={() => setEditing(null)}
          onSaved={(updated) => {
            setEditing(null);
            showToast(`Clave ${updated.propertyKey} asignada a ${updated.folio}.`);
            data.reload();
          }}
        />
      ) : null}
    </PageContainer>
  );
}

function KeysContent({ data, onEdit }: { data: PropertyKeyOverview; onEdit: (e: Editing) => void }) {
  return (
    <div className="space-y-5">
      <Card>
        <CardHeader
          title="Consecutivos por combinación"
          description="En uso, huecos libres por debajo del más alto y el siguiente que se asignará automáticamente en nuevas altas."
        />
        {data.combinations.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin claves con este filtro</p>
        ) : (
          <div className="-mx-1 overflow-x-auto px-1">
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className="text-left text-xs font-medium uppercase tracking-wide text-muted">
                  <th scope="col" className="px-2 py-2">Combinación</th>
                  <th scope="col" className="px-2 py-2">En uso</th>
                  <th scope="col" className="px-2 py-2">Disponibles</th>
                  <th scope="col" className="px-2 py-2">Siguiente automática</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.combinations.map((c) => (
                  <tr key={c.prefix}>
                    <th scope="row" className="px-2 py-2.5 text-left font-mono font-semibold text-obsessed">
                      {c.prefix}
                    </th>
                    <td className="px-2 py-2.5 font-mono text-xs text-obsessed">
                      {c.used.length === 0 ? <span className="text-muted">Ninguno</span> : c.used.map((n) => `${c.prefix}${pad(n)}`).join(", ")}
                    </td>
                    <td className="px-2 py-2.5 font-mono text-xs text-success-text">
                      {[...c.gaps.map(pad), c.nextKey ? `${c.nextKey.slice(c.prefix.length)}…` : null].filter(Boolean).join(", ")}
                    </td>
                    <td className="px-2 py-2.5 font-mono text-xs font-semibold text-obsessed">{c.nextKey ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <CardHeader
          title={`Expedientes sin clave (${data.withoutKey.length})`}
          description="Propiedades registradas antes de la clave estructurada. Asigna a cada una su clave; la sugerida es el siguiente consecutivo de su combinación, pero puedes capturar otra."
        />
        {data.withoutKey.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Todos los expedientes de este filtro ya tienen clave.</p>
        ) : (
          <ul className="divide-y divide-border">
            {data.withoutKey.map((e) => (
              <li key={e.expedienteId} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-obsessed">
                    <Link href={`/expedientes/${e.expedienteId}`} className="font-mono text-xs text-muted hover:underline">
                      {e.folio}
                    </Link>{" "}
                    {e.ownerDisplayName}
                    {e.archivedAt ? (
                      <Badge tone="neutral" className="ml-2">
                        Archivado
                      </Badge>
                    ) : null}
                  </p>
                  <p className="truncate text-xs text-muted">
                    {[e.propertyTypeLabel ?? "Tipo sin clasificar", operationLabels[e.operationType], e.advisorName ?? "Sin asesor", e.property].filter(Boolean).join(" · ")}
                  </p>
                  {!e.suggestedKey ? (
                    <p className="text-xs text-warning-text">Sin sugerencia: su tipo de inmueble no tiene letras de clave o no tiene asesor asignado.</p>
                  ) : null}
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {e.suggestedKey ? <span className="font-mono text-xs text-muted">Sugerida: {e.suggestedKey}</span> : null}
                  <Button size="sm" onClick={() => onEdit({ id: e.expedienteId, folio: e.folio, propertyKey: null, suggestion: e.suggestedKey })}>
                    <KeyRound className="h-4 w-4" aria-hidden /> Asignar
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <CardHeader title={`Claves asignadas (${data.assigned.length})`} description="Listado completo con el expediente al que pertenece cada clave." />
        {data.assigned.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin claves con este filtro</p>
        ) : (
          <div className="-mx-1 overflow-x-auto px-1">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="text-left text-xs font-medium uppercase tracking-wide text-muted">
                  <th scope="col" className="px-2 py-2">Clave</th>
                  <th scope="col" className="px-2 py-2">Expediente</th>
                  <th scope="col" className="px-2 py-2">Tipo · operación</th>
                  <th scope="col" className="px-2 py-2">Asesor</th>
                  <th scope="col" className="px-2 py-2">
                    <span className="sr-only">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.assigned.map((a) => (
                  <tr key={a.expedienteId}>
                    <th scope="row" className="px-2 py-2.5 text-left font-mono font-semibold text-obsessed">
                      {a.key}
                    </th>
                    <td className="px-2 py-2.5">
                      <Link href={`/expedientes/${a.expedienteId}`} className="font-mono text-xs text-muted hover:underline">
                        {a.folio}
                      </Link>
                      <span className="block text-obsessed">{a.ownerDisplayName}</span>
                      {a.property ? <span className="block truncate text-xs text-muted">{a.property}</span> : null}
                    </td>
                    <td className="px-2 py-2.5 text-xs text-muted">
                      {a.propertyTypeLabel ?? "Sin clasificar"} · {operationLabels[a.operationType]}
                    </td>
                    <td className="px-2 py-2.5 text-xs text-muted">{a.advisorName ?? "Sin asesor"}</td>
                    <td className="px-2 py-2.5 text-right">
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-label={`Corregir clave ${a.key}`}
                        onClick={() => onEdit({ id: a.expedienteId, folio: a.folio, propertyKey: a.key, suggestion: null })}
                      >
                        <Pencil className="h-3.5 w-3.5" aria-hidden />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
