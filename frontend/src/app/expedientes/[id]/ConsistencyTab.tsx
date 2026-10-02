"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { useToast } from "@/components/ui/Toast";
import { getDataConflicts, resolveConflict, runConsistencyCheck } from "@/lib/api/extraction";
import type { DataConflictResponse } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { RefreshCw, ScanSearch } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

export function ConsistencyTab({ expediente, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [conflicts, setConflicts] = useState<DataConflictResponse[] | null>(null);
  const [running, setRunning] = useState(false);
  const [resolving, setResolving] = useState<DataConflictResponse | null>(null);

  const load = useCallback(() => {
    getDataConflicts(expediente.id)
      .then(setConflicts)
      .catch((err) => showToast(errorText(err)));
  }, [expediente.id, showToast]);

  useEffect(() => {
    load();
  }, [load]);

  const severityOrder = { CRITICAL: 0, WARNING: 1, INFO: 2 } as const;
  const open = (conflicts ?? [])
    .filter((c) => !c.resolved)
    .sort((a, b) => severityOrder[a.severity ?? "WARNING"] - severityOrder[b.severity ?? "WARNING"]);
  const criticalCount = open.filter((c) => c.severity === "CRITICAL").length;
  const closed = (conflicts ?? []).filter((c) => c.resolved);

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Consistencia entre documentos"
          description="Compara nombres, CURP, RFC, propietarios, domicilio, clave catastral, folio real y superficies entre escritura, predial, plano, identificaciones y los datos capturados. Cada diferencia es una POSIBLE inconsistencia para ayudarte a revisar: solo las críticas (evidencia fuerte de otra persona u otro inmueble) impiden enviar el contrato a firma."
        />
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Badge tone={open.length === 0 ? "success" : criticalCount > 0 ? "danger" : "warning"}>
            {open.length === 0
              ? "Sin diferencias pendientes"
              : criticalCount > 0
                ? `${criticalCount} crítica(s) · ${open.length} por revisar`
                : `${open.length} posible(s) inconsistencia(s), ninguna crítica`}
          </Badge>
          <Button
            variant="secondary"
            size="sm"
            disabled={running}
            onClick={async () => {
              setRunning(true);
              try {
                setConflicts(await runConsistencyCheck(expediente.id));
                showToast("Comparación actualizada.");
              } catch (err) {
                showToast(errorText(err));
              } finally {
                setRunning(false);
              }
            }}
          >
            {running ? <RefreshCw className="h-4 w-4 animate-spin" aria-hidden /> : <ScanSearch className="h-4 w-4" aria-hidden />}
            Volver a comparar
          </Button>
        </div>
        <p className="mb-3 text-xs text-muted">
          La comparación usa los datos que se leen automáticamente de cada documento. Si la lectura automática no está disponible, revisa los
          documentos visualmente.
        </p>
        {conflicts === null ? <p className="text-sm text-muted">Cargando…</p> : null}
        <ul className="flex flex-col gap-2">
          {open.map((c) => (
            <li
              key={c.id}
              className={`rounded-lg border px-3 py-2 text-sm ${
                c.severity === "CRITICAL"
                  ? "border-danger-text/30 bg-danger-bg"
                  : c.severity === "INFO"
                    ? "border-border bg-app-bg"
                    : "border-warning-text/30 bg-warning-bg"
              }`}
            >
              <div className="mb-1">
                <Badge tone={c.severity === "CRITICAL" ? "danger" : c.severity === "INFO" ? "neutral" : "warning"}>
                  {c.severity === "CRITICAL" ? "Crítica: bloquea la firma" : c.severity === "INFO" ? "Probablemente igual" : "Revisar"}
                </Badge>
              </div>
              <p className={c.severity === "CRITICAL" ? "text-danger-text" : c.severity === "INFO" ? "text-muted" : "text-warning-text"}>
                {c.description}
              </p>
              <div className="mt-2 flex items-center justify-between gap-2">
                <span className="text-xs text-muted">Detectada {formatDateTime(c.detectedAt)}</span>
                {can("EXTRACTED_DATA_EDIT") ? (
                  <Button size="sm" variant="secondary" onClick={() => setResolving(c)}>
                    Marcar como revisada
                  </Button>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      </Card>

      {closed.length > 0 ? (
        <Card>
          <CardHeader title="Diferencias resueltas" />
          <ul className="flex flex-col gap-2 text-sm">
            {closed.map((c) => (
              <li key={c.id} className="rounded-lg border border-border px-3 py-2">
                <p className="text-obsessed">{c.description}</p>
                <p className="mt-1 text-xs text-muted">
                  {c.resolvedAutomatically ? "Se cerró sola: los datos ya coinciden." : `Resuelta: ${c.resolutionNote ?? ""}`} ·{" "}
                  {formatDateTime(c.resolvedAt)}
                </p>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <ReasonModal
        open={resolving !== null}
        title="Marcar diferencia como revisada"
        description={
          <>
            <p className="mb-2">{resolving?.description}</p>
            <p>Explica por qué es aceptable (p. ej. &quot;el predial está a nombre de uno de los copropietarios&quot;) o corrige el dato o el documento y vuelve a comparar.</p>
          </>
        }
        label="Explicación"
        minLength={10}
        onCancel={() => setResolving(null)}
        onConfirm={async (note) => {
          if (!resolving) return;
          try {
            await resolveConflict(resolving.id, note);
            showToast("Diferencia marcada como revisada.");
            setResolving(null);
            load();
            await reload();
          } catch (err) {
            showToast(errorText(err));
          }
        }}
      />
    </div>
  );
}
