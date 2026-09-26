"use client";

import { ParticipantFields } from "@/components/expediente/ParticipantFields";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { createExpediente } from "@/lib/api/expedientes";
import type {
  BackendAccreditationType,
  BackendPersonType,
  BackendPropertyCaseType,
  BackendPropertyLegalStatus,
  ParticipantRequest,
} from "@/lib/api/types";
import { accreditationLabels, legalStatusLabels, propertyTypeLabels } from "@/lib/labels";
import { previewRequirements } from "@/lib/requirements-preview";
import { AlertTriangle, Plus, Save, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";

interface AddressForm {
  street: string;
  exteriorNumber: string;
  interiorNumber: string;
  neighborhood: string;
  municipality: string;
  state: string;
  zipCode: string;
}

const emptyAddress: AddressForm = {
  street: "",
  exteriorNumber: "",
  interiorNumber: "",
  neighborhood: "",
  municipality: "",
  state: "Morelos",
  zipCode: "",
};

function formatAddress(a: AddressForm): string {
  const interior = a.interiorNumber.trim() ? ` Int. ${a.interiorNumber.trim()}` : "";
  return `${a.street.trim()} No. Ext. ${a.exteriorNumber.trim()}${interior}, Col. ${a.neighborhood.trim()}, ${a.municipality.trim()}, ${a.state.trim()}, C.P. ${a.zipCode.trim()}`;
}

const person = (role: ParticipantRequest["role"]): ParticipantRequest => ({ role, fullName: "" });

export default function NuevoExpedientePage() {
  const { showToast } = useToast();
  const router = useRouter();

  const [personType, setPersonType] = useState<BackendPersonType>("FISICA");
  const [signedByAttorney, setSignedByAttorney] = useState(false);
  const [owners, setOwners] = useState<ParticipantRequest[]>([person("OWNER")]);
  const [representatives, setRepresentatives] = useState<ParticipantRequest[]>([person("LEGAL_REPRESENTATIVE")]);
  const [attorney, setAttorney] = useState<ParticipantRequest>(person("ATTORNEY"));
  const [accreditationType, setAccreditationType] = useState<BackendAccreditationType>("ESCRITURA_PUBLICA");
  const [propertyCaseType, setPropertyCaseType] = useState<BackendPropertyCaseType>("HOUSING");
  const [condominiumRegime, setCondominiumRegime] = useState(false);
  const [legalStatus, setLegalStatus] = useState<BackendPropertyLegalStatus>("LIBRE_GRAVAMEN");
  const [address, setAddress] = useState<AddressForm>(emptyAddress);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const participants = useMemo<ParticipantRequest[]>(() => {
    if (personType === "MORAL") return [{ ...owners[0], role: "OWNER", civilStatus: null, maritalRegime: null }, ...representatives];
    const titulares = owners.map((o, i) => ({ ...o, role: i === 0 ? ("OWNER" as const) : ("CO_OWNER" as const) }));
    return signedByAttorney ? [...titulares, attorney] : titulares;
  }, [personType, owners, representatives, attorney, signedByAttorney]);

  const preview = useMemo(
    () => previewRequirements({ personType, signedByAttorney, accreditationType, condominiumRegime, propertyCaseType, participants }),
    [personType, signedByAttorney, accreditationType, condominiumRegime, propertyCaseType, participants],
  );

  const addressComplete = (Object.keys(address) as (keyof AddressForm)[])
    .filter((k) => k !== "interiorNumber")
    .every((k) => address[k].trim().length > 0);

  const setOwner = (index: number, value: ParticipantRequest) => setOwners((prev) => prev.map((o, i) => (i === index ? value : o)));

  const handleCreate = async () => {
    setError(null);
    if (participants.some((p) => !p.fullName.trim())) {
      setError("Captura el nombre de cada participante.");
      return;
    }
    if (!addressComplete) {
      setError("Captura todos los campos obligatorios del domicilio del inmueble.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createExpediente({
        personType,
        signedByAttorney: personType === "FISICA" && signedByAttorney,
        accreditationType,
        condominiumRegime,
        propertyCaseType,
        declaredLegalStatus: legalStatus,
        propertyAddress: formatAddress(address),
        participants: participants.map((p) => ({ ...p, fullName: p.fullName.trim() })),
      });
      showToast(`Expediente ${created.folio} creado.`);
      router.push(`/expedientes/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo conectar con el servidor. Intenta de nuevo.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <PageContainer
      title="Nuevo expediente"
      subtitle="Captura quién vende y qué inmueble es. Con esto el sistema determina qué documentos pedirle al cliente."
    >
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          <Card>
            <CardHeader title="¿Quién es el cliente?" description="Define qué documentos y qué datos pide el contrato." />
            <div className="grid gap-3 sm:grid-cols-2">
              {(["FISICA", "MORAL"] as const).map((type) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => setPersonType(type)}
                  className={`rounded-xl border p-4 text-left transition-colors ${personType === type ? "border-gold bg-gold/10" : "border-border hover:bg-app-bg"}`}
                >
                  <p className="text-sm font-semibold text-obsessed">{type === "FISICA" ? "Persona física" : "Persona moral (empresa)"}</p>
                  <p className="mt-1 text-xs text-muted">
                    {type === "FISICA"
                      ? "Uno o varios propietarios personas. Pueden firmar ellos mismos o un apoderado."
                      : "Una empresa es la propietaria; firma su representante legal. No aplica estado civil."}
                  </p>
                </button>
              ))}
            </div>
            {personType === "FISICA" ? (
              <div className="mt-4 border-t border-border pt-4">
                <Toggle
                  id="attorney-toggle"
                  checked={signedByAttorney}
                  onChange={setSignedByAttorney}
                  label="Firma un apoderado en nombre del propietario"
                  description="Se pedirá su identificación y el poder notarial."
                />
              </div>
            ) : null}
          </Card>

          <Card>
            <CardHeader
              title={personType === "MORAL" ? "Empresa y representante legal" : "Propietarios"}
              description={
                personType === "MORAL"
                  ? "La empresa titular del inmueble y quién la representa."
                  : "Registra a todos los titulares del inmueble; no hay límite. Cada uno tendrá sus propios documentos y firma."
              }
            />
            <div className="flex flex-col gap-4">
              {(personType === "MORAL" ? owners.slice(0, 1) : owners).map((owner, index) => (
                <div key={index} className="rounded-xl border border-border p-4">
                  <div className="mb-3 flex items-center justify-between">
                    <p className="text-sm font-semibold text-obsessed">
                      {personType === "MORAL" ? "Empresa titular" : index === 0 ? "Propietario principal" : `Copropietario ${index + 1}`}
                    </p>
                    {personType === "FISICA" && index > 0 ? (
                      <Button variant="ghost" size="sm" onClick={() => setOwners((prev) => prev.filter((_, i) => i !== index))}>
                        <Trash2 className="h-4 w-4" aria-hidden /> Quitar
                      </Button>
                    ) : null}
                  </div>
                  <ParticipantFields
                    value={{ ...owner, role: personType === "MORAL" || index === 0 ? "OWNER" : "CO_OWNER" }}
                    onChange={(v) => setOwner(index, v)}
                    personType={personType}
                  />
                </div>
              ))}
              {personType === "FISICA" ? (
                <Button variant="secondary" size="sm" className="self-start" onClick={() => setOwners((prev) => [...prev, person("CO_OWNER")])}>
                  <Plus className="h-4 w-4" aria-hidden /> Agregar copropietario
                </Button>
              ) : null}

              {personType === "MORAL"
                ? representatives.map((rep, index) => (
                    <div key={`rep-${index}`} className="rounded-xl border border-border p-4">
                      <div className="mb-3 flex items-center justify-between">
                        <p className="text-sm font-semibold text-obsessed">Representante legal {representatives.length > 1 ? index + 1 : ""}</p>
                        {index > 0 ? (
                          <Button variant="ghost" size="sm" onClick={() => setRepresentatives((prev) => prev.filter((_, i) => i !== index))}>
                            <Trash2 className="h-4 w-4" aria-hidden /> Quitar
                          </Button>
                        ) : null}
                      </div>
                      <ParticipantFields
                        value={rep}
                        onChange={(v) => setRepresentatives((prev) => prev.map((r, i) => (i === index ? v : r)))}
                        personType={personType}
                      />
                    </div>
                  ))
                : null}
              {personType === "MORAL" ? (
                <Button
                  variant="secondary"
                  size="sm"
                  className="self-start"
                  onClick={() => setRepresentatives((prev) => [...prev, person("LEGAL_REPRESENTATIVE")])}
                >
                  <Plus className="h-4 w-4" aria-hidden /> Agregar otro representante
                </Button>
              ) : null}

              {personType === "FISICA" && signedByAttorney ? (
                <div className="rounded-xl border border-border p-4">
                  <p className="mb-3 text-sm font-semibold text-obsessed">Apoderado</p>
                  <ParticipantFields value={attorney} onChange={setAttorney} personType={personType} />
                </div>
              ) : null}
            </div>
          </Card>

          <Card>
            <CardHeader title="Inmueble" />
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
              <Select
                label="Situación jurídica declarada (preliminar)"
                value={legalStatus}
                onChange={(e) => setLegalStatus(e.target.value as BackendPropertyLegalStatus)}
                options={Object.entries(legalStatusLabels).map(([value, label]) => ({ value, label }))}
              />
              <div className="flex items-end">
                <Toggle
                  id="condominium-toggle"
                  checked={condominiumRegime}
                  onChange={setCondominiumRegime}
                  label="Está en régimen de condominio"
                />
              </div>
            </div>
            {propertyCaseType === "COMMERCIAL" ? (
              <p className="mt-3 flex items-start gap-2 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                El contrato registrado ante PROFECO solo ampara inmuebles destinados a casa habitación. Puedes integrar el expediente,
                pero el sistema no generará el contrato para un inmueble comercial.
              </p>
            ) : null}

            <p className="mb-3 mt-5 text-sm font-medium text-obsessed">Domicilio del inmueble</p>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Input label="Calle" value={address.street} onChange={(e) => setAddress({ ...address, street: e.target.value })} containerClassName="lg:col-span-2" />
              <Input label="Número exterior" value={address.exteriorNumber} onChange={(e) => setAddress({ ...address, exteriorNumber: e.target.value })} />
              <Input
                label="Número interior (opcional)"
                value={address.interiorNumber}
                onChange={(e) => setAddress({ ...address, interiorNumber: e.target.value })}
              />
              <Input label="Colonia" value={address.neighborhood} onChange={(e) => setAddress({ ...address, neighborhood: e.target.value })} />
              <Input label="Municipio" value={address.municipality} onChange={(e) => setAddress({ ...address, municipality: e.target.value })} />
              <Input label="Estado" value={address.state} onChange={(e) => setAddress({ ...address, state: e.target.value })} />
              <Input label="Código postal" value={address.zipCode} onChange={(e) => setAddress({ ...address, zipCode: e.target.value })} />
            </div>
          </Card>
        </div>

        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader
              title="Documentos que se le pedirán"
              description="Se actualiza con lo que capturas. Si algo cambia después (p. ej. un propietario se declara casado), la lista se ajusta sola."
            />
            <ul className="flex flex-col gap-2 text-sm">
              {preview.map((item, i) => (
                <li key={i} className="rounded-lg border border-border px-3 py-2 text-obsessed">
                  {item.label}
                  {item.note ? <span className="ml-1 text-xs text-muted">({item.note})</span> : null}
                </li>
              ))}
            </ul>
            <p className="mt-3 text-xs text-muted">
              Cualquier documento que no aplique a este caso se puede marcar como &quot;No aplica&quot;, con justificación, durante la revisión.
            </p>
          </Card>

          {error ? <p className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">{error}</p> : null}
          <Button onClick={handleCreate} disabled={submitting} size="lg">
            <Save className="h-4 w-4" aria-hidden />
            {submitting ? "Creando…" : "Crear expediente"}
          </Button>
        </div>
      </div>
    </PageContainer>
  );
}
