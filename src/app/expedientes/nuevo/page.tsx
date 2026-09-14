"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { ExpedienteConfigForm } from "@/components/expediente/ExpedienteConfigForm";
import { GenerateLinkModal } from "@/components/expediente/GenerateLinkModal";
import { RequiredDocuments } from "@/components/expediente/RequiredDocuments";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import { getRequiredDocuments } from "@/data/document-requirements";
import { defaultExpedienteConfig } from "@/data/mock-expediente";
import type { ExpedienteConfig } from "@/types/expediente";
import { Link2, Save } from "lucide-react";
import { useMemo, useState } from "react";

export default function NuevoExpedientePage() {
  const [config, setConfig] = useState<ExpedienteConfig>(defaultExpedienteConfig);
  const [linkModalOpen, setLinkModalOpen] = useState(false);
  const { showToast } = useToast();

  const conditionalDocuments = useMemo(() => getRequiredDocuments(config), [config]);

  const handleChange = <K extends keyof ExpedienteConfig>(key: K, value: ExpedienteConfig[K]) => {
    setConfig((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <PageContainer
      title="Nuevo expediente"
      subtitle="Configura los datos que determinan qué documentos deberá subir el cliente."
    >
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <ExpedienteConfigForm config={config} onChange={handleChange} />
        <RequiredDocuments config={config} conditionalDocuments={conditionalDocuments} />
      </div>

      <div className="mt-8 flex flex-col-reverse gap-3 border-t border-border pt-6 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={() => showToast("Borrador guardado")}>
          <Save className="h-4 w-4" aria-hidden />
          Guardar borrador
        </Button>
        <Button onClick={() => setLinkModalOpen(true)}>
          <Link2 className="h-4 w-4" aria-hidden />
          Generar liga para cliente
        </Button>
      </div>

      <GenerateLinkModal open={linkModalOpen} onClose={() => setLinkModalOpen(false)} />
    </PageContainer>
  );
}
