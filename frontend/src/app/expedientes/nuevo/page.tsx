"use client";

import { ParticipantFields } from "@/components/expediente/ParticipantFields";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { SaveStatus } from "@/components/ui/SaveStatus";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { createExpediente } from "@/lib/api/expedientes";
import type { BackendAccreditationType, BackendPersonType, BackendPropertyCaseType, ParticipantRequest } from "@/lib/api/types";
import { accreditationLabels, propertyTypeLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { previewRequirements, type PreviewItem } from "@/lib/requirements-preview";
import { internalDraftTransport, useServerDraft, type ServerDraft } from "@/lib/use-server-draft";
import { cn } from "@/lib/utils";
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Building2,
  Check,
  FileText,
  Heart,
  Home,
  IdCard,
  Info,
  Landmark,
  Loader2,
  Map as MapIcon,
  Pencil,
  Plus,
  ReceiptText,
  ScrollText,
  Trash2,
  User,
  UserCheck,
  Zap,
  Droplet,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";

const DRAFT_KEY = "nuevo-expediente";

interface NewExpedienteDraft {
  personType: BackendPersonType;
  signedByAttorney: boolean;
  owners: ParticipantRequest[];
  representatives: ParticipantRequest[];
  attorney: ParticipantRequest;
  accreditationType: BackendAccreditationType;
  propertyCaseType: BackendPropertyCaseType;
  condominiumRegime: boolean;
}

const person = (role: ParticipantRequest["role"]): ParticipantRequest => ({ role, fullName: "" });

const steps = [
  { id: 1, label: "Cliente" },
  { id: 2, label: "Inmueble" },
  { id: 3, label: "Revisión" },
] as const;
type StepId = (typeof steps)[number]["id"];

export default function NuevoExpedientePage() {
  const can = useCan();
  // Lo capturado se autoguarda en el servidor (bajo el usuario) hasta que se
  // crea el expediente: sobrevive a recargar, a cambiar de equipo y a caídas de red.
  const draft = useServerDraft<NewExpedienteDraft>(DRAFT_KEY, () => internalDraftTransport(DRAFT_KEY));
  if (!can("EXPEDIENT_CREATE")) {
    return (
      <PageContainer title="Nuevo expediente">
        <StateMessage kind="restricted" title="Acceso restringido" description="Tu usuario no tiene permiso para crear expedientes." />
      </PageContainer>
    );
  }
  if (draft.restored === undefined) {
    return (
      <PageContainer title="Nuevo expediente">
        <StateMessage kind="loading" title="Recuperando lo que ya habías capturado…" />
      </PageContainer>
    );
  }
  return <NuevoExpedienteForm draft={draft.restored} serverDraft={draft} />;
}

function NuevoExpedienteForm({ draft, serverDraft }: { draft: NewExpedienteDraft | null; serverDraft: ServerDraft<NewExpedienteDraft> }) {
  const { showToast } = useToast();
  const router = useRouter();
  const { update: updateDraft } = serverDraft;

  const [step, setStep] = useState<StepId>(1);
  const [personType, setPersonType] = useState<BackendPersonType>(draft?.personType ?? "FISICA");
  const [signedByAttorney, setSignedByAttorney] = useState(draft?.signedByAttorney ?? false);
  const [owners, setOwners] = useState<ParticipantRequest[]>(draft?.owners ?? [person("OWNER")]);
  const [representatives, setRepresentatives] = useState<ParticipantRequest[]>(draft?.representatives ?? [person("LEGAL_REPRESENTATIVE")]);
  const [attorney, setAttorney] = useState<ParticipantRequest>(draft?.attorney ?? person("ATTORNEY"));
  const [accreditationType, setAccreditationType] = useState<BackendAccreditationType>(draft?.accreditationType ?? "ESCRITURA_PUBLICA");
  const [propertyCaseType, setPropertyCaseType] = useState<BackendPropertyCaseType>(draft?.propertyCaseType ?? "HOUSING");
  const [condominiumRegime, setCondominiumRegime] = useState(draft?.condominiumRegime ?? false);
  const [showErrors, setShowErrors] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submittingRef = useRef(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const firstRender = useRef(true);

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    updateDraft({
      personType,
      signedByAttorney,
      owners,
      representatives,
      attorney,
      accreditationType,
      propertyCaseType,
      condominiumRegime,
    } satisfies NewExpedienteDraft);
  }, [updateDraft, personType, signedByAttorney, owners, representatives, attorney, accreditationType, propertyCaseType, condominiumRegime]);

  const isMoral = personType === "MORAL";
  const participants = useMemo<ParticipantRequest[]>(() => {
    if (isMoral) return [{ ...owners[0], role: "OWNER", civilStatus: null, maritalRegime: null }, ...representatives];
    const titulares = owners.map((o, i) => ({ ...o, role: i === 0 ? ("OWNER" as const) : ("CO_OWNER" as const) }));
    return signedByAttorney ? [...titulares, attorney] : titulares;
  }, [isMoral, owners, representatives, attorney, signedByAttorney]);

  const preview = useMemo(
    () => previewRequirements({ personType, signedByAttorney, accreditationType, condominiumRegime, propertyCaseType, participants }),
    [personType, signedByAttorney, accreditationType, condominiumRegime, propertyCaseType, participants],
  );

  const clientValid = participants.every((p) => p.fullName.trim().length > 0);
  const nameError = (value: string) => (showErrors && !value.trim() ? "Captura el nombre completo." : undefined);
  const setOwner = (index: number, value: ParticipantRequest) => setOwners((prev) => prev.map((o, i) => (i === index ? value : o)));

  const goTo = (next: StepId) => {
    setError(null);
    setStep(next);
    // Al cambiar de paso, el foco va al título del paso (lectores de pantalla y teclado).
    requestAnimationFrame(() => {
      headingRef.current?.focus();
      headingRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  };

  const next = () => {
    if (step === 1 && !clientValid) {
      setShowErrors(true);
      setError("Captura el nombre de cada persona para continuar.");
      return;
    }
    setShowErrors(false);
    if (step < 3) goTo((step + 1) as StepId);
  };

  const handleCreate = async () => {
    if (submittingRef.current || created) return;
    setError(null);
    if (!clientValid) {
      setShowErrors(true);
      goTo(1);
      setError("Captura el nombre de cada persona antes de crear el expediente.");
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const result = await createExpediente({
        personType,
        signedByAttorney: personType === "FISICA" && signedByAttorney,
        accreditationType,
        condominiumRegime,
        propertyCaseType,
        // Solo el nombre: estado civil, contacto, domicilio y situación jurídica
        // se piden al cliente en su liga o se leen de sus documentos.
        participants: participants.map((p) => ({ role: p.role, fullName: p.fullName.trim() })),
      });
      setCreated(true);
      await serverDraft.clear();
      showToast(`Expediente ${result.folio} creado.`);
      router.push(`/expedientes/${result.id}`);
    } catch (err) {
      // Lo capturado se conserva: el usuario puede reintentar sin volver a escribir nada.
      setError(err instanceof ApiError ? `No se pudo crear el expediente: ${err.message}` : "No se pudo conectar con el servidor. Lo capturado sigue aquí; intenta de nuevo.");
      submittingRef.current = false;
    } finally {
      setSubmitting(false);
    }
  };

  const ownerTitle = (index: number) => (isMoral ? "Empresa titular" : index === 0 ? "Propietario principal" : `Copropietario ${index + 1}`);

  return (
    <PageContainer
      title="Nuevo expediente"
      subtitle="Solo lo necesario para saber qué documentos pedir. El domicilio, la situación jurídica y los demás datos se leen de los documentos o los captura el cliente en su liga."
    >
      <Stepper current={step} onSelect={(s) => (s < step ? goTo(s) : undefined)} />

      <div className="mt-6 grid grid-cols-1 gap-6 pb-28 lg:grid-cols-[minmax(0,1fr)_340px] xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="flex min-w-0 flex-col gap-6">
          <h2 ref={headingRef} tabIndex={-1} className="sr-only scroll-mt-24">
            Paso {step} de 3: {steps[step - 1].label}
          </h2>

          {step === 1 ? (
            <>
              <Card className="animate-fade-in">
                <CardHeader title="¿Quién es el cliente?" description="Define qué documentos y qué datos pide el contrato." />
                <div role="radiogroup" aria-label="Tipo de cliente" className="grid gap-3 sm:grid-cols-2">
                  {(["FISICA", "MORAL"] as const).map((type) => {
                    const selected = personType === type;
                    const Icon = type === "FISICA" ? User : Building2;
                    return (
                      <button
                        key={type}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        onClick={() => setPersonType(type)}
                        className={cn(
                          "relative flex gap-3 rounded-xl border p-4 text-left transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                          selected ? "border-gold bg-gold/10" : "border-border hover:border-gold/50 hover:bg-app-bg",
                        )}
                      >
                        <Icon className={cn("mt-0.5 h-6 w-6 shrink-0", selected ? "text-dark-gold" : "text-muted")} aria-hidden />
                        <span className="min-w-0 pr-6">
                          <span className="block text-sm font-semibold text-obsessed">{type === "FISICA" ? "Persona física" : "Persona moral (empresa)"}</span>
                          <span className="mt-1 block text-sm text-muted">
                            {type === "FISICA"
                              ? "Uno o varios propietarios personas. Pueden firmar ellos mismos o un apoderado."
                              : "Una empresa es la propietaria; firma su representante legal. No aplica estado civil."}
                          </span>
                        </span>
                        <span
                          className={cn(
                            "absolute right-3 top-3 flex h-5 w-5 items-center justify-center rounded-full border",
                            selected ? "border-dark-gold bg-dark-gold text-white" : "border-border bg-card",
                          )}
                          aria-hidden
                        >
                          {selected ? <Check className="h-3 w-3" /> : null}
                        </span>
                      </button>
                    );
                  })}
                </div>
                {!isMoral ? (
                  <div className="mt-4 flex items-start gap-3 border-t border-border pt-4">
                    <UserCheck className="mt-1.5 h-5 w-5 shrink-0 text-muted" aria-hidden />
                    <div className="flex-1">
                      <Toggle
                        id="attorney-toggle"
                        checked={signedByAttorney}
                        onChange={setSignedByAttorney}
                        label="Firma mediante apoderado"
                        description="Se pedirá su identificación y el poder notarial."
                      />
                    </div>
                  </div>
                ) : null}
              </Card>

              <Card className="animate-fade-in">
                <CardHeader
                  title={isMoral ? "Empresa y representante legal" : "Propietarios"}
                  description={
                    isMoral
                      ? "La empresa titular del inmueble y quién la representa. Solo el nombre: lo demás se lee de sus documentos."
                      : "Registra a todos los titulares del inmueble; no hay límite. Cada uno tendrá sus propios documentos y firma. Solo el nombre: estado civil y contacto los captura el cliente."
                  }
                />
                <div className="flex flex-col gap-4">
                  {(isMoral ? owners.slice(0, 1) : owners).map((owner, index) => (
                    <fieldset key={index} className="rounded-xl border border-border p-4">
                      <div className="mb-3 flex items-center justify-between gap-2">
                        <legend className="text-sm font-semibold text-obsessed">{ownerTitle(index)}</legend>
                        {!isMoral && index > 0 ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setOwners((prev) => prev.filter((_, i) => i !== index))}
                            aria-label={`Quitar ${ownerTitle(index).toLowerCase()}`}
                          >
                            <Trash2 className="h-4 w-4" aria-hidden /> Quitar
                          </Button>
                        ) : null}
                      </div>
                      <ParticipantFields
                        value={{ ...owner, role: isMoral || index === 0 ? "OWNER" : "CO_OWNER" }}
                        onChange={(v) => setOwner(index, v)}
                        personType={personType}
                        nameOnly
                        nameError={nameError(owner.fullName)}
                      />
                    </fieldset>
                  ))}
                  {!isMoral ? (
                    <Button variant="ghost" size="sm" className="self-start text-dark-gold" onClick={() => setOwners((prev) => [...prev, person("CO_OWNER")])}>
                      <Plus className="h-4 w-4" aria-hidden /> Agregar copropietario
                    </Button>
                  ) : null}

                  {isMoral
                    ? representatives.map((rep, index) => (
                        <fieldset key={`rep-${index}`} className="rounded-xl border border-border p-4">
                          <div className="mb-3 flex items-center justify-between gap-2">
                            <legend className="text-sm font-semibold text-obsessed">Representante legal {representatives.length > 1 ? index + 1 : ""}</legend>
                            {index > 0 ? (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setRepresentatives((prev) => prev.filter((_, i) => i !== index))}
                                aria-label={`Quitar representante legal ${index + 1}`}
                              >
                                <Trash2 className="h-4 w-4" aria-hidden /> Quitar
                              </Button>
                            ) : null}
                          </div>
                          <ParticipantFields
                            value={rep}
                            onChange={(v) => setRepresentatives((prev) => prev.map((r, i) => (i === index ? v : r)))}
                            personType={personType}
                            nameOnly
                            nameError={nameError(rep.fullName)}
                          />
                        </fieldset>
                      ))
                    : null}
                  {isMoral ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="self-start text-dark-gold"
                      onClick={() => setRepresentatives((prev) => [...prev, person("LEGAL_REPRESENTATIVE")])}
                    >
                      <Plus className="h-4 w-4" aria-hidden /> Agregar otro representante
                    </Button>
                  ) : null}

                  {!isMoral && signedByAttorney ? (
                    <fieldset className="rounded-xl border border-border p-4">
                      <legend className="mb-3 text-sm font-semibold text-obsessed">Apoderado</legend>
                      <ParticipantFields value={attorney} onChange={setAttorney} personType={personType} nameOnly nameError={nameError(attorney.fullName)} />
                    </fieldset>
                  ) : null}
                </div>
              </Card>
            </>
          ) : null}

          {step === 2 ? (
            <Card className="animate-fade-in">
              <CardHeader title="Inmueble" description="Con esto se define qué documentos del inmueble se piden." />
              <div className="grid gap-4 sm:grid-cols-2">
                <Select
                  label="Tipo de inmueble"
                  value={propertyCaseType}
                  onChange={(e) => setPropertyCaseType(e.target.value as BackendPropertyCaseType)}
                  options={Object.entries(propertyTypeLabels).map(([value, label]) => ({ value, label }))}
                />
                <Select
                  label="¿Con qué documento se acredita la propiedad?"
                  value={accreditationType}
                  onChange={(e) => setAccreditationType(e.target.value as BackendAccreditationType)}
                  options={Object.entries(accreditationLabels).map(([value, label]) => ({ value, label }))}
                />
              </div>
              <div className="mt-4 border-t border-border pt-4">
                <Toggle
                  id="condominium-toggle"
                  checked={condominiumRegime}
                  onChange={setCondominiumRegime}
                  label="Está en régimen de condominio"
                  description="Se pedirá la escritura del régimen de condominio."
                />
              </div>
              {propertyCaseType === "COMMERCIAL" ? (
                <p className="mt-4 flex items-start gap-2 rounded-xl bg-warning-bg px-3 py-2.5 text-sm text-warning-text">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                  El contrato registrado ante PROFECO solo ampara inmuebles destinados a casa habitación. Puedes integrar el expediente, pero el sistema no
                  generará el contrato para un inmueble comercial.
                </p>
              ) : null}
              <p className="mt-4 flex items-start gap-2 rounded-xl bg-info-bg px-3 py-2.5 text-sm text-info-text">
                <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                No necesitas capturar el domicilio ni la situación jurídica: se toman de la escritura, el predial y el certificado de libertad de gravamen cuando se
                leen.
              </p>
            </Card>
          ) : null}

          {step === 3 ? (
            <Card className="animate-fade-in">
              <CardHeader title="Revisa antes de crear" description="Puedes volver a cualquier paso; lo capturado se conserva." />
              <ReviewSection title="Cliente" onEdit={() => goTo(1)}>
                <ReviewRow label="Tipo de cliente" value={isMoral ? "Persona moral (empresa)" : "Persona física"} />
                {!isMoral ? <ReviewRow label="Firma" value={signedByAttorney ? "Mediante apoderado" : "Los propietarios"} /> : null}
                {participants.map((p, i) => (
                  <ReviewRow
                    key={i}
                    label={
                      p.role === "OWNER"
                        ? isMoral
                          ? "Empresa titular"
                          : "Propietario principal"
                        : p.role === "CO_OWNER"
                          ? "Copropietario"
                          : p.role === "ATTORNEY"
                            ? "Apoderado"
                            : "Representante legal"
                    }
                    value={p.fullName.trim() || "—"}
                  />
                ))}
              </ReviewSection>
              <ReviewSection title="Inmueble" onEdit={() => goTo(2)}>
                <ReviewRow label="Tipo de inmueble" value={propertyTypeLabels[propertyCaseType]} />
                <ReviewRow label="Acreditación" value={accreditationLabels[accreditationType]} />
                <ReviewRow label="Régimen de condominio" value={condominiumRegime ? "Sí" : "No"} />
              </ReviewSection>
              <p className="mt-4 text-sm text-muted">
                Al crear el expediente se registran <strong className="text-obsessed">{preview.length} documentos previstos</strong>. Después podrás generar la
                liga para que el cliente los cargue.
              </p>
            </Card>
          ) : null}
        </div>

        <aside className="min-w-0 lg:sticky lg:top-24 lg:self-start" aria-labelledby="docs-previstos">
          <Card>
            <CardHeader
              title={<span id="docs-previstos">Documentos previstos</span>}
              description="Se ajustan con lo que capturas, según las reglas del sistema."
            />
            <ul className="flex flex-col gap-2">
              {preview.map((item, i) => (
                <PreviewRow key={`${item.type}-${i}`} item={item} />
              ))}
            </ul>
            <p className="mt-4 flex items-start gap-2 rounded-xl bg-warning-bg px-3 py-2.5 text-sm text-warning-text">
              <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              Lo que no aplique a este caso se puede marcar como “No aplica”, con justificación, durante la revisión.
            </p>
          </Card>
        </aside>
      </div>

      {/* Barra de acciones fija: el pb-28 del contenido deja espacio para que no tape campos. */}
      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-card/95 backdrop-blur-sm lg:left-60">
        <div className="mx-auto flex max-w-[1440px] flex-wrap items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
          <div className="min-w-0 flex-1">
            {error ? (
              <p role="alert" className="flex items-start gap-1.5 text-sm font-medium text-danger-text">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                {error}
              </p>
            ) : (
              <span className="text-xs text-muted">
                <SaveStatus status={serverDraft.status} lastSavedAt={serverDraft.lastSavedAt} />
                {serverDraft.status === "idle" ? "Lo que captures se guarda automáticamente como borrador." : null}
              </span>
            )}
          </div>
          <div className="flex shrink-0 gap-2">
            {step > 1 ? (
              <Button variant="secondary" onClick={() => goTo((step - 1) as StepId)} disabled={submitting || created}>
                <ArrowLeft className="h-4 w-4" aria-hidden /> Anterior
              </Button>
            ) : null}
            {step < 3 ? (
              <Button variant="dark" onClick={next}>
                Continuar <ArrowRight className="h-4 w-4" aria-hidden />
              </Button>
            ) : (
              <Button onClick={handleCreate} disabled={submitting || created} aria-busy={submitting}>
                {submitting || created ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Check className="h-4 w-4" aria-hidden />}
                {created ? "Abriendo expediente…" : submitting ? "Creando…" : "Crear expediente"}
              </Button>
            )}
          </div>
        </div>
      </div>
    </PageContainer>
  );
}

