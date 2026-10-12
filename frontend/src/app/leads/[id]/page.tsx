"use client";

import { AdvisorChip, LoadError, PropertyMini, SourceLabel, StageBadge, errorText, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import {
  addLeadNote,
  assignLead,
  changeLeadStage,
  getConversation,
  getLead,
  listAdvisors,
  resendLeadNotice,
  type ActivityKind,
  type AdvisorRouting,
  type LeadDetail,
  type LeadItem,
  type LeadStage,
} from "@/lib/api/crm";
import {
  advisorStatusLabels,
  assignmentHints,
  assignmentLabels,
  assignmentStatusLabels,
  assignmentStatusTones,
  formatInstantMx,
  controlLabels,
  controlTones,
  formatDateTime,
  formatPrice,
  interestLabels,
  stageLabels,
  stageOrder,
  timeAgo,
  whatsappLink,
} from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, ArrowLeft, Bell, Bot, CheckCircle2, FilePlus2, Home, MessageCircle, MessagesSquare, StickyNote, UserCheck, UserPlus } from "lucide-react";
import Link from "next/link";
import { use, useState, type ReactNode } from "react";

export default function LeadDetailPage({ params }: PageProps<"/leads/[id]">) {
  const { id } = use(params);
  const can = useCan();
  const detail = useApi(() => getLead(id), [id]);
  const advisors = useApi(() => (can("LEAD_ASSIGN") ? listAdvisors() : Promise.resolve([])), [can("LEAD_ASSIGN")]);

  if (detail.error && !detail.data) {
    return (
      <PageContainer title="Lead">
        <LoadError message={detail.error} onRetry={detail.reload} />
      </PageContainer>
    );
  }
  if (!detail.data) {
    return (
      <PageContainer title="Lead">
        <StateMessage kind="loading" title="Cargando lead…" />
      </PageContainer>
    );
  }

  const { lead, activities } = detail.data;
  const update = (next: LeadDetail) => detail.setData(next);

  return (
    <PageContainer
      title={lead.name}
      subtitle={
        <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
          {lead.phone ? <span className="tabular-nums">{lead.phone}</span> : null}
          {lead.email ? <span>{lead.email}</span> : null}
          <span>Recibido {timeAgo(lead.createdAt)}</span>
        </span>
      }
      action={
        <>
          <Link
            href="/leads"
            className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-medium text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-obsessed/15"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden /> Leads
          </Link>
          {lead.phone ? (
            <a
              href={whatsappLink(lead.phone)}
              target="_blank"
              rel="noreferrer"
              className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-gold px-4 py-2.5 text-sm font-medium text-brand-ink shadow-sm hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
            >
              <MessageCircle className="h-4 w-4" aria-hidden /> WhatsApp
            </a>
          ) : null}
        </>
      }
    >
      {lead.coordinationNote ? (
        <p className="mb-5 flex items-start gap-2.5 rounded-xl border border-warning-text/25 bg-warning-bg px-4 py-3 text-sm text-warning-text">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          <span>
            <strong className="font-semibold">Requiere coordinación:</strong> {lead.coordinationNote}
          </span>
        </p>
      ) : null}
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="min-w-0 space-y-5">
          <Card>
            <CardHeader title="Resumen" action={<StageBadge stage={lead.stage} />} />
            <dl className="grid grid-cols-1 gap-x-6 gap-y-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
              <Field label="Busca">{interestLabels[lead.interest]}</Field>
              <Field label="Fuente">
                <SourceLabel source={lead.source} detail={lead.sourceDetail} className="text-obsessed" />
              </Field>
              <Field label="Presupuesto">{lead.budget ? formatPrice(lead.budget) : "—"}</Field>
              <Field label="Recibido">{formatDateTime(lead.createdAt)}</Field>
              <Field label="Último movimiento">{timeAgo(lead.lastActivityAt)}</Field>
              {lead.stage === "LOST" ? <Field label="Motivo de pérdida">{lead.lostReason ?? "—"}</Field> : null}
            </dl>
            {lead.summary ? (
              <p className="mt-5 whitespace-pre-wrap rounded-xl bg-app-bg px-4 py-3 text-sm text-obsessed">
                <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-muted">Lo que nos dijo</span>
                {lead.summary}
              </p>
            ) : null}
          </Card>

          <Card>
            <CardHeader title="Propiedad de interés" description={lead.property ? "Consulta la ficha completa en 21Online." : undefined} />
            {lead.property ? (
              <PropertyMini property={lead.property} />
            ) : (
              <p className="text-sm text-muted">
                {lead.interest === "SELL" || lead.interest === "LEASE_OUT"
                  ? "Este prospecto quiere vender o rentar su propiedad: todavía no está en el inventario."
                  : "No mencionó una propiedad específica; el asesor le ofrecerá opciones según lo que busca."}
              </p>
            )}
          </Card>

          {lead.conversationId && can("CONVERSATION_VIEW") ? <ConversationPreview conversationId={lead.conversationId} name={lead.name} /> : null}

          {detail.data.assignments.length ? (
            <Card>
              <CardHeader title="Asignación: por qué y cuándo" />
              <AssignmentLogList entries={detail.data.assignments} />
            </Card>
          ) : null}

          <Card>
            <CardHeader title="Historial" />
            <Timeline items={activities} />
          </Card>
        </div>

        <aside className="min-w-0 space-y-5">
          {can("LEAD_MANAGE") ? <StageCard lead={lead} onChange={update} /> : null}
          <Card>
            <CardHeader title="Asesor que atiende" action={<Badge tone={assignmentStatusTones[lead.assignmentStatus]}>{assignmentStatusLabels[lead.assignmentStatus]}</Badge>} />
            <AdvisorChip advisor={lead.advisor} emptyLabel={lead.assignmentStatus === "PENDING_SCHEDULE" ? "Pendiente por horario" : "Sin asignar"} />
            {lead.assignmentStatus === "ASSIGNED" && lead.assignmentMethod ? (
              <p className="mt-2 text-xs text-muted">
                Motivo: {assignmentLabels[lead.assignmentMethod]} — {lead.assignmentRule ?? assignmentHints[lead.assignmentMethod]}.
              </p>
            ) : null}
            {lead.assignmentStatus === "PENDING_OWNER" || lead.assignmentStatus === "PENDING_SCHEDULE" ? (
              <div className="mt-3 rounded-xl bg-app-bg px-3 py-2 text-xs text-obsessed">
                <p>{lead.pendingReason ?? "En espera de un turno disponible."}</p>
                <p className="mt-1 text-muted">
                  {lead.nextShiftAt ? `Próximo turno disponible: ${formatInstantMx(lead.nextShiftAt)} (se asigna solo).` : "No hay un próximo turno en el calendario: asígnalo manualmente o configura turnos."}
                </p>
              </div>
            ) : null}
            {lead.assignmentStatus === "UNASSIGNED" ? <p className="mt-2 text-xs text-warning-text">{lead.pendingReason ?? "Sin asesor."} Asígnalo manualmente.</p> : null}
            {lead.propertyAdvisor && lead.propertyAdvisor.id !== lead.advisor?.id ? (
              <p className="mt-3 flex items-center gap-1.5 border-t border-border pt-3 text-xs text-muted">
                <Home className="h-3.5 w-3.5" aria-hidden /> Asesor responsable de la propiedad: <span className="font-medium text-obsessed">{lead.propertyAdvisor.name}</span>
              </p>
            ) : null}
            {lead.assignmentStatus === "ASSIGNED" ? <NoticeStatus leadId={lead.id} status={detail.data.advisorNoticeStatus} canResend={can("LEAD_MANAGE")} onChange={update} /> : null}
            {can("LEAD_ASSIGN") ? <ReassignForm lead={lead} advisors={advisors.data ?? []} onChange={update} /> : null}
          </Card>
          {lead.interest === "SELL" || lead.interest === "LEASE_OUT" ? (
            <Card className="border-gold/50 bg-gold/5">
              <CardHeader title="¿Firmó la intermediación?" description="Los propietarios que firman pasan al Módulo 1 como expediente." />
              <Link
                href="/expedientes/nuevo"
                className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-brand-ink px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-ink/90 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
              >
                <FilePlus2 className="h-4 w-4" aria-hidden /> Crear expediente
              </Link>
            </Card>
          ) : null}
          {can("LEAD_MANAGE") ? <NoteCard leadId={lead.id} onChange={update} /> : null}
        </aside>
      </div>
    </PageContainer>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 text-obsessed">{children}</dd>
    </div>
  );
}

