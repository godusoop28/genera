"use client";

import { AdvisorChip, PreviewNotice, SourceLabel, StageBadge } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { ONLINE21_BASE_URL } from "@/data/crm/properties";
import {
  assignmentHints,
  assignmentLabels,
  controlLabels,
  controlTones,
  formatDateTime,
  formatPrice,
  interestLabels,
  propertyAddress,
  propertyPrice,
  stageLabels,
  stageOrder,
  timeAgo,
  whatsappLink,
} from "@/lib/crm/labels";
import { addLeadNote, changeLeadStage, reassignLead, useCrm } from "@/lib/crm/store";
import { cn } from "@/lib/utils";
import type { CrmLead, LeadActivity, LeadStage } from "@/types/crm";
import {
  ArrowLeft,
  Bath,
  BedDouble,
  Bell,
  Bot,
  Car,
  CheckCircle2,
  ExternalLink,
  FilePlus2,
  MessageCircle,
  MessagesSquare,
  Ruler,
  StickyNote,
  UserCheck,
  UserPlus,
} from "lucide-react";
import Link from "next/link";
import { use, useState, type ReactNode } from "react";

export default function LeadDetailPage({ params }: PageProps<"/leads/[id]">) {
  const { id } = use(params);
  const crm = useCrm();

  if (!crm) {
    return (
      <PageContainer title="Lead">
        <StateMessage kind="loading" title="Cargando lead…" />
      </PageContainer>
    );
  }

  const lead = crm.leads.find((l) => l.id === id);
  if (!lead) {
    return (
      <PageContainer title="Lead no encontrado">
        <StateMessage
          kind="no-results"
          title="Este lead no existe"
          description="Puede que el enlace sea de una sesión anterior (los datos de ejemplo se regeneran al recargar)."
          action={
            <Link href="/leads" className="text-sm font-medium text-dark-gold hover:text-obsessed">
              Volver a leads
            </Link>
          }
        />
      </PageContainer>
    );
  }

  const property = lead.propertyId ? crm.properties.find((p) => p.id === lead.propertyId) : undefined;
  const advisor = lead.advisorId ? crm.advisors.find((a) => a.id === lead.advisorId) : undefined;
  const conversation = lead.conversationId ? crm.conversations.find((c) => c.id === lead.conversationId) : undefined;

  return (
    <PageContainer
      title={lead.name}
      subtitle={
        <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className="tabular-nums">{lead.phone}</span>
          <span aria-hidden>·</span>
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
          <a
            href={whatsappLink(lead.phone)}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-gold px-4 py-2.5 text-sm font-medium text-brand-ink shadow-sm hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
          >
            <MessageCircle className="h-4 w-4" aria-hidden /> WhatsApp
          </a>
        </>
      }
    >
      <PreviewNotice />
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
              <Field label="Último movimiento">{lead.lastContactAt ? timeAgo(lead.lastContactAt) : "—"}</Field>
              {lead.stage === "LOST" ? <Field label="Motivo de pérdida">{lead.lostReason ?? "—"}</Field> : null}
            </dl>
            {lead.summary ? (
              <p className="mt-5 rounded-xl bg-app-bg px-4 py-3 text-sm text-obsessed">
                <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-muted">Lo que nos dijo</span>
                {lead.summary}
              </p>
            ) : null}
          </Card>

          <Card>
            <CardHeader
              title="Propiedad de interés"
              description={property ? "Datos consultados en 21Online." : undefined}
              action={
                property ? (
                  <a
                    href={`${ONLINE21_BASE_URL}/propiedades/ver/${property.id}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-sm font-medium text-dark-gold hover:text-obsessed"
                  >
                    21Online <ExternalLink className="h-3.5 w-3.5" aria-hidden />
                  </a>
                ) : null
              }
            />
            {property ? (
              <div>
                <Link href={`/propiedades/${property.id}`} className="group block rounded-lg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30">
                  <p className="font-mono text-xs text-muted">
                    {property.key} · {property.internalKey}
                  </p>
                  <p className="mt-1 text-lg font-semibold text-obsessed group-hover:text-dark-gold">{propertyPrice(property)}</p>
                  <p className="text-sm text-muted">
                    {property.subtype} en {property.operation} · {propertyAddress(property)}
                  </p>
                </Link>
                {property.details ? (
                  <ul className="mt-4 flex flex-wrap gap-2">
                    {property.details.bedrooms ? <Spec icon={BedDouble}>{property.details.bedrooms} recámaras</Spec> : null}
                    {property.details.bathrooms ? <Spec icon={Bath}>{property.details.bathrooms} baños</Spec> : null}
                    {property.details.parking ? <Spec icon={Car}>{property.details.parking} estacionamientos</Spec> : null}
                    {property.details.builtM2 ? <Spec icon={Ruler}>{property.details.builtM2} m² construcción</Spec> : null}
                  </ul>
                ) : null}
              </div>
            ) : (
              <p className="text-sm text-muted">
                {lead.interest === "SELL"
                  ? "Este prospecto quiere vender: su propiedad todavía no está en el inventario."
                  : "El cliente no mencionó una propiedad específica; el asesor le ofrecerá opciones según lo que busca."}
              </p>
            )}
          </Card>

          {conversation ? (
            <Card>
              <CardHeader
                title="Conversación de WhatsApp"
                action={<Badge tone={controlTones[conversation.control]}>{controlLabels[conversation.control]}</Badge>}
              />
              <ul className="space-y-2">
                {conversation.messages.slice(-3).map((m) => (
                  <li key={m.id} className={cn("max-w-[85%] rounded-xl px-3.5 py-2 text-sm", m.from === "CLIENT" ? "bg-app-bg text-obsessed" : "ml-auto bg-gold/15 text-obsessed")}>
                    <span className="mb-0.5 block text-[0.7rem] font-medium uppercase tracking-wide text-muted">
                      {m.from === "CLIENT" ? lead.name.split(" ")[0] : m.from === "BOT" ? "Asistente" : "Equipo"}
                    </span>
                    {m.text}
                  </li>
                ))}
              </ul>
              <Link
                href={`/conversaciones?c=${conversation.id}`}
                className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:text-obsessed"
              >
                <MessagesSquare className="h-4 w-4" aria-hidden /> Abrir conversación completa
              </Link>
            </Card>
          ) : null}

          <Card>
            <CardHeader title="Historial" />
            <Timeline items={lead.activities} />
          </Card>
        </div>

        <aside className="min-w-0 space-y-5">
          <StageCard lead={lead} />
          <AdvisorCard lead={lead} advisorName={advisor?.name} advisors={crm.advisors.filter((a) => a.active)}>
            <AdvisorChip advisor={advisor} />
          </AdvisorCard>
          {lead.interest === "SELL" ? (
            <Card className="border-gold/50 bg-gold/5">
              <CardHeader title="¿Firmó la intermediación?" description="Los prospectos que quieren vender pasan al Módulo 1 como expediente." />
              <Link
                href="/expedientes/nuevo"
                className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-brand-ink px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-ink/90 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
              >
                <FilePlus2 className="h-4 w-4" aria-hidden /> Crear expediente
              </Link>
            </Card>
          ) : null}
          <NoteCard leadId={lead.id} />
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

function Spec({ icon: Icon, children }: { icon: typeof Bath; children: ReactNode }) {
  return (
    <li className="inline-flex items-center gap-1.5 rounded-full border border-border bg-app-bg px-3 py-1 text-xs text-obsessed">
      <Icon className="h-3.5 w-3.5 text-muted" aria-hidden />
      {children}
    </li>
  );
}

function StageCard({ lead }: { lead: CrmLead }) {
  const { showToast } = useToast();
  const [pending, setPending] = useState<LeadStage | null>(null);
  const [reason, setReason] = useState("");
  const funnel = stageOrder.filter((s) => s !== "LOST");
  const reachedIndex = lead.stage === "LOST" ? -1 : funnel.indexOf(lead.stage);

  const apply = (stage: LeadStage) => {
    if (stage === "LOST" && !reason.trim()) return;
    changeLeadStage(lead.id, stage, reason.trim());
    setPending(null);
    setReason("");
    showToast(`Etapa: ${stageLabels[stage]}`);
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
                onClick={() => (isCurrent ? null : stage === "WON" ? setPending("WON") : apply(stage))}
                disabled={isCurrent}
                className={cn(
                  "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                  isCurrent ? "bg-gold/15 font-semibold text-obsessed" : "text-muted hover:bg-app-bg hover:text-obsessed",
                )}
                aria-current={isCurrent ? "step" : undefined}
              >
                <span
                  className={cn(
                    "flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-[0.65rem]",
                    done ? "border-gold bg-gold text-brand-ink" : "border-border bg-card",
                  )}
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
            <Button size="sm" onClick={() => apply("WON")}>
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
              <Button size="sm" variant="danger" onClick={() => apply("LOST")} disabled={!reason.trim()}>
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
        <Button variant="secondary" size="sm" className="mt-3" onClick={() => apply("CONTACTED")}>
          Reabrir seguimiento
        </Button>
      )}
    </Card>
  );
}

function AdvisorCard({
  lead,
  advisorName,
  advisors,
  children,
}: {
  lead: CrmLead;
  advisorName?: string;
  advisors: { id: string; name: string }[];
  children: ReactNode;
}) {
  const { showToast } = useToast();
  const [target, setTarget] = useState("");
  const options = advisors.filter((a) => a.id !== lead.advisorId);
  return (
    <Card>
      <CardHeader title="Asesor" />
      {children}
      {lead.assignment ? (
        <p className="mt-2 text-xs text-muted" title={assignmentHints[lead.assignment]}>
          Asignación: {assignmentLabels[lead.assignment]} — {assignmentHints[lead.assignment].toLowerCase()}.
        </p>
      ) : (
        <p className="mt-2 text-xs text-warning-text">Ningún asesor estaba disponible cuando llegó. Asígnalo manualmente.</p>
      )}
      <div className="mt-4 flex items-end gap-2">
        <Select
          label={lead.advisorId ? "Reasignar a" : "Asignar a"}
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          options={[{ value: "", label: "Elige un asesor" }, ...options.map((a) => ({ value: a.id, label: a.name }))]}
          containerClassName="min-w-0 flex-1"
        />
        <Button
          variant="secondary"
          disabled={!target}
          onClick={() => {
            const name = options.find((a) => a.id === target)?.name;
            reassignLead(lead.id, target);
            setTarget("");
            showToast(`Asignado a ${name}${advisorName ? ` (antes: ${advisorName})` : ""}`);
          }}
        >
          {lead.advisorId ? <UserCheck className="h-4 w-4" aria-hidden /> : <UserPlus className="h-4 w-4" aria-hidden />}
          {lead.advisorId ? "Reasignar" : "Asignar"}
        </Button>
      </div>
      <p className="mt-2 text-xs text-muted">Al asignar, el asesor recibirá un aviso por WhatsApp con la ficha de la propiedad (cuando el bot esté conectado).</p>
    </Card>
  );
}

function NoteCard({ leadId }: { leadId: string }) {
  const [text, setText] = useState("");
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
        disabled={!text.trim()}
        onClick={() => {
          addLeadNote(leadId, text.trim());
          setText("");
          showToast("Nota agregada");
        }}
      >
        <StickyNote className="h-4 w-4" aria-hidden /> Guardar nota
      </Button>
    </Card>
  );
}

const activityIcons: Record<LeadActivity["kind"], typeof Bot> = {
  CREATED: Bot,
  ASSIGNED: UserCheck,
  REASSIGNED: UserCheck,
  STAGE: CheckCircle2,
  NOTE: StickyNote,
  BOT: Bot,
  NOTICE: Bell,
};

function Timeline({ items }: { items: LeadActivity[] }) {
  if (items.length === 0) return <p className="text-sm text-muted">Sin movimientos.</p>;
  return (
    <ol className="space-y-4">
      {items.map((a) => {
        const Icon = activityIcons[a.kind];
        return (
          <li key={a.id} className="flex gap-3">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-app-bg text-muted">
              <Icon className="h-4 w-4" aria-hidden />
            </span>
            <div className="min-w-0 pt-1">
              <p className={cn("text-sm text-obsessed", a.kind === "NOTE" && "whitespace-pre-wrap")}>{a.text}</p>
              <p className="text-xs text-muted">
                {formatDateTime(a.at)}
                {a.by ? ` · ${a.by}` : ""}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
