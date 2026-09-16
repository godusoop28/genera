"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { ExpedienteConfigForm } from "@/components/expediente/ExpedienteConfigForm";
import { GenerateLinkModal } from "@/components/expediente/GenerateLinkModal";
import { RequiredDocuments } from "@/components/expediente/RequiredDocuments";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { buildDocumentRequirements, buildOwners } from "@/data/document-requirements";
import { createExpediente, defaultExpedienteConfig } from "@/lib/expediente-factory";
import type { ExpedienteConfig } from "@/types/expediente";
import { Link2, Save } from "lucide-react";
import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";

export default function NuevoExpedientePage() {
  const { expedientes, addExpediente, getExpediente } = useDemoApp();
  const [ownerName, setOwnerName] = useState("");
  const [propertyAddress, setPropertyAddress] = useState("");
  const [config, setConfig] = useState<ExpedienteConfig>(defaultExpedienteConfig);
  const [linkModalOpen, setLinkModalOpen] = useState(false);
  const [createdExpedienteId, setCreatedExpedienteId] = useState<string | null>(null);
  const { showToast } = useToast();
  const router = useRouter();
  const createdExpediente = createdExpedienteId ? getExpediente(createdExpedienteId) : null;

  const requirements = useMemo(
    () => buildDocumentRequirements(config, buildOwners(config.ownerCount)),
    [config],
  );

  const nextFolio = useMemo(() => {
    const year = new Date().getFullYear() + 1;
    return `EXP-${year}-${String(expedientes.length + 1).padStart(3, "0")}`;
  }, [expedientes.length]);

  const handleChange = <K extends keyof ExpedienteConfig>(key: K, value: ExpedienteConfig[K]) => {
    setConfig((prev) => ({ ...prev, [key]: value }));
  };

  const handleGenerateLink = () => {
    if (!ownerName.trim()) {
      showToast("Captura el nombre del propietario.");
      return;
    }
    const exp = createExpediente({
      folio: nextFolio,
      ownerName: ownerName.trim(),
      propertyAddress: propertyAddress.trim() || undefined,
      config,
    });
    addExpediente(exp);
    setCreatedExpedienteId(exp.id);
    setLinkModalOpen(true);
    showToast("Expediente creado.");
  };

  const handleSaveDraft = () => {
    if (!ownerName.trim()) {
      showToast("Captura el nombre del propietario.");
      return;
    }
    const exp = createExpediente({
      folio: nextFolio,
      ownerName: ownerName.trim(),
      propertyAddress: propertyAddress.trim() || undefined,
      config,
    });
    exp.status = "draft";
    addExpediente(exp);
    showToast("Borrador guardado.");
    router.push(`/expedientes/${exp.id}`);
  };

  return (
    <PageContainer
      title="Nuevo expediente"
      subtitle="Configura los datos que determinan qué documentos deberá subir el cliente."
    >
      <Card className="mb-6">
        <CardHeader title="Datos del expediente" description={`Folio ${nextFolio}`} />
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
        <Button variant="secondary" onClick={handleSaveDraft}>
          <Save className="h-4 w-4" aria-hidden />
          Guardar borrador
        </Button>
        <Button onClick={handleGenerateLink}>
          <Link2 className="h-4 w-4" aria-hidden />
          Generar liga para cliente
        </Button>
      </div>

      {createdExpediente ? (
        <GenerateLinkModal
          open={linkModalOpen}
          onClose={() => setLinkModalOpen(false)}
          expediente={createdExpediente}
        />
      ) : null}
    </PageContainer>
  );
}