function ConversationPreview({ conversationId, name }: { conversationId: string; name: string }) {
  const conversation = useApi(() => getConversation(conversationId), [conversationId]);
  if (!conversation.data) return null;
  const c = conversation.data;
  return (
    <Card>
      <CardHeader title="Conversación de WhatsApp" action={<Badge tone={controlTones[c.conversation.control]}>{controlLabels[c.conversation.control]}</Badge>} />
      <ul className="space-y-2">
        {c.messages.slice(-3).map((m) => (
          <li key={m.id} className={cn("max-w-[85%] rounded-xl px-3.5 py-2 text-sm", m.role === "CLIENT" ? "bg-app-bg text-obsessed" : "ml-auto bg-gold/15 text-obsessed")}>
            <span className="mb-0.5 block text-[0.7rem] font-medium uppercase tracking-wide text-muted">
              {m.role === "CLIENT" ? name.split(" ")[0] : m.role === "BOT" ? "Asistente" : "Equipo"}
            </span>
            {m.text}
          </li>
        ))}
      </ul>
      <Link href={`/conversaciones?c=${conversationId}`} className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:text-obsessed">
        <MessagesSquare className="h-4 w-4" aria-hidden /> Abrir conversación completa
      </Link>
    </Card>
  );
}

function StageCard({ lead, onChange }: { lead: LeadItem; onChange: (d: LeadDetail) => void }) {
  const { showToast } = useToast();
  const [pending, setPending] = useState<LeadStage | null>(null);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const funnel = stageOrder.filter((s) => s !== "LOST");
  const reachedIndex = lead.stage === "LOST" ? -1 : funnel.indexOf(lead.stage);

  const apply = async (stage: LeadStage) => {
    if (stage === "LOST" && !reason.trim()) return;
    setSaving(true);
    try {
      onChange(await changeLeadStage(lead.id, stage, stage === "LOST" ? reason.trim() : undefined));
      setPending(null);
      setReason("");
      showToast(`Etapa: ${stageLabels[stage]}`);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Etapa" description="Avanza el lead conforme progresa el seguimiento." />
      <ol className="space-y-1.5">
        {funnel.map((stage, i) => {
          const done = i <= reachedIndex;
          const isCurrent = stage === lead.stage;
          return (
            <li key={stage}>
              <button
                type="button"
                onClick={() => (stage === "WON" ? setPending("WON") : void apply(stage))}
                disabled={isCurrent || saving}
                className={cn(
                  "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                  isCurrent ? "bg-gold/15 font-semibold text-obsessed" : "text-muted hover:bg-app-bg hover:text-obsessed",
                )}
                aria-current={isCurrent ? "step" : undefined}
              >
                <span
                  className={cn("flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-[0.65rem]", done ? "border-gold bg-gold text-brand-ink" : "border-border bg-card")}
                  aria-hidden
                >
                  {done ? <CheckCircle2 className="h-3.5 w-3.5" /> : i + 1}
                </span>
                {stageLabels[stage]}
              </button>
            </li>
          );
        })}
      </ol>

      {pending === "WON" ? (
        <div className="mt-4 rounded-xl border border-success-text/30 bg-success-bg p-3 text-sm text-success-text">
          <p>¿Confirmas que el lead cerró operación?</p>
          <div className="mt-2 flex gap-2">
            <Button size="sm" onClick={() => void apply("WON")} disabled={saving}>
              Sí, cerrado ganado
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setPending(null)}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : null}

      {lead.stage !== "LOST" ? (
        pending === "LOST" ? (
          <div className="mt-4 space-y-2">
            <label htmlFor="lost-reason" className="text-sm font-medium text-obsessed">
              Motivo de pérdida
            </label>
            <input
              id="lost-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Ej. compró con otra inmobiliaria"
              className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
            />
            <div className="flex gap-2">
              <Button size="sm" variant="danger" onClick={() => void apply("LOST")} disabled={!reason.trim() || saving}>
                Marcar como perdido
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setPending(null)}>
                Cancelar
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="ghost" size="sm" className="mt-3 text-danger-text" onClick={() => setPending("LOST")}>
            Marcar como perdido
          </Button>
        )
      ) : (
        <Button variant="secondary" size="sm" className="mt-3" onClick={() => void apply("CONTACTED")} disabled={saving}>
          Reabrir seguimiento
        </Button>
      )}
    </Card>
  );
}

