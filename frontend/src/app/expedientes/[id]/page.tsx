"use client";

import { ActivityTimeline } from "@/components/expediente/ActivityTimeline";
import { DocumentBank } from "@/components/documents/DocumentBank";
import { CalculatedContractData } from "@/components/contract/CalculatedContractData";
import { ContractFields } from "@/components/contract/ContractFields";
import { ContractInfo } from "@/components/contract/ContractInfo";
import { ContractPreview } from "@/components/contract/ContractPreview";
import { ComplianceChecklist } from "@/components/compliance/ComplianceChecklist";
import { ClosingSection } from "@/components/closing/ClosingSection";
import { ExpedienteHeader } from "@/components/expediente/ExpedienteHeader";
import { ExpedienteSummary } from "@/components/expediente/ExpedienteSummary";
import { ExtractedFieldsPanel } from "@/components/expediente/ExtractedFieldsPanel";
import { PropertyDecisionCard } from "@/components/expediente/PropertyDecisionCard";
import { PrivacyReceipt } from "@/components/privacy/PrivacyReceipt";
import { ReceptionSignatureCard } from "@/components/privacy/ReceptionSignatureCard";
import { PageContainer } from "@/components/layout/PageContainer";
import { useDemoApp } from "@/context/DemoAppProvider";
import { buildContractCalculations } from "@/lib/calculations";
import { cn } from "@/lib/utils";
import { use, useMemo, useState } from "react";

const tabs = [
  { id: "resumen", label: "Resumen" },
  { id: "documentos", label: "Documentos" },
  { id: "informacion", label: "Información" },
  { id: "contrato", label: "Contrato" },
  { id: "cumplimiento", label: "Cumplimiento" },
  { id: "cierre", label: "Cierre de venta" },
] as const;

type TabId = (typeof tabs)[number]["id"];

export default function ExpedienteDetailPage({ params }: PageProps<"/expedientes/[id]">) {
  const { id } = use(params);
  const { getExpediente } = useDemoApp();
  const [tab, setTab] = useState<TabId>("resumen");
  const expediente = getExpediente(id);

  const calculations = useMemo(
    () =>
      buildContractCalculations(
        expediente?.manualData.authorizedPrice ?? 0,
        expediente?.manualData.contractSignatureDate,
      ),
    [expediente?.manualData.authorizedPrice, expediente?.manualData.contractSignatureDate],
  );

  if (!expediente) {
    return (
      <PageContainer title="Expediente no encontrado">
        <p className="text-sm text-muted">Este expediente no existe o fue eliminado.</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer title={expediente.ownerName} subtitle="Detalle del expediente documental.">
      <ExpedienteHeader expediente={expediente} />

      <div className="mb-6 flex gap-1 overflow-x-auto border-b border-border">
        {tabs.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              "shrink-0 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
              tab === t.id ? "border-gold text-obsessed" : "border-transparent text-muted hover:text-obsessed",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "resumen" ? <ExpedienteSummary expediente={expediente} /> : null}
      {tab === "documentos" ? <DocumentBank expediente={expediente} /> : null}
      {tab === "informacion" ? <ExtractedFieldsPanel expediente={expediente} /> : null}
      {tab === "contrato" ? (
        <div className="flex flex-col gap-6">
          <ContractInfo />
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <ContractFields expediente={expediente} />
            <div className="flex flex-col gap-6">
              <CalculatedContractData calculations={calculations} />
              <ContractPreview expediente={expediente} calculations={calculations} />
            </div>
          </div>
        </div>
      ) : null}
      {tab === "cumplimiento" ? (
        <div className="flex flex-col gap-6">
          <ComplianceChecklist expediente={expediente} />
          <PrivacyReceipt expediente={expediente} />
          <ReceptionSignatureCard expediente={expediente} />
          <PropertyDecisionCard expediente={expediente} />
          <ActivityTimeline activity={expediente.activity} />
        </div>
      ) : null}
      {tab === "cierre" ? <ClosingSection expediente={expediente} /> : null}
    </PageContainer>
  );
}
