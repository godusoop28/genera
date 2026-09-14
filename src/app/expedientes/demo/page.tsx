"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { AIAnalysisPanel } from "@/components/expediente/AIAnalysisPanel";
import { CalculatedData } from "@/components/expediente/CalculatedData";
import { ContractFields } from "@/components/expediente/ContractFields";
import { ContractPreview } from "@/components/expediente/ContractPreview";
import { DocumentsTable } from "@/components/expediente/DocumentsTable";
import { ProcessingStatus } from "@/components/expediente/ProcessingStatus";
import { PdfViewerModal } from "@/components/documents/PdfViewerModal";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { receivedDocuments } from "@/data/mock-expediente";
import { BrainCircuit, CheckCircle2, Download, FileCheck2, Save } from "lucide-react";
import { useState } from "react";

export default function RevisionExpedientePage() {
  const [contractModalOpen, setContractModalOpen] = useState(false);
  const [pdfViewerOpen, setPdfViewerOpen] = useState(false);
  const [analysisDone, setAnalysisDone] = useState(false);
  const { showToast } = useToast();

  return (
    <PageContainer
      title="Revisión del expediente"
      subtitle="Consulta los documentos recibidos y verifica la información extraída automáticamente."
    >
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="flex flex-col gap-6">
          <DocumentsTable documents={receivedDocuments} />
          {analysisDone ? (
            <>
              <ContractFields />
              <CalculatedData />
            </>
          ) : (
            <AIAnalysisPanel onComplete={() => setAnalysisDone(true)} />
          )}
        </div>

        <div className="flex flex-col gap-6 lg:sticky lg:top-6 lg:self-start">
          {analysisDone ? (
            <ProcessingStatus />
          ) : (
            <Card className="flex items-center gap-3 border-border bg-app-bg/60">
              <BrainCircuit className="h-5 w-5 shrink-0 animate-pulse text-teal-dark" aria-hidden />
              <p className="text-sm text-muted">
                La vista previa del contrato estará disponible cuando la IA termine de
                analizar los documentos.
              </p>
            </Card>
          )}
          <ContractPreview onOpen={() => setPdfViewerOpen(true)} />
        </div>
      </div>

      <div className="mt-8 flex flex-col-reverse gap-3 border-t border-border pt-6 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={() => showToast("Cambios guardados")}>
          <Save className="h-4 w-4" aria-hidden />
          Guardar borrador
        </Button>
        <Button onClick={() => setContractModalOpen(true)} disabled={!analysisDone}>
          <FileCheck2 className="h-4 w-4" aria-hidden />
          Generar contrato
        </Button>
      </div>

      <Modal
        open={contractModalOpen}
        onClose={() => setContractModalOpen(false)}
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => {
                setContractModalOpen(false);
                setPdfViewerOpen(true);
              }}
            >
              Ver contrato
            </Button>
            <Button
              onClick={() => {
                showToast("Descarga simulada");
                setContractModalOpen(false);
              }}
            >
              <Download className="h-4 w-4" aria-hidden />
              Descargar PDF
            </Button>
          </>
        }
      >
        <div className="flex flex-col items-center py-4 text-center">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-success-bg">
            <CheckCircle2 className="h-7 w-7 text-success-text" aria-hidden />
          </div>
          <h3 className="text-lg font-semibold text-navy">Contrato generado</h3>
          <p className="mt-2 text-sm text-muted">
            El contrato se generó correctamente con la información revisada del expediente.
          </p>
        </div>
      </Modal>

      <PdfViewerModal open={pdfViewerOpen} onClose={() => setPdfViewerOpen(false)} />
    </PageContainer>
  );
}