function ReassignForm({ lead, advisors, onChange }: { lead: LeadItem; advisors: AdvisorRouting[]; onChange: (d: LeadDetail) => void }) {
  const { showToast } = useToast();
  const [target, setTarget] = useState("");
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const options = advisors.filter((a) => a.userId !== lead.advisor?.id || lead.assignmentStatus !== "ASSIGNED");
  const selected = advisors.find((a) => a.userId === target);
  const needsException = !!selected && selected.status !== "AVAILABLE";
  return (
    <>
      <div className="mt-4 flex items-end gap-2">
        <Select
          label={lead.advisor ? "Reasignar a" : "Asignar a"}
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          options={[{ value: "", label: "Elige un asesor" }, ...options.map((a) => ({ value: a.userId, label: `${a.name} · ${advisorStatusLabels[a.status]}` }))]}
          containerClassName="min-w-0 flex-1"
        />
        <Button
          variant="secondary"
          disabled={!target || saving || (needsException && !reason.trim())}
          onClick={async () => {
            setSaving(true);
            try {
              onChange(await assignLead(lead.id, target, needsException ? reason.trim() : undefined));
              setTarget("");
              setReason("");
              showToast("Lead asignado");
            } catch (err) {
              showToast(errorText(err));
            } finally {
              setSaving(false);
            }
          }}
        >
          {lead.advisor ? <UserCheck className="h-4 w-4" aria-hidden /> : <UserPlus className="h-4 w-4" aria-hidden />}
          {lead.advisor ? "Reasignar" : "Asignar"}
        </Button>
      </div>
      {needsException ? (
        <div className="mt-3 space-y-1.5 rounded-xl border border-warning-text/25 bg-warning-bg p-3">
          <p className="text-xs text-warning-text">
            {selected.name}: {selected.statusDetail}
            {selected.nextShift ? ` · próximo turno ${selected.nextShift.slice(11, 16)}` : ""}. Para asignarlo de todos modos indica el motivo de la excepción.
          </p>
          <label htmlFor="exception-reason" className="sr-only">
            Motivo de la excepción
          </label>
          <input
            id="exception-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Ej. el cliente pidió específicamente a este asesor"
            className="w-full rounded-xl border border-border bg-card px-3 py-2 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>
      ) : null}
      <p className="mt-2 text-xs text-muted">Si el asesor tiene WhatsApp configurado en Asesores, recibe un aviso con los datos del lead.</p>
    </>
  );
}

