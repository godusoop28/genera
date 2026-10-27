"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import {
  createSurvey,
  getSurveyConfig,
  type CreateSurveyInput,
  type NpsCategory,
  type PublicSurvey,
  type SurveyChannel,
  type SurveyItem,
  type SurveyStatus,
  type Touchpoint,
  type TouchpointConfig,
} from "@/lib/api/surveys";
import { cn } from "@/lib/utils";
import { CheckCircle2, Copy, Download, MessageCircle, Send } from "lucide-react";
import QRCode from "qrcode";
import { useEffect, useState } from "react";

type Tone = "gold" | "obsessed" | "success" | "warning" | "danger" | "info" | "manual" | "neutral";

export const channelLabels: Record<SurveyChannel, string> = {
  WHATSAPP: "WhatsApp",
  EMAIL: "Correo electrónico",
  SYSTEM: "Dentro del sistema (portal del cliente)",
  ADVISOR_LINK: "Liga generada por el asesor",
  QR: "QR en material físico",
};

export const channelShort: Record<SurveyChannel, string> = { WHATSAPP: "WhatsApp", EMAIL: "Correo", SYSTEM: "Sistema", ADVISOR_LINK: "Liga del asesor", QR: "QR" };

export const statusLabels: Record<SurveyStatus, string> = {
  SCHEDULED: "Programada",
  SENT: "Enviada, sin respuesta",
  ANSWERED: "Respondida",
  EXPIRED: "Venció sin respuesta",
  SKIPPED: "Omitida",
  FAILED: "No se pudo enviar",
};

export const statusTones: Record<SurveyStatus, Tone> = { SCHEDULED: "info", SENT: "warning", ANSWERED: "success", EXPIRED: "neutral", SKIPPED: "neutral", FAILED: "danger" };

export const categoryLabels: Record<NpsCategory, string> = { DETRACTOR: "Detractor", PASSIVE: "Pasivo", PROMOTER: "Promotor" };
export const categoryTones: Record<NpsCategory, Tone> = { DETRACTOR: "danger", PASSIVE: "warning", PROMOTER: "success" };
export const categoryDots: Record<NpsCategory, string> = { DETRACTOR: "🔴", PASSIVE: "🟡", PROMOTER: "🟢" };

export const delayOptions = [
  { value: "0", label: "Inmediato" },
  { value: "30", label: "30 minutos después" },
  { value: "120", label: "2 horas después" },
  { value: "1440", label: "24 horas después" },
  { value: "43200", label: "30 días después" },
];

export function categoryOf(score: number): NpsCategory {
  return score <= 6 ? "DETRACTOR" : score <= 8 ? "PASSIVE" : "PROMOTER";
}

export function ScoreBadge({ score, category }: { score: number | null; category: NpsCategory | null }) {
  if (score === null || category === null) return null;
  return (
    <Badge tone={categoryTones[category]}>
      {categoryDots[category]} {score}/10 · {categoryLabels[category]}
    </Badge>
  );
}

/** NPS con color: < 0 rojo, 0–49 amarillo, ≥ 50 verde. */
export function npsTone(nps: number): string {
  return nps < 0 ? "text-danger-text" : nps < 50 ? "text-warning-text" : "text-success-text";
}

// --- Encuesta para el cliente ----------------------------------------------------

/**
 * La pregunta NPS (0 a 10) y, según la calificación, la pregunta de
 * seguimiento adaptada. onAnswer/onComment llaman a la API pública.
 */
