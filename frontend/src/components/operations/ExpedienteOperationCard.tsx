"use client";

import { OperationStatusBadge, formatDay } from "@/components/operations/OperationUi";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { useToast } from "@/components/ui/Toast";
import { errorText } from "@/lib/errors";
import { createOperation, operationsOfExpediente, representedLabel, type OperationItem, type OperationType, type RepresentedSide } from "@/lib/api/operations";
import { useCan } from "@/lib/permissions";
import { ArrowRight, Handshake } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

/**
 * CC#32: desde el expediente se inicia el cierre de la operación del inmueble de la cartera propia.
 * El titular del expediente se vincula como vendedor (o arrendador) y se piden los papeles de cada
 * parte según la matriz documental, sin volver a pedir lo que ya está en el expediente.
 */
export function ExpedienteOperationCard({ expedienteId, operationType }: { expedienteId: string; operationType: OperationType }) {
  const can = useCan();
  const router = useRouter();
  const { showToast } = useToast();
  const [items, setItems] = useState<OperationItem[] | null>(null);
  const [side, setSide] = useState<RepresentedSide>("SELLER");
  const [busy, setBusy] = useState(false);
  const visible = can("OPERATION_VIEW_ALL") || can("OPERATION_MANAGE") || can("OPERATION_REVIEW");

  useEffect(() => {
    if (!visible) return;
    operationsOfExpediente(expedienteId)
      .then(setItems)
      .catch(() => setItems([]));
  }, [expedienteId, visible]);

  if (!visible) return null;
  const active = items?.find((o) => o.status !== "CANCELLED");

  return (
    <Card>
      <CardHeader
        title="Cierre de la operación"
        description="Estado de la operación (compraventa o arrendamiento) y su revisión documental de la operación, PLD y PROFECO."
      />
      {items === null ? <p className="text-sm text-muted">Cargando…</p> : null}
      {items?.map((o) => (
        <Link key={o.id} href={`/operaciones/${o.id}`} className="mb-2 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border p-3 hover:bg-app-bg">
          <div>
            <p className="font-medium text-obsessed">{o.folio}</p>
            <p className="text-xs text-muted">
              {o.status === "CLOSED" ? `Cerrada el ${formatDay(o.closedOn)} · ${o.closedByName ?? ""}` : `${o.requirementsSatisfied}/${o.requirementsTotal} requisitos cumplidos`}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <OperationStatusBadge item={o} />
            <ArrowRight className="h-4 w-4 text-muted" aria-hidden />
          </div>
        </Link>
      ))}
      {items && !active && can("OPERATION_MANAGE") ? (
        <div className="flex flex-wrap items-end gap-3">
          <Select
            label="C21 Genera representa a"
            value={side}
            onChange={(e) => setSide(e.target.value as RepresentedSide)}
            options={(["SELLER", "BUYER", "BOTH"] as RepresentedSide[]).map((s) => ({ value: s, label: representedLabel(operationType, s) }))}
            containerClassName="sm:w-64"
          />
          <Button
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                const created = await createOperation({ origin: "OWN", expedienteId, operationType, representedSide: side });
                router.push(`/operaciones/${created.operation.id}`);
              } catch (err) {
                showToast(errorText(err));
                setBusy(false);
              }
            }}
          >
            <Handshake className="h-4 w-4" aria-hidden /> Iniciar cierre
          </Button>
        </div>
      ) : null}
    </Card>
  );
}