function Stepper({ current, onSelect }: { current: StepId; onSelect: (step: StepId) => void }) {
  return (
    <nav aria-label="Pasos">
      <ol className="flex items-start">
        {steps.map((s, i) => {
          const done = s.id < current;
          const active = s.id === current;
          return (
            <li key={s.id} className="relative flex flex-1 flex-col items-center">
              {i > 0 ? (
                <span
                  className={cn("absolute right-1/2 top-4 h-0.5 w-full -translate-y-1/2 transition-colors duration-200", s.id <= current ? "bg-gold" : "bg-border")}
                  aria-hidden
                />
              ) : null}
              <button
                type="button"
                onClick={() => onSelect(s.id)}
                disabled={!done}
                aria-current={active ? "step" : undefined}
                className={cn(
                  "relative z-10 flex flex-col items-center gap-1.5 rounded-xl px-2 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                  done ? "cursor-pointer" : "cursor-default",
                )}
              >
                <span
                  className={cn(
                    "flex h-8 w-8 items-center justify-center rounded-full border-2 text-sm font-semibold transition-colors duration-200",
                    active ? "border-dark-gold bg-dark-gold text-white" : done ? "border-gold bg-gold text-brand-ink" : "border-border bg-card text-muted",
                  )}
                >
                  {done ? <Check className="h-4 w-4" aria-hidden /> : s.id}
                </span>
                <span className={cn("text-sm", active ? "font-semibold text-obsessed" : done ? "text-obsessed" : "text-muted")}>
                  {s.label}
                  {done ? <span className="sr-only"> (completado, volver a este paso)</span> : null}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

const previewIcons: Record<string, typeof FileText> = {
  INE: IdCard,
  TAX_STATUS_CERTIFICATE: Landmark,
  MARRIAGE_CERTIFICATE: Heart,
  INCORPORATION_DEED: Building2,
  DEED: ScrollText,
  PRIVATE_CONTRACT: ScrollText,
  CADASTRAL_PLAN: MapIcon,
  RPP_REGISTRATION_SLIP: Home,
  PROPERTY_TAX: ReceiptText,
  ELECTRICITY_RECEIPT: Zap,
  WATER_RECEIPT: Droplet,
  POWER_OF_ATTORNEY: UserCheck,
  CONDOMINIUM_REGIME: Building2,
};

function PreviewRow({ item }: { item: PreviewItem }) {
  const Icon = previewIcons[item.type] ?? FileText;
  return (
    <li className="flex items-start gap-3 rounded-xl border border-border p-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-app-bg text-muted">
        <Icon className="h-[18px] w-[18px]" aria-hidden />
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-medium text-obsessed">{item.label}</span>
        {item.detail ? <span className="block break-words text-xs text-muted">{item.detail}</span> : null}
        {item.note ? <span className="mt-0.5 block text-xs font-medium text-warning-text">{item.note}</span> : null}
      </span>
    </li>
  );
}

function ReviewSection({ title, onEdit, children }: { title: string; onEdit: () => void; children: ReactNode }) {
  return (
    <section className="mt-2 border-t border-border pt-4 first-of-type:border-t-0 first-of-type:pt-0">
      <div className="mb-2 flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-muted">{title}</h3>
        <Button variant="ghost" size="sm" onClick={onEdit} aria-label={`Editar ${title.toLowerCase()}`}>
          <Pencil className="h-3.5 w-3.5" aria-hidden /> Editar
        </Button>
      </div>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-3 pb-2 sm:grid-cols-2">{children}</dl>
    </section>
  );
}

function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="break-words text-sm font-medium text-obsessed">{value}</dd>
    </div>
  );
}