export function SurveyWidget({
  survey,
  onAnswer,
  onComment,
  compact,
}: {
  survey: PublicSurvey;
  onAnswer: (score: number) => Promise<PublicSurvey>;
  onComment: (comment: string) => Promise<PublicSurvey>;
  compact?: boolean;
}) {
  const [current, setCurrent] = useState(survey);
  const [comment, setComment] = useState(survey.comment ?? "");
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(survey.status === "ANSWERED" && (!survey.askComment || survey.comment !== null));
  const [error, setError] = useState<string | null>(null);

  if (current.status === "EXPIRED") {
    return <p className="text-sm text-muted">Esta encuesta ya venció. ¡Gracias por tu interés!</p>;
  }

  const pick = async (score: number) => {
    setSaving(true);
    setError(null);
    try {
      const next = await onAnswer(score);
      setCurrent(next);
      if (!next.askComment) setDone(true);
    } catch (err) {
      setError(err instanceof ApiError && err.message ? err.message : "No se pudo guardar tu respuesta. Intenta de nuevo.");
    } finally {
      setSaving(false);
    }
  };

  const send = async () => {
    setSaving(true);
    setError(null);
    try {
      setCurrent(await onComment(comment.trim()));
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError && err.message ? err.message : "No se pudo guardar tu comentario. Intenta de nuevo.");
    } finally {
      setSaving(false);
    }
  };

  if (done) {
    return (
      <div className="flex flex-col items-center gap-2 py-4 text-center">
        <CheckCircle2 className="h-10 w-10 text-success-text" aria-hidden />
        <p className="text-base font-semibold text-obsessed">¡Gracias por tu respuesta!</p>
        <p className="text-sm text-muted">Tu opinión nos ayuda a mejorar en cada etapa.</p>
      </div>
    );
  }

  const selected = current.score;
  return (
    <div className="space-y-5">
      {current.introMessage ? <p className="text-sm text-muted">{current.introMessage}</p> : null}
      <fieldset>
        <legend className={cn("font-semibold text-obsessed", compact ? "text-sm" : "text-base")}>
          ¿Qué tan probable es que recomiende los servicios de CENTURY 21 Genera a un familiar o amigo?
        </legend>
        <div className="mt-3 grid grid-cols-6 gap-1.5 sm:grid-cols-11" role="radiogroup" aria-label="Calificación del 0 al 10">
          {Array.from({ length: 11 }, (_, n) => {
            const cat = categoryOf(n);
            return (
              <button
                key={n}
                type="button"
                role="radio"
                aria-checked={selected === n}
                disabled={saving}
                onClick={() => void pick(n)}
                className={cn(
                  "flex h-11 items-center justify-center rounded-lg border text-sm font-semibold tabular-nums transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                  selected === n
                    ? cat === "DETRACTOR"
                      ? "border-danger-text bg-danger-text text-white"
                      : cat === "PASSIVE"
                        ? "border-warning-text bg-warning-text text-white"
                        : "border-success-text bg-success-text text-white"
                    : "border-border bg-card text-obsessed hover:border-gold",
                )}
              >
                {n}
              </button>
            );
          })}
        </div>
        <div className="mt-1.5 flex justify-between text-xs text-muted">
          <span>0 = Definitivamente no lo recomendaría</span>
          <span className="text-right">10 = Con total seguridad</span>
        </div>
      </fieldset>
      {selected !== null && current.askComment && current.followUpQuestion ? (
        <div className="space-y-2">
          <label htmlFor={`survey-comment-${current.token ?? "qr"}`} className="text-sm font-medium text-obsessed">
            {current.followUpQuestion} <span className="font-normal text-muted">(opcional)</span>
          </label>
          <textarea
            id={`survey-comment-${current.token ?? "qr"}`}
            rows={3}
            maxLength={2000}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
          <Button onClick={() => void send()} disabled={saving}>
            <Send className="h-4 w-4" aria-hidden /> Enviar
          </Button>
        </div>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-danger-text">
          {error}
        </p>
      ) : null}
    </div>
  );
}

// --- QR ------------------------------------------------------------------------

export function QrImage({ value, size = 180, downloadName }: { value: string; size?: number; downloadName?: string }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    QRCode.toDataURL(value, { width: size * 2, margin: 1, errorCorrectionLevel: "M" })
      .then((url) => alive && setSrc(url))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [value, size]);
  if (!src) return <div className="animate-pulse rounded-lg bg-app-bg" style={{ width: size, height: size }} />;
  return (
    <div className="flex flex-col items-center gap-2">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt={`Código QR de ${value}`} width={size} height={size} className="rounded-lg bg-white p-1" />
      {downloadName ? (
        <a href={src} download={`${downloadName}.png`} className="inline-flex items-center gap-1 text-xs font-medium text-dark-gold hover:text-obsessed">
          <Download className="h-3.5 w-3.5" aria-hidden /> Descargar QR
        </a>
      ) : null}
    </div>
  );
}

// --- Generar encuesta (asesor) --------------------------------------------------

let configRequest: Promise<TouchpointConfig[]> | null = null;

function useTouchpoints(): TouchpointConfig[] | null {
  const [touchpoints, setTouchpoints] = useState<TouchpointConfig[] | null>(null);
  useEffect(() => {
    let alive = true;
    const request = configRequest ?? (configRequest = getSurveyConfig().then((c) => c.touchpoints));
    request
      .then((t) => alive && setTouchpoints(t))
      .catch(() => {
        configRequest = null;
      });
    return () => {
      alive = false;
    };
  }, []);
  return touchpoints;
}

export function invalidateSurveyConfig() {
  configRequest = null;
}

/**
 * El asesor activa la encuesta de una interacción: elige el punto de contacto
 * y el canal; con "liga del asesor" obtiene la liga, el QR y el botón para
 * compartirla por WhatsApp desde su teléfono.
 */
