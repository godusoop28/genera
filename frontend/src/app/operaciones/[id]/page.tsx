"use client";

import { LoadError, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { CloseOperationModal, EditOperationModal } from "@/components/operations/OperationModals";
import { RequirementRow } from "@/components/operations/RequirementRow";
import { OperationStatusBadge, formatDay, formatMoment } from "@/components/operations/OperationUi";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { errorText } from "@/lib/errors";
import { formatMoneyWithCurrency } from "@/lib/money";
import {
  applyCurrentMatrix,
  authorizeOperation,
  cancelOperation,
  getOperation,
  moveToClosingReview,
  operationTypeLabels,
  originLabels,
  partyLabel,
  reopenDocReview,
  reopenOperation,
  representedLabel,
  sectionLabels,
  type OperationDetail,
  type PartySide,
  type RequirementSection,
} from "@/lib/api/operations";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, ArrowLeft, CheckCircle2, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { use, useState } from "react";

const sections: RequirementSection[] = ["OPERATION", "PLD", "PROFECO"];

export default function OperationPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const can = useCan();
  const { showToast } = useToast();
  const result = useApi(() => getOperation(id), [id]);
  const [section, setSection] = useState<RequirementSection>("OPERATION");
  const [modal, setModal] = useState<null | "edit" | "close" | "cancel" | "reopen" | "reopen-review">(null);
  const [busy, setBusy] = useState(false);
  const d = result.data;

  if (result.error && !d) return <PageContainer title="Operación"><LoadError message={result.error} onRetry={result.reload} /></PageContainer>;
  if (!d) return <PageContainer title="Operación"><StateMessage kind="loading" title="Cargando operación…" /></PageContainer>;

  const op = d.operation;
  const canManage = can("OPERATION_MANAGE") && op.status !== "CANCELLED";
  const canReview = can("OPERATION_REVIEW");
  const locked = op.docStatus === "COMPLETE_VALIDATED";
  const apply = (next: OperationDetail) => result.setData(next);

  const run = async (action: () => Promise<OperationDetail>, ok: string) => {
    setBusy(true);
    try {
      apply(await action());
      showToast(ok);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const bySection = d.requirements.filter((r) => r.section === section);
  const groups: Array<{ key: PartySide | "OPERATION"; title: string }> = [
    { key: "SELLER", title: partyLabel(op.operationType, "SELLER") },
    { key: "BUYER", title: partyLabel(op.operationType, "BUYER") },
    { key: "OPERATION", title: "De la operación" },
  ];

  return (
    <PageContainer
      title={`${op.folio} · ${op.propertyDescription}`}
      subtitle={
        <span className="flex flex-wrap items-center gap-2">
          <Link href="/operaciones" className="inline-flex items-center gap-1 text-dark-gold hover:underline">
            <ArrowLeft className="h-4 w-4" aria-hidden /> Operaciones
          </Link>
          <span>·</span>
          <span>
            {originLabels[op.origin]} · {operationTypeLabels[op.operationType]} · C21 Genera representa: {representedLabel(op.operationType, op.representedSide)}
          </span>
          {op.expedienteId ? (
            <Link href={`/expedientes/${op.expedienteId}`} className="text-dark-gold hover:underline">
              Expediente {d.expedienteFolio}
            </Link>
          ) : null}
        </span>
      }
      action={
        <>
          {canManage ? (
            <Button variant="secondary" onClick={() => setModal("edit")} disabled={busy}>
              Editar datos y partes
            </Button>
          ) : null}
          {canManage && op.status === "IN_PROCESS" ? (
            <Button variant="secondary" onClick={() => run(() => moveToClosingReview(op.id), "En revisión de cierre.")} disabled={busy}>
              Pasar a revisión de cierre
            </Button>
          ) : null}
          {canManage && (op.status === "IN_PROCESS" || op.status === "CLOSING_REVIEW") ? (
            <Button onClick={() => setModal("close")} disabled={busy}>
              Registrar cierre
            </Button>
          ) : null}
          {canReview && (op.status === "CLOSED" || op.status === "CANCELLED") ? (
            <Button variant="secondary" onClick={() => setModal("reopen")} disabled={busy}>
              Reabrir operación
            </Button>
          ) : null}
          {canManage && op.status !== "CLOSED" ? (
            <Button variant="danger" onClick={() => setModal("cancel")} disabled={busy}>
              Cancelar
            </Button>
          ) : null}
        </>
      }
    >
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Estado" />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted">Operación</p>
              <OperationStatusBadge item={op} />
              {op.status === "CLOSED" ? (
                <p className="mt-2 text-sm">
                  Cerrada el <strong>{formatDay(op.closedOn)}</strong> · responsable {op.closedByName}
                </p>
              ) : null}
              {op.status === "CANCELLED" && d.cancelReason ? <p className="mt-2 text-sm text-muted">Motivo: {d.cancelReason}</p> : null}
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-muted">Monto</p>
              <p className="text-lg font-semibold tabular-nums">{op.amount != null ? formatMoneyWithCurrency(op.amount, op.currency ?? "MXN") : "—"}</p>
              <p className="text-xs text-muted">Asesor responsable: {op.advisorName ?? "—"}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide text-muted">Revisión documental</p>
              <p className="text-lg font-semibold">
                {d.summary.satisfied}/{d.summary.total} cumplidos
              </p>
              {locked ? (
                <p className="text-xs text-success-text">
                  Completa y validada por {d.docAuthorizedByName} · {formatMoment(d.docAuthorizedAt)}
                </p>
              ) : op.docDueDate ? (
                <p className="text-xs text-warning-text">
                  Compromiso {formatDay(op.docDueDate)} · {op.docFollowUpOwner}
                </p>
              ) : null}
            </div>
          </div>
          {op.origin === "EXTERNAL" ? (
            <dl className="mt-4 grid grid-cols-1 gap-2 border-t border-border pt-4 text-sm sm:grid-cols-2">
              <Info label="Origen de la propiedad" value={d.externalSource} />
              <Info label="Oficina o tercero participante" value={d.externalOffice} />
              <Info label="Contacto del tercero" value={d.externalContact} />
              <Info label="Participación de C21 Genera" value={d.generaParticipation} />
              <Info label="Domicilio" value={d.propertyAddress} />
            </dl>
          ) : (
            <dl className="mt-4 grid grid-cols-1 gap-2 border-t border-border pt-4 text-sm sm:grid-cols-2">
              <Info label="Domicilio" value={d.propertyAddress} />
              <Info label="Participación de C21 Genera" value={d.generaParticipation} />
            </dl>
          )}
        </Card>

        <Card>
          <CardHeader title="Faltantes y observaciones" />
          <ul className="flex flex-col gap-1 text-sm">
            <li>
              {partyLabel(op.operationType, "SELLER")}: <strong>{d.summary.missingBySide.SELLER ?? 0}</strong> por resolver
            </li>
            <li>
              {partyLabel(op.operationType, "BUYER")}: <strong>{d.summary.missingBySide.BUYER ?? 0}</strong> por resolver
            </li>
            <li>
              De la operación: <strong>{d.summary.missingBySide.OPERATION ?? 0}</strong> por resolver
            </li>
            <li className={cn(d.summary.needsUpdate > 0 && "text-warning-text")}>Requieren actualización (vencidos): {d.summary.needsUpdate}</li>
            <li className={cn(d.summary.needsCorrection > 0 && "text-danger-text")}>Requieren corrección: {d.summary.needsCorrection}</li>
            <li className={cn(d.summary.pendingNaApprovals > 0 && "text-warning-text")}>&quot;No aplica&quot; por validar: {d.summary.pendingNaApprovals}</li>
          </ul>
          {!d.matrixApproved ? (
            <p className="mt-3 flex items-start gap-2 rounded-lg bg-warning-bg px-3 py-2 text-xs text-warning-text">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              La matriz documental está en borrador: falta que el área legal o responsable de cumplimiento la apruebe. Mientras tanto ningún expediente puede declararse
              completo y validado.
            </p>
          ) : null}
          {canReview && !locked ? (
            <div className="mt-3 flex flex-col gap-2">
              {d.summary.blockers.length > 0 ? (
                <ul className="list-disc pl-5 text-xs text-muted">
                  {d.summary.blockers.map((b) => (
                    <li key={b}>{b}</li>
                  ))}
                </ul>
              ) : null}
              <Button onClick={() => run(() => authorizeOperation(op.id), "Expediente de cierre completo y validado.")} disabled={busy || d.summary.blockers.length > 0}>
                <ShieldCheck className="h-4 w-4" aria-hidden /> Declarar completo y validado
              </Button>
            </div>
          ) : null}
          {canReview && locked ? (
            <Button className="mt-3" variant="secondary" onClick={() => setModal("reopen-review")} disabled={busy}>
              Reabrir revisión documental
            </Button>
          ) : null}
          {canManage && !locked && d.newMatrixRequirements > 0 ? (
            <Button className="mt-3" variant="secondary" onClick={() => run(() => applyCurrentMatrix(op.id), "Requisitos agregados.")} disabled={busy}>
              Agregar {d.newMatrixRequirements} requisito(s) nuevos de la matriz
            </Button>
          ) : null}
        </Card>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-2">
        {(["SELLER", "BUYER"] as PartySide[]).map((side) => {
          const p = d.parties.find((x) => x.side === side);
          return (
            <Card key={side}>
              <CardHeader
                title={partyLabel(op.operationType, side)}
                action={p ? <Badge tone={p.representedByGenera ? "gold" : "neutral"}>{p.representedByGenera ? "Cliente de C21 Genera" : "Contraparte"}</Badge> : undefined}
              />
              {p ? (
                <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
                  <Info label="Nombre" value={p.fullName} />
                  <Info label="Tipo de persona" value={p.personType === "LEGAL_ENTITY" ? "Persona moral" : p.personType === "INDIVIDUAL" ? "Persona física" : null} />
                  <Info label="Representante" value={p.representativeName ? `${p.representativeName}${p.representativeRole ? ` (${p.representativeRole})` : ""}` : null} />
                  <Info label="Contacto" value={[p.email, p.phone].filter(Boolean).join(" · ") || null} />
                  {!p.representedByGenera ? <Info label="Sus papeles se piden por" value={p.counterpartContact} /> : null}
                </dl>
              ) : (
                <p className="text-sm text-warning-text">Falta identificarlo. Se necesita para registrar el cierre y para validar el expediente.</p>
              )}
            </Card>
          );
        })}
      </div>

      <Card className="mt-6">
        <CardHeader
          title="Revisión documental de cierre"
          description="Cada requisito muestra su estado y la evidencia que lo sustenta. Recibir un archivo no equivale a validarlo; solo el rol autorizado valida y aprueba los “No aplica”."
        />
        <div className="mb-4 flex flex-wrap gap-2" role="tablist">
          {sections.map((s) => {
            const missing = d.summary.missingBySection[s] ?? 0;
            return (
              <button
                key={s}
                type="button"
                role="tab"
                aria-selected={section === s}
                onClick={() => setSection(s)}
                className={cn("rounded-lg border px-3 py-1.5 text-sm font-medium", section === s ? "border-gold bg-gold/10 text-obsessed" : "border-border text-muted hover:text-obsessed")}
              >
                {sectionLabels[s]} {missing > 0 ? <span className="ml-1 text-warning-text">({missing})</span> : <CheckCircle2 className="ml-1 inline h-3.5 w-3.5 text-success-text" aria-label="Sin faltantes" />}
              </button>
            );
          })}
        </div>
        {bySection.length === 0 ? (
          <p className="text-sm text-muted">La matriz vigente no pide requisitos de esta sección para esta operación.</p>
        ) : (
          groups.map((g) => {
            const rows = bySection.filter((r) => (r.partySide ?? "OPERATION") === g.key);
            if (rows.length === 0) return null;
            return (
              <div key={g.key} className="mb-5">
                <h3 className="mb-2 text-sm font-semibold text-obsessed">{g.title}</h3>
                <div className="flex flex-col gap-2">
                  {rows.map((r) => (
                    <RequirementRow key={r.id} detail={d} requirement={r} canManage={canManage && !locked} canReview={canReview && !locked} onChange={apply} />
                  ))}
                </div>
              </div>
            );
          })
        )}
      </Card>

      <Card className="mt-6">
        <CardHeader title="Historial" description="Cambios de estado, revisiones, observaciones, autorizaciones y reaperturas." />
        <ol className="flex flex-col gap-2 text-sm">
          {d.history.map((h) => (
            <li key={h.id} className="flex flex-col gap-0.5 border-l-2 border-border pl-3">
              <span>{h.detail}</span>
              <span className="text-xs text-muted">
                {h.actorName ?? "Sistema"} · {formatMoment(h.occurredAt)}
              </span>
            </li>
          ))}
        </ol>
      </Card>

      {modal === "edit" ? <EditOperationModal detail={d} open onClose={() => setModal(null)} onSaved={(n) => (apply(n), setModal(null), showToast("Operación actualizada."))} /> : null}
      {modal === "close" ? <CloseOperationModal detail={d} open onClose={() => setModal(null)} onSaved={(n) => (apply(n), setModal(null), showToast("Cierre registrado."))} /> : null}
      <ReasonModal
        open={modal === "cancel"}
        title="Cancelar operación"
        label="Motivo"
        danger
        confirmLabel="Cancelar operación"
        onCancel={() => setModal(null)}
        onConfirm={async (reason) => {
          await run(() => cancelOperation(op.id, reason), "Operación cancelada.");
          setModal(null);
        }}
      />
      <ReasonModal
        open={modal === "reopen"}
        title="Reabrir operación"
        description="Vuelve a revisión de cierre. Se conservan documentos, versiones e historial."
        label="Motivo"
        confirmLabel="Reabrir"
        onCancel={() => setModal(null)}
        onConfirm={async (reason) => {
          await run(() => reopenOperation(op.id, reason), "Operación reabierta.");
          setModal(null);
        }}
      />
      <ReasonModal
        open={modal === "reopen-review"}
        title="Reabrir revisión documental"
        description="El expediente deja de estar completo y validado hasta una nueva autorización."
        label="Motivo"
        confirmLabel="Reabrir revisión"
        onCancel={() => setModal(null)}
        onConfirm={async (reason) => {
          await run(() => reopenDocReview(op.id, reason), "Revisión documental reabierta.");
          setModal(null);
        }}
      />
    </PageContainer>
  );
}

function Info({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="text-obsessed">{value || "—"}</dd>
    </div>
  );
}