function NoteCard({ leadId, onChange }: { leadId: string; onChange: (d: LeadDetail) => void }) {
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const { showToast } = useToast();
  return (
    <Card>
      <CardHeader title="Agregar nota" />
      <label htmlFor="lead-note" className="sr-only">
        Nota de seguimiento
      </label>
      <textarea
        id="lead-note"
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={3}
        placeholder="Ej. Le llamé, quedó de confirmar la visita el jueves."
        className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
      />
      <Button
        className="mt-2"
        variant="secondary"
        disabled={!text.trim() || saving}
        onClick={async () => {
          setSaving(true);
          try {
            onChange(await addLeadNote(leadId, text.trim()));
            setText("");
            showToast("Nota agregada");
          } catch (err) {
            showToast(errorText(err));
          } finally {
            setSaving(false);
          }
        }}
      >
        <StickyNote className="h-4 w-4" aria-hidden /> Guardar nota
      </Button>
    </Card>
  );
}

const noticeText: Record<string, { label: string; tone: "success" | "warning" | "danger" | "neutral" }> = {
  DONE: { label: "Ficha enviada por WhatsApp", tone: "success" },
  PENDING: { label: "Ficha en envío", tone: "warning" },
  FAILED: { label: "No se pudo enviar la ficha", tone: "danger" },
  NOT_REQUIRED: { label: "Sin WhatsApp del asesor: ficha no enviada", tone: "neutral" },
};

