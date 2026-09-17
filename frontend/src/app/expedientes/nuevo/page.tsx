"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { ExpedienteConfigForm } from "@/components/expediente/ExpedienteConfigForm";
import { RequiredDocuments } from "@/components/expediente/RequiredDocuments";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import { buildDocumentRequirements, buildOwners } from "@/data/document-requirements";
import { ApiError } from "@/lib/api/client";
import {
  buildBackendParticipants,
  toBackendAccreditationType,
  toBackendLegalStatus,
  toBackendPersonType,
  toBackendPropertyCaseType,
  toBackendSignerCharacter,
} from "@/lib/api/config-mapping";
import { createExpediente as createBackendExpediente } from "@/lib/api/expedientes";
import { defaultExpedienteConfig } from "@/lib/expediente-factory";
import type { ExpedienteConfig } from "@/types/expediente";
import { Save } from "lucide-react";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";

export default function NuevoExpedientePage() {
  const [ownerName, setOwnerName] = useState("");
  const [propertyAddress, setPropertyAddress] = useState("");
  const [config, setConfig] = useState<ExpedienteConfig>(defaultExpedienteConfig);
  const [submitting, setSubmitting] = useState(false);
  const { showToast } = useToast();
  const router = useRouter();

  const requirements = useMemo(
    () => buildDocumentRequirements(config, buildOwners(config.ownerCount)),
    [config],
  );

  const handleChange = <K extends keyof ExpedienteConfig>(key: K, value: ExpedienteConfig[K]) => {
    setConfig((prev) => ({ ...prev, [key]: value }));
  };

  const handleCreate = async () => {
    if (!ownerName.trim()) {
      showToast("Captura el nombre del propietario.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createBackendExpediente({
        ownerDisplayName: ownerName.trim(),
        personType: toBackendPersonType(config.personType),
        signerCharacter: toBackendSignerCharacter(config.signerCharacter),
        accreditationType: toBackendAccreditationType(config.accreditation),
        condominiumRegime: config.condominiumRegime,
        propertyCaseType: toBackendPropertyCaseType(config.propertyType),
        declaredLegalStatus: toBackendLegalStatus(config.legalStatus),
        propertyAddress: propertyAddress.trim() || undefined,
        participants: buildBackendParticipants(ownerName.trim(), config.ownerCount),
      });
      showToast(`Expediente ${created.folio} creado en el backend.`);
      router.push(`/expedientes/${created.id}`);
    } catch (err) {
      if (err instanceof ApiError) {
        showToast(`No se pudo crear el expediente (${err.status}): ${err.message}`);
      } else {
        showToast("No se pudo conectar con el backend en localhost:8080.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <PageContainer
      title="Nuevo expediente"
      subtitle="Configura los datos que determinan qué documentos deberá subir el cliente. Se guarda directamente en el backend."
    >
      <Card className="mb-6">
        <CardHeader title="Datos del expediente" description="El folio (EXP-AAAA-000001) lo asigna el backend al crear." />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Nombre del propietario"
            value={ownerName}
            onChange={(e) => setOwnerName(e.target.value)}
            placeholder="Ej. Juan Pérez López"
          />
          <Input
            label="Domicilio del inmueble (opcional)"
            value={propertyAddress}
            onChange={(e) => setPropertyAddress(e.target.value)}
          />
        </div>
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <ExpedienteConfigForm config={config} onChange={handleChange} />
        <RequiredDocuments requirements={requirements} />
      </div>

      <div className="mt-8 flex flex-col-reverse gap-3 border-t border-border pt-6 sm:flex-row sm:justify-end">
        <Button onClick={handleCreate} disabled={submitting}>
          <Save className="h-4 w-4" aria-hidden />
          {submitting ? "Creando..." : "Crear expediente"}
        </Button>
      </div>
    </PageContainer>
  );
}
