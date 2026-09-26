"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import { getContractReadiness } from "@/lib/api/contracts";
import { getClientData, getLegalDetails, updateClientData, updateLegalDetails } from "@/lib/api/expedientes";
import type { ContractReadinessResponse, LegalDetails, ManualClientDataResponse } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { useCan } from "@/lib/permissions";
import { CheckCircle2, CircleAlert, Save } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

const HOUSING_CHECKLIST: [string, string][] = [
  ["LICENSES", "Autorizaciones, licencias y permisos de construcción, especificaciones técnicas, materiales, seguridad, uso de suelo y servicios"],
  ["PLANS", "Planos estructurales, arquitectónicos y de instalaciones (o dictamen estructural)"],
  ["CIVIL_PROTECTION", "Programa Interno de Protección Civil"],
  ["LIENS", "Información sobre gravámenes que afecten la propiedad"],
  ["TAXES", "Condiciones de pago de contribuciones, derechos y servicios"],
];

const LAND_CHECKLIST: [string, string][] = [
  ["LAND_USE", "Uso de suelo conforme al plan de desarrollo urbano y licencia vigente"],
  ["SERVICES_FEASIBILITY", "Estudio de factibilidad para la instalación de servicios básicos"],
  ["CONSTRUCTION_RULES", "Reglamento de construcción del fraccionamiento o condominio (en su caso)"],
  ["CIVIL_PROTECTION", "Programa Interno de Protección Civil"],
  ["LIENS", "Información sobre gravámenes que afecten la propiedad"],
  ["TAXES", "Condiciones de pago de contribuciones, derechos y servicios"],
];

type Section = Record<string, string | null | undefined>;