export function GenerateSurveyModal({
  open,
  onClose,
  target,
  defaultContact,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  target: { expedienteId?: string; leadId?: string };
  defaultContact?: { name?: string | null; phone?: string | null; email?: string | null };
  onCreated: (s: SurveyItem) => void;
}) {
  if (!open) return null;
  return <GenerateSurveyBody onClose={onClose} target={target} defaultContact={defaultContact} onCreated={onCreated} />;
}

function GenerateSurveyBody({
  onClose,
  target,
  defaultContact,
  onCreated,
}: {
  onClose: () => void;
  target: { expedienteId?: string; leadId?: string };
  defaultContact?: { name?: string | null; phone?: string | null; email?: string | null };
  onCreated: (s: SurveyItem) => void;
}) {
  const touchpoints = useTouchpoints();
  const { showToast } = useToast();
  const [touchpoint, setTouchpoint] = useState<Touchpoint | "">("");
  const [channel, setChannel] = useState<SurveyChannel>("ADVISOR_LINK");
  const [contact, setContact] = useState({ name: defaultContact?.name ?? "", phone: defaultContact?.phone ?? "", email: defaultContact?.email ?? "" });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<SurveyItem | null>(null);
  const forLead = !target.expedienteId;
  const channels: SurveyChannel[] = forLead ? ["ADVISOR_LINK", "WHATSAPP", "EMAIL"] : ["ADVISOR_LINK", "WHATSAPP", "EMAIL", "SYSTEM"];

  const groups = touchpoints
    ? [
        { label: "Vía asesor (presencial)", options: touchpoints.filter((t) => !t.systemTriggered).map((t) => ({ value: t.touchpoint, label: `${t.number}. ${t.label}${t.active ? "" : " (inactivo)"}` })) },
        { label: "Vía sistema", options: touchpoints.filter((t) => t.systemTriggered).map((t) => ({ value: t.touchpoint, label: `${t.number}. ${t.label}${t.active ? "" : " (inactivo)"}` })) },
      ]
    : [];

  const submit = async () => {
    if (!touchpoint) return;
    setSaving(true);
    setError(null);
    try {
      const input: CreateSurveyInput = {
        touchpoint,
        channel,
        expedienteId: target.expedienteId,
        leadId: target.leadId,
        clientName: contact.name.trim() || undefined,
        clientPhone: contact.phone.trim() || undefined,
        clientEmail: contact.email.trim() || undefined,
      };
      const survey = await createSurvey(input);
      setCreated(survey);
      onCreated(survey);
      if (survey.recentlySurveyed) showToast("Aviso: este cliente ya recibió una encuesta hace poco.");
    } catch (err) {
      setError(err instanceof ApiError && err.message ? err.message : "No se pudo generar la encuesta.");
    } finally {
      setSaving(false);
    }
  };

  const share = created?.link
    ? `Hola${contact.name ? ` ${contact.name.split(" ")[0]}` : ""}, en CENTURY 21 Genera queremos saber cómo te fue. ¿Nos ayudas con esta encuesta de 1 minuto? ${created.link}`
    : "";

  return (
    <Modal
      open
      onClose={onClose}
      title={created ? "Encuesta lista" : "Enviar encuesta de satisfacción"}
      size="lg"
      footer={
        created ? (
          <Button onClick={onClose}>Listo</Button>
        ) : (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancelar
            </Button>
            <Button onClick={() => void submit()} disabled={saving || !touchpoint}>
              {saving ? "Generando…" : channel === "ADVISOR_LINK" ? "Generar liga" : "Enviar"}
            </Button>
          </>
        )
      }
    >
      {created ? (
        <div className="space-y-4">
          <p className="text-sm text-obsessed">
            {created.touchpointNumber}. {created.touchpointLabel} · {channelShort[created.channel]} · <span className="text-muted">{created.deliveryDetail}</span>
          </p>
          {created.recentlySurveyed ? <p className="rounded-xl bg-warning-bg px-3 py-2 text-xs text-warning-text">Este cliente ya recibió una encuesta en los últimos días.</p> : null}
          {created.link ? (
            <div className="flex flex-col items-center gap-3 sm:flex-row sm:items-start">
              <QrImage value={created.link} size={160} downloadName={`encuesta-${created.touchpoint.toLowerCase()}`} />
              <div className="min-w-0 flex-1 space-y-2">
                <p className="text-xs text-muted">Que el cliente escanee el QR o compártele la liga:</p>
                <p className="break-all rounded-lg bg-app-bg px-3 py-2 text-xs text-obsessed">{created.link}</p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => {
                      void navigator.clipboard?.writeText(created.link ?? "");
                      showToast("Liga copiada");
                    }}
                  >
                    <Copy className="h-4 w-4" aria-hidden /> Copiar liga
                  </Button>
                  <a
                    href={`https://wa.me/${contact.phone.replace(/\D/g, "").replace(/^(\d{10})$/, "52$1")}?text=${encodeURIComponent(share)}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-gold px-3 py-1.5 text-sm font-medium text-brand-ink hover:bg-gold-hover"
                  >
                    <MessageCircle className="h-4 w-4" aria-hidden /> Compartir por WhatsApp
                  </a>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Select
            label="Punto de contacto"
            value={touchpoint}
            onChange={(e) => setTouchpoint(e.target.value as Touchpoint)}
            options={[{ value: "", label: touchpoints ? "Elige la interacción" : "Cargando…" }]}
            groups={groups}
            containerClassName="sm:col-span-2"
          />
          <Select label="Canal" value={channel} onChange={(e) => setChannel(e.target.value as SurveyChannel)} options={channels.map((c) => ({ value: c, label: channelLabels[c] }))} containerClassName="sm:col-span-2" />
          <Input label="Nombre del cliente" value={contact.name} onChange={(e) => setContact({ ...contact, name: e.target.value })} placeholder={forLead ? "" : "Del expediente"} />
          <Input label="Teléfono" type="tel" value={contact.phone} onChange={(e) => setContact({ ...contact, phone: e.target.value })} placeholder={forLead ? "" : "Del expediente"} />
          <Input label="Correo" type="email" value={contact.email} onChange={(e) => setContact({ ...contact, email: e.target.value })} placeholder={forLead ? "" : "Del expediente"} containerClassName="sm:col-span-2" />
          <p className="text-xs text-muted sm:col-span-2">
            {channel === "ADVISOR_LINK"
              ? "Obtendrás la liga y su QR para compartirla en el momento."
              : channel === "SYSTEM"
                ? "Aparecerá en el portal del cliente la próxima vez que entre con su liga."
                : "Se envía de inmediato. Si el canal no está disponible, se intenta por el otro."}
          </p>
          {error ? (
            <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text sm:col-span-2">
              {error}
            </p>
          ) : null}
        </div>
      )}
    </Modal>
  );
}

const dateTime = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "America/Mexico_City" });

/** Historial de encuestas de un expediente o lead, con indicador de color por categoría. */
export function SurveyHistoryList({ surveys, canSeeScores }: { surveys: SurveyItem[]; canSeeScores: boolean }) {
  const { showToast } = useToast();
  if (surveys.length === 0) return <p className="text-sm text-muted">Todavía no se han enviado encuestas.</p>;
  return (
    <ul className="space-y-2">
      {surveys.map((s) => (
        <li
          key={s.id}
          className={cn(
            "rounded-xl border p-3 text-sm",
            s.category === "DETRACTOR" ? "border-danger-text/40 bg-danger-bg" : s.category === "PROMOTER" ? "border-success-text/30" : "border-border",
          )}
        >
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-obsessed">
              {s.touchpointNumber}. {s.touchpointLabel}
            </span>
            {canSeeScores && s.score !== null ? <ScoreBadge score={s.score} category={s.category} /> : <Badge tone={statusTones[s.status]}>{statusLabels[s.status]}</Badge>}
            <span className="ml-auto text-xs text-muted">{dateTime.format(new Date(s.answeredAt ?? s.sentAt ?? s.sendAt))}</span>
          </div>
          <p className="mt-1 text-xs text-muted">
            {channelShort[s.channel]}
            {s.advisorName ? ` · Asesor: ${s.advisorName}` : ""}
            {s.createdByName ? ` · Generada por ${s.createdByName}` : " · Automática"}
            {s.remindersSent ? ` · ${s.remindersSent} recordatorio${s.remindersSent === 1 ? "" : "s"}` : ""}
            {s.deliveryDetail && s.status !== "ANSWERED" ? ` · ${s.deliveryDetail}` : ""}
          </p>
          {canSeeScores && s.comment ? <p className="mt-1.5 whitespace-pre-wrap rounded-lg bg-card/70 px-3 py-2 text-obsessed">“{s.comment}”</p> : null}
          {s.status === "SENT" && s.link ? (
            <button
              type="button"
              className="mt-1.5 inline-flex items-center gap-1 text-xs font-medium text-dark-gold hover:text-obsessed"
              onClick={() => {
                void navigator.clipboard?.writeText(s.link ?? "");
                showToast("Liga copiada");
              }}
            >
              <Copy className="h-3.5 w-3.5" aria-hidden /> Copiar liga
            </button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
