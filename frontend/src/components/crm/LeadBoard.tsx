"use client";

import { SourceLabel, StageBadge, errorText } from "@/components/crm/CrmUi";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { useToast } from "@/components/ui/Toast";
import { changeLeadStage, type LeadItem, type LeadStage } from "@/lib/api/crm";
import { interestLabels, stageLabels, stageOrder, timeAgo, whatsappLink } from "@/lib/crm/labels";
import { cn } from "@/lib/utils";
import { MessageCircle, Phone } from "lucide-react";
import Link from "next/link";
import { useState, type DragEvent } from "react";

const DAY_MS = 86_400_000;

/** Nuevo sin primer contacto por más de 24 h (mismo criterio que el dashboard). */
function isCold(lead: LeadItem, now: number): boolean {
  return lead.stage === "NEW" && now - new Date(lead.createdAt).getTime() > DAY_MS;
}

/**
 * Tablero por etapa. Con permiso para mover (LEAD_MANAGE) las tarjetas se arrastran entre columnas
 * o se mueven con su menú; ganado pide confirmación y perdido pide el motivo, igual que en el detalle.
 */
export function LeadBoard({ leads, canMove, onMoved }: { leads: LeadItem[]; canMove: boolean; onMoved: (lead: LeadItem) => void }) {
  const { showToast } = useToast();
  const [dragging, setDragging] = useState<string | null>(null);
  const [over, setOver] = useState<LeadStage | null>(null);
  const [pending, setPending] = useState<{ lead: LeadItem; stage: "WON" | "LOST" } | null>(null);
  const [saving, setSaving] = useState(false);
  const [now] = useState(() => Date.now());

  const move = async (lead: LeadItem, stage: LeadStage, lostReason?: string) => {
    setSaving(true);
    try {
      const detail = await changeLeadStage(lead.id, stage, lostReason);
      onMoved(detail.lead);
      setPending(null);
      showToast(`${lead.name}: ${stageLabels[stage]}`);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const request = (lead: LeadItem, stage: LeadStage) => {
    if (stage === lead.stage) return;
    if (stage === "WON" || stage === "LOST") setPending({ lead, stage });
    else void move(lead, stage);
  };

  const drop = (e: DragEvent, stage: LeadStage) => {
    e.preventDefault();
    setOver(null);
    const lead = leads.find((l) => l.id === e.dataTransfer.getData("text/plain"));
    setDragging(null);
    if (lead) request(lead, stage);
  };

  return (
    <>
      {canMove ? <p className="mb-3 text-xs text-muted">Arrastra una tarjeta a otra columna para cambiar su etapa.</p> : null}
      <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
        <div className="grid min-w-[1180px] grid-cols-7 gap-3">
          {stageOrder.map((stage) => {
            const items = leads.filter((l) => l.stage === stage);
            const isTarget = canMove && over === stage && dragging !== null;
            return (
              <section
                key={stage}
                aria-label={stageLabels[stage]}
                onDragOver={
                  canMove
                    ? (e) => {
                        e.preventDefault();
                        e.dataTransfer.dropEffect = "move";
                        if (over !== stage) setOver(stage);
                      }
                    : undefined
                }
                onDragLeave={canMove ? () => setOver((o) => (o === stage ? null : o)) : undefined}
                onDrop={canMove ? (e) => drop(e, stage) : undefined}
                className={cn(
                  "flex min-w-0 flex-col rounded-xl border bg-app-bg/60 transition-colors duration-150",
                  isTarget ? "border-gold bg-gold/10" : "border-border",
                )}
              >
                <header className="flex items-center justify-between gap-2 border-b border-border px-3 py-2.5">
                  <StageBadge stage={stage} />
                  <span className="rounded-full bg-card px-2 text-xs tabular-nums text-muted">{items.length}</span>
                </header>
                <ul className="flex min-h-24 flex-col gap-2 p-2">
                  {items.map((lead) => (
                    <li
                      key={lead.id}
                      draggable={canMove && !saving}
                      onDragStart={(e) => {
                        e.dataTransfer.setData("text/plain", lead.id);
                        e.dataTransfer.effectAllowed = "move";
                        setDragging(lead.id);
                      }}
                      onDragEnd={() => {
                        setDragging(null);
                        setOver(null);
                      }}
                      className={cn(canMove && "cursor-grab active:cursor-grabbing", dragging === lead.id && "opacity-50")}
                    >
                      <BoardCard lead={lead} cold={isCold(lead, now)} canMove={canMove} disabled={saving} onMove={(s) => request(lead, s)} />
                    </li>
                  ))}
                  {items.length === 0 ? <li className="px-1 py-4 text-center text-xs text-muted">{isTarget ? "Soltar aquí" : "Sin leads"}</li> : null}
                </ul>
              </section>
            );
          })}
        </div>
      </div>

      <Modal
        open={pending?.stage === "WON"}
        onClose={() => setPending(null)}
        title="Cerrado ganado"
        footer={
          <>
            <Button variant="ghost" onClick={() => setPending(null)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={() => pending && void move(pending.lead, "WON")} disabled={saving}>
              {saving ? "Guardando…" : "Sí, cerrado ganado"}
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted">¿Confirmas que {pending?.lead.name ?? "el lead"} cerró operación?</p>
      </Modal>
      <ReasonModal
        open={pending?.stage === "LOST"}
        title="Marcar como perdido"
        description={pending ? `${pending.lead.name} pasará a Perdido.` : undefined}
        label="Motivo de pérdida"
        placeholder="Ej. compró con otra inmobiliaria"
        confirmLabel="Marcar como perdido"
        minLength={1}
        danger
        onCancel={() => setPending(null)}
        onConfirm={(reason) => (pending ? move(pending.lead, "LOST", reason) : undefined)}
      />
    </>
  );
}

function BoardCard({ lead, cold, canMove, disabled, onMove }: { lead: LeadItem; cold: boolean; canMove: boolean; disabled: boolean; onMove: (stage: LeadStage) => void }) {
  return (
    <article className={cn("rounded-lg border bg-card p-3 shadow-[0_1px_2px_rgba(36,39,35,0.04)] transition-shadow hover:shadow-md", cold ? "border-warning-text/40" : "border-border")}>
      <Link href={`/leads/${lead.id}`} className="block rounded focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30" draggable={false}>
        <p className="truncate text-sm font-semibold text-obsessed">{lead.name}</p>
        <p className="mt-0.5 truncate text-xs text-muted">{lead.property?.title ?? interestLabels[lead.interest]}</p>
      </Link>
      <SourceLabel source={lead.source} detail={lead.sourceDetail} className="mt-1.5 text-xs" />
      <p className={cn("mt-1 truncate text-xs", lead.advisor ? "text-muted" : "font-medium text-warning-text")}>{lead.advisor?.name ?? "Sin asignar"}</p>
      <div className="mt-2 flex items-center gap-1 border-t border-border pt-2">
        {lead.phone ? (
          <>
            <a
              href={`tel:${lead.phone.replace(/[^\d+]/g, "")}`}
              aria-label={`Llamar a ${lead.name}`}
              title="Llamar"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-muted hover:bg-app-bg hover:text-obsessed focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
            >
              <Phone className="h-4 w-4" aria-hidden />
            </a>
            <a
              href={whatsappLink(lead.phone)}
              target="_blank"
              rel="noreferrer"
              aria-label={`WhatsApp a ${lead.name}`}
              title="WhatsApp"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-muted hover:bg-app-bg hover:text-success-text focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
            >
              <MessageCircle className="h-4 w-4" aria-hidden />
            </a>
          </>
        ) : null}
        <span className={cn("ml-auto truncate text-[0.7rem]", cold ? "font-medium text-warning-text" : "text-muted")} title={cold ? "Sin primer contacto por más de 24 h" : "Último movimiento"}>
          {timeAgo(lead.lastActivityAt)}
        </span>
      </div>
      {canMove ? (
        <>
          <label htmlFor={`move-${lead.id}`} className="sr-only">
            Mover {lead.name} a otra etapa
          </label>
          <select
            id={`move-${lead.id}`}
            value=""
            disabled={disabled}
            onChange={(e) => e.target.value && onMove(e.target.value as LeadStage)}
            className="mt-2 h-8 w-full rounded-lg border border-border bg-card px-2 text-xs text-muted focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          >
            <option value="">Mover a…</option>
            {stageOrder
              .filter((s) => s !== lead.stage)
              .map((s) => (
                <option key={s} value={s}>
                  {stageLabels[s]}
                </option>
              ))}
          </select>
        </>
      ) : null}
    </article>
  );
}