/** Estado del envío de la ficha al asesor y reenvío manual. */
function NoticeStatus({ leadId, status, canResend, onChange }: { leadId: string; status: string; canResend: boolean; onChange: (d: LeadDetail) => void }) {
  const { showToast } = useToast();
  const [sending, setSending] = useState(false);
  const meta = noticeText[status] ?? noticeText.NOT_REQUIRED;
  return (
    <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-border pt-3">
      <Badge tone={meta.tone}>
        <Bell aria-hidden /> {meta.label}
      </Badge>
      {canResend ? (
        <Button
          variant="ghost"
          size="sm"
          disabled={sending}
          onClick={async () => {
            setSending(true);
            try {
              onChange(await resendLeadNotice(leadId));
              showToast("Ficha reenviada al asesor");
            } catch (err) {
              showToast(errorText(err));
            } finally {
              setSending(false);
            }
          }}
        >
          Reenviar ficha
        </Button>
      ) : null}
      {status === "NOT_REQUIRED" ? <p className="w-full text-xs text-muted">Captura su WhatsApp en Asesores para que reciba la ficha de cada lead.</p> : null}
    </div>
  );
}

const outcomeLabels: Record<LeadDetail["assignments"][number]["outcome"], string> = {
  ASSIGNED: "Asignado",
  REASSIGNED: "Reasignado",
  PENDING: "En espera",
  KEPT: "Se conservó",
  CONFLICT: "Conflicto",
};

function AssignmentLogList({ entries }: { entries: LeadDetail["assignments"] }) {
  return (
    <ol className="space-y-3">
      {entries.map((e) => (
        <li key={e.id} className="rounded-xl border border-border p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={e.outcome === "CONFLICT" ? "warning" : e.outcome === "PENDING" ? "info" : "success"}>{outcomeLabels[e.outcome]}</Badge>
            {e.advisor ? <span className="font-medium text-obsessed">{e.advisor.name}</span> : null}
            {e.reason ? <span className="text-xs text-muted">· {assignmentLabels[e.reason]}</span> : null}
            <span className="ml-auto text-xs text-muted">{formatInstantMx(e.createdAt)}</span>
          </div>
          <p className="mt-1 text-obsessed">{e.rule}</p>
          {e.detail ? <p className="text-xs text-muted">{e.detail}</p> : null}
          <p className="text-xs text-muted">
            {e.roundMode ? `Ronda ${e.roundMode === "EQUAL" ? "equitativa" : "ponderada"} · ` : ""}
            {e.shiftId ? "Dentro de un turno del calendario · " : ""}
            {e.actorName ? `Por ${e.actorName}` : "Automático"}
          </p>
        </li>
      ))}
    </ol>
  );
}

const activityIcons: Record<ActivityKind, typeof Bot> = {
  CREATED: Bot,
  ASSIGNED: UserCheck,
  REASSIGNED: UserCheck,
  STAGE: CheckCircle2,
  NOTE: StickyNote,
  BOT: Bot,
  NOTICE: Bell,
  FOLLOW_UP: MessagesSquare,
};

function Timeline({ items }: { items: LeadDetail["activities"] }) {
  if (items.length === 0) return <p className="text-sm text-muted">Sin movimientos.</p>;
  return (
    <ol className="space-y-4">
      {items.map((a) => {
        const Icon = activityIcons[a.kind] ?? Bot;
        return (
          <li key={a.id} className="flex gap-3">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-app-bg text-muted">
              <Icon className="h-4 w-4" aria-hidden />
            </span>
            <div className="min-w-0 pt-1">
              <p className={cn("text-sm text-obsessed", a.kind === "NOTE" && "whitespace-pre-wrap")}>{a.text}</p>
              <p className="text-xs text-muted">
                {formatDateTime(a.createdAt)}
                {a.actorName ? ` · ${a.actorName}` : ""}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