export function ContractDataTab({ expediente, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const editable = can("EXPEDIENT_EDIT") && expediente.correctable;
  const [readiness, setReadiness] = useState<ContractReadinessResponse | null>(null);
  const [data, setData] = useState<ManualClientDataResponse | null>(null);
  const [legal, setLegal] = useState<LegalDetails | null>(null);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    getContractReadiness(expediente.id).then(setReadiness).catch(() => undefined);
    getClientData(expediente.id).then(setData).catch((err) => showToast(errorText(err)));
    getLegalDetails(expediente.id).then(setLegal).catch((err) => showToast(errorText(err)));
  }, [expediente.id, showToast]);

  useEffect(() => {
    load();
  }, [load]);

  if (!data || !legal) return <p className="text-sm text-muted">Cargando…</p>;

  const moral = expediente.personType === "MORAL";
  const represented = moral || expediente.signedByAttorney;
  const land = expediente.propertyCaseType === "RESIDENTIAL_LAND";
  const checklist = land ? LAND_CHECKLIST : HOUSING_CHECKLIST;

  const setData2 = (patch: Partial<ManualClientDataResponse>) => setData({ ...data, ...patch });
  const setSection = (key: keyof LegalDetails, patch: Section) =>
    setLegal({ ...legal, [key]: { ...((legal[key] as Section | null) ?? {}), ...patch } });

  const num = (v: string) => (v.trim() === "" ? null : Number(v));

  const save = async () => {
    setSaving(true);
    try {
      await updateClientData(expediente.id, {
        authorizedPrice: data.authorizedPrice === "" ? null : data.authorizedPrice,
        contractSignatureDate: data.contractSignatureDate || null,
        email: data.email,
        phone: data.phone,
        notificationAddress: data.notificationAddress,
        visitInstructions: data.visitInstructions,
        marketingDataAuthorized: data.marketingDataAuthorized,
        receiveAdsAuthorized: data.receiveAdsAuthorized,
        additionalServicesRequested: data.additionalServicesRequested,
        bedrooms: data.bedrooms,
        bathrooms: data.bathrooms,
        parkingSpots: data.parkingSpots,
        conservationStatus: data.conservationStatus,
        availableServices: data.availableServices,
        relevantFeatures: data.relevantFeatures,
        landAreaM2: data.landAreaM2 === "" ? null : data.landAreaM2,
        builtAreaM2: data.builtAreaM2 === "" ? null : data.builtAreaM2,
        reason: reason.trim() || undefined,
      });
      await updateLegalDetails(expediente.id, legal, reason.trim());
      showToast("Datos del contrato guardados.");
      setReason("");
      load();
      await reload();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      {readiness ? (
        <Card>
          <CardHeader
            title={readiness.ready ? "Listo para generar el contrato" : "Qué falta para generar el contrato"}
            description={`Variante del contrato: ${readiness.variant}`}
          />
          {readiness.ready ? (
            <p className="flex items-center gap-2 text-sm text-success-text">
              <CheckCircle2 className="h-4 w-4" aria-hidden /> No falta nada. Ve a &quot;Contrato y firmas&quot; para generarlo.
            </p>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">Requisitos del proceso</p>
                {readiness.blockers.length === 0 ? <p className="text-sm text-success-text">Completos.</p> : null}
                <ul className="flex flex-col gap-1 text-sm">
                  {readiness.blockers.map((b) => (
                    <li key={b} className="flex items-start gap-1.5 text-warning-text">
                      <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden /> {b}
                    </li>
                  ))}
                </ul>
              </div>
              <div>
                <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">Datos del contrato</p>
                {readiness.missingData.length === 0 ? <p className="text-sm text-success-text">Completos.</p> : null}
                <ul className="flex max-h-72 flex-col gap-1 overflow-y-auto text-sm">
                  {readiness.missingData.map((m) => (
                    <li key={m} className="flex items-start gap-1.5 text-warning-text">
                      <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden /> {m}
                    </li>
                  ))}
                </ul>
                <p className="mt-2 text-xs text-muted">Los datos personales de cada participante se capturan en la pestaña &quot;Participantes&quot;.</p>
              </div>
            </div>
          )}
        </Card>
      ) : null}

      <Card>
        <CardHeader title="Precio, firma y autorizaciones" />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field disabled={!editable} label="Precio autorizado (MXN)" type="number" value={data.authorizedPrice} onChange={(v) => setData2({ authorizedPrice: num(v) })} />
          <Field disabled={!editable} label="Fecha de firma del contrato" type="date" value={data.contractSignatureDate} onChange={(v) => setData2({ contractSignatureDate: v })} />
          <Field disabled={!editable}
            label="Medios de publicidad autorizados por el cliente"
            value={legal.advertisingMedia}
            onChange={(v) => setLegal({ ...legal, advertisingMedia: v })}
            span
            hint="Ej. portales inmobiliarios, redes sociales y letrero en el inmueble."
          />
          <YesNo disabled={!editable}
            label="¿Autoriza ceder su información a terceros con fines mercadotécnicos?"
            value={data.marketingDataAuthorized}
            onChange={(v) => setData2({ marketingDataAuthorized: v })}
          />
          <YesNo disabled={!editable} label="¿Acepta recibir publicidad?" value={data.receiveAdsAuthorized} onChange={(v) => setData2({ receiveAdsAuthorized: v })} />
          <Field disabled={!editable} label="Instrucciones para visitas" value={data.visitInstructions} onChange={(v) => setData2({ visitInstructions: v })} span />
          <Field disabled={!editable}
            label="Servicios adicionales solicitados (Anexo D)"
            value={data.additionalServicesRequested}
            onChange={(v) => setData2({ additionalServicesRequested: v })}
            span
          />
        </div>
      </Card>

      <Card>
        <CardHeader title="Datos del cliente para notificaciones (cláusula décima segunda)" />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field disabled={!editable} label="Correo electrónico" type="email" value={data.email} onChange={(v) => setData2({ email: v })} />
          <Field disabled={!editable} label="Teléfono" value={data.phone} onChange={(v) => setData2({ phone: v })} />
          <Field disabled={!editable}
            label="Domicilio para notificaciones"
            value={data.notificationAddress}
            onChange={(v) => setData2({ notificationAddress: v })}
            span
            hint="Si se deja vacío se usa el domicilio del propietario principal."
          />
        </div>
      </Card>

      <Card>
        <CardHeader title="Características del inmueble (Anexo A)" />
        <div className="grid gap-4 sm:grid-cols-3">
          <Field disabled={!editable} label="Superficie de terreno (m²)" type="number" value={data.landAreaM2} onChange={(v) => setData2({ landAreaM2: num(v) })} />
          <Field disabled={!editable}
            label={land ? "Superficie de construcción (m², si hay)" : "Superficie de construcción (m²)"}
            type="number"
            value={data.builtAreaM2}
            onChange={(v) => setData2({ builtAreaM2: num(v) })}
          />
          <Field disabled={!editable} label="Estacionamientos" type="number" value={data.parkingSpots} onChange={(v) => setData2({ parkingSpots: num(v) })} />
          {!land ? (
            <>
              <Field disabled={!editable} label="Recámaras" type="number" value={data.bedrooms} onChange={(v) => setData2({ bedrooms: num(v) })} />
              <Field disabled={!editable} label="Baños" type="number" value={data.bathrooms} onChange={(v) => setData2({ bathrooms: num(v) })} />
            </>
          ) : null}
          <Field disabled={!editable} label="Estado general de conservación" value={data.conservationStatus} onChange={(v) => setData2({ conservationStatus: v })} />
          <Field disabled={!editable} label="Servicios con los que cuenta" value={data.availableServices} onChange={(v) => setData2({ availableServices: v })} span />
          <Field disabled={!editable} label="Características relevantes" value={data.relevantFeatures} onChange={(v) => setData2({ relevantFeatures: v })} />
        </div>
      </Card>

      {moral ? (
        <Card>
          <CardHeader title="Constitución de la persona moral (declaración II.a.2)" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field disabled={!editable} label="Tipo de sociedad" value={legal.company?.companyType} onChange={(v) => setSection("company", { companyType: v })} hint="Ej. Sociedad Anónima de Capital Variable" />
            <Field disabled={!editable} label="RFC de la empresa" value={legal.company?.rfc} onChange={(v) => setSection("company", { rfc: v })} />
            <Field disabled={!editable} label="Número de instrumento (acta constitutiva)" value={legal.company?.instrumentNumber} onChange={(v) => setSection("company", { instrumentNumber: v })} />
            <Field disabled={!editable} label="Fecha del instrumento" type="date" value={legal.company?.instrumentDate} onChange={(v) => setSection("company", { instrumentDate: v || null })} />
            <Field disabled={!editable} label="Notario o Corredor" value={legal.company?.notaryTitle} onChange={(v) => setSection("company", { notaryTitle: v })} hint="Escribe: Notario o Corredor" />
            <Field disabled={!editable} label="Número de notaría / correduría" value={legal.company?.notaryNumber} onChange={(v) => setSection("company", { notaryNumber: v })} />
            <Field disabled={!editable} label="Lugar de la notaría" value={legal.company?.notaryPlace} onChange={(v) => setSection("company", { notaryPlace: v })} />
            <Field disabled={!editable} label="Nombre del notario / corredor" value={legal.company?.notaryName} onChange={(v) => setSection("company", { notaryName: v })} />
            <Field disabled={!editable} label="Registro Público de Comercio de" value={legal.company?.commerceRegistryPlace} onChange={(v) => setSection("company", { commerceRegistryPlace: v })} />
            <Field disabled={!editable} label="Folio mercantil" value={legal.company?.mercantileFolio} onChange={(v) => setSection("company", { mercantileFolio: v })} />
          </div>
        </Card>
      ) : null}

      {represented ? (
        <Card>
          <CardHeader
            title={moral ? "Facultades del representante legal (declaración II.b)" : "Poder del apoderado (declaración II.b)"}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field disabled={!editable}
              label="Carácter con el que comparece"
              value={legal.representation?.capacity}
              onChange={(v) => setSection("representation", { capacity: v })}
              hint={moral ? "Ej. representante legal / administrador único" : "Ej. apoderado"}
            />
            <Field disabled={!editable} label="Número del instrumento (poder)" value={legal.representation?.instrumentNumber} onChange={(v) => setSection("representation", { instrumentNumber: v })} />
            <Field disabled={!editable} label="Fecha del instrumento" type="date" value={legal.representation?.instrumentDate} onChange={(v) => setSection("representation", { instrumentDate: v || null })} />
            <Field disabled={!editable} label="Notario o Corredor" value={legal.representation?.notaryTitle} onChange={(v) => setSection("representation", { notaryTitle: v })} />
            <Field disabled={!editable} label="Número de notaría" value={legal.representation?.notaryNumber} onChange={(v) => setSection("representation", { notaryNumber: v })} />
            <Field disabled={!editable} label="Lugar de la notaría" value={legal.representation?.notaryPlace} onChange={(v) => setSection("representation", { notaryPlace: v })} />
            <Field disabled={!editable} label="Nombre del notario" value={legal.representation?.notaryName} onChange={(v) => setSection("representation", { notaryName: v })} />
            <Field disabled={!editable}
              label={moral ? "Registro Público de Comercio de" : "Registro Público de Comercio de (si está inscrito)"}
              value={legal.representation?.registryPlace}
              onChange={(v) => setSection("representation", { registryPlace: v })}
            />
            <Field disabled={!editable} label="Folio mercantil" value={legal.representation?.registryFolio} onChange={(v) => setSection("representation", { registryFolio: v })} />
          </div>
        </Card>
      ) : null}

      {expediente.accreditationType === "ESCRITURA_PUBLICA" ? (
        <Card>
          <CardHeader title="Escritura con la que se acredita la propiedad (declaración II.d)" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field disabled={!editable} label="Número de escritura" value={legal.deed?.number} onChange={(v) => setSection("deed", { number: v })} />
            <Field disabled={!editable} label="Fecha de la escritura" type="date" value={legal.deed?.date} onChange={(v) => setSection("deed", { date: v || null })} />
            <Field disabled={!editable} label="Nombre del notario" value={legal.deed?.notaryName} onChange={(v) => setSection("deed", { notaryName: v })} />
            <Field disabled={!editable} label="Número de notaría" value={legal.deed?.notaryNumber} onChange={(v) => setSection("deed", { notaryNumber: v })} />
            <Field disabled={!editable} label="Lugar de la notaría" value={legal.deed?.notaryPlace} onChange={(v) => setSection("deed", { notaryPlace: v })} hint="Ej. Cuernavaca, Morelos" />
            <Field disabled={!editable} label="Datos registrales (opcional)" value={legal.deed?.registryData} onChange={(v) => setSection("deed", { registryData: v })} hint="Folio real o datos de inscripción, si ya se confirmaron" />
          </div>
        </Card>
      ) : (
        <Card>
          <CardHeader title="Contrato privado con el que se acredita la propiedad (declaración II.d)" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field disabled={!editable} label="Vendedor en ese contrato" value={legal.privateContract?.sellerName} onChange={(v) => setSection("privateContract", { sellerName: v })} />
            <Field disabled={!editable} label="Comprador (el cliente)" value={legal.privateContract?.buyerName} onChange={(v) => setSection("privateContract", { buyerName: v })} />
            <Field disabled={!editable} label="Fecha del contrato" type="date" value={legal.privateContract?.date} onChange={(v) => setSection("privateContract", { date: v || null })} />
            <Field disabled={!editable} label="Fecha de ratificación" type="date" value={legal.privateContract?.ratificationDate} onChange={(v) => setSection("privateContract", { ratificationDate: v || null })} />
            <Field disabled={!editable} label="Ratificado ante" value={legal.privateContract?.ratifiedBefore} onChange={(v) => setSection("privateContract", { ratifiedBefore: v })} hint="Ej. la fe pública del Notario Público" />
            <Field disabled={!editable} label="Número de notaría" value={legal.privateContract?.notaryNumber} onChange={(v) => setSection("privateContract", { notaryNumber: v })} />
            <Field disabled={!editable} label="Lugar de la notaría" value={legal.privateContract?.notaryPlace} onChange={(v) => setSection("privateContract", { notaryPlace: v })} />
            <Field disabled={!editable} label="Nombre del notario" value={legal.privateContract?.notaryName} onChange={(v) => setSection("privateContract", { notaryName: v })} />
            <Field disabled={!editable} label="Fecha de inscripción en el RPP" type="date" value={legal.privateContract?.registryDate} onChange={(v) => setSection("privateContract", { registryDate: v || null })} />
            <Field disabled={!editable} label="Registro Público de la Propiedad de" value={legal.privateContract?.registryPlace} onChange={(v) => setSection("privateContract", { registryPlace: v })} />
            <Field disabled={!editable} label="Folio real" value={legal.privateContract?.realFolio} onChange={(v) => setSection("privateContract", { realFolio: v })} />
          </div>
        </Card>
      )}

      {expediente.condominiumRegime ? (
        <Card>
          <CardHeader title="Régimen de condominio (declaración II.f)" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field disabled={!editable} label="Número de escritura del régimen" value={legal.condominium?.deedNumber} onChange={(v) => setSection("condominium", { deedNumber: v })} />
            <Field disabled={!editable} label="Fecha" type="date" value={legal.condominium?.date} onChange={(v) => setSection("condominium", { date: v || null })} />
            <Field disabled={!editable} label="Número de notaría" value={legal.condominium?.notaryNumber} onChange={(v) => setSection("condominium", { notaryNumber: v })} />
            <Field disabled={!editable} label="Lugar de la notaría" value={legal.condominium?.notaryPlace} onChange={(v) => setSection("condominium", { notaryPlace: v })} />
            <Field disabled={!editable} label="Nombre del notario" value={legal.condominium?.notaryName} onChange={(v) => setSection("condominium", { notaryName: v })} />
            <Field disabled={!editable} label="Fecha de inscripción" type="date" value={legal.condominium?.registryDate} onChange={(v) => setSection("condominium", { registryDate: v || null })} />
            <Field disabled={!editable} label="Folio real" value={legal.condominium?.realFolio} onChange={(v) => setSection("condominium", { realFolio: v })} />
          </div>
        </Card>
      ) : null}

      <Card>
        <CardHeader
          title={land ? "Documentación del terreno (declaración II.g)" : "Documentación de la vivienda (declaración II.g)"}
          description="¿El cliente cuenta con esta documentación o información?"
        />
        <ul className="flex flex-col gap-3">
          {checklist.map(([key, question]) => (
            <li key={key} className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <span className="text-sm text-obsessed">{question}</span>
              <YesNo disabled={!editable}
                label=""
                value={legal.propertyChecklist?.[key] ?? null}
                onChange={(v) => setLegal({ ...legal, propertyChecklist: { ...(legal.propertyChecklist ?? {}), [key]: v } })}
              />
            </li>
          ))}
        </ul>
      </Card>

      {editable ? (
        <Card>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <Input
              label="Motivo del cambio (queda en la bitácora)"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Ej. Captura de datos de la escritura"
              containerClassName="flex-1"
            />
            <Button onClick={save} disabled={saving}>
              <Save className="h-4 w-4" aria-hidden /> {saving ? "Guardando…" : "Guardar datos del contrato"}
            </Button>
          </div>
          <p className="mt-2 text-xs text-muted">Si ya existe un contrato sin firmar, al guardar cambios quedará sin efecto y habrá que generar una versión nueva.</p>
        </Card>
      ) : (
        <p className="text-sm text-muted">
          {expediente.correctable ? "No tienes permiso para editar estos datos." : "Los datos están bloqueados porque el contrato ya se firmó o el expediente ya se decidió."}
        </p>
      )}
    </div>
  );
}

const text = (v: unknown) => (v === null || v === undefined ? "" : String(v));

function Field({
  label,
  value,
  onChange,
  type = "text",
  span = false,
  hint,
  disabled,
}: {
  label: string;
  value: unknown;
  onChange: (v: string) => void;
  type?: string;
  span?: boolean;
  hint?: string;
  disabled: boolean;
}) {
  return (
    <Input
      label={label}
      type={type}
      value={text(value)}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
      hint={hint}
      containerClassName={span ? "sm:col-span-2" : ""}
    />
  );
}

function YesNo({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string;
  value: boolean | null;
  onChange: (v: boolean) => void;
  disabled: boolean;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      {label ? <span className="text-sm font-medium text-obsessed">{label}</span> : null}
      <div className="flex gap-2">
        {[true, false].map((option) => (
          <button
            key={String(option)}
            type="button"
            disabled={disabled}
            onClick={() => onChange(option)}
            className={`rounded-lg border px-4 py-1.5 text-sm ${value === option ? "border-gold bg-gold/15 font-medium" : "border-border"}`}
          >
            {option ? "Sí" : "No"}
          </button>
        ))}
      </div>
    </div>
  );
}
