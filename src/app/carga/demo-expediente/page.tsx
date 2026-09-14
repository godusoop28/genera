"use client";

import { BrandMark } from "@/components/layout/BrandMark";
import { DocumentUploadCard } from "@/components/documents/DocumentUploadCard";
import { DocumentValidationPanel } from "@/components/documents/DocumentValidationPanel";
import { Stepper } from "@/components/documents/Stepper";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { baseRequiredDocuments, conditionalDocuments } from "@/data/document-requirements";
import { CheckCircle2, HelpCircle, Send } from "lucide-react";
import { useState } from "react";

const documentsToUpload = [
  ...baseRequiredDocuments,
  { ...conditionalDocuments.find((d) => d.id === "ine-2")!, required: true },
];

const steps = ["Datos del expediente", "Subir documentos", "Envío"];

export default function CargaDemoExpedientePage() {
  const [submitModalOpen, setSubmitModalOpen] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = () => {
    setSubmitModalOpen(true);
    setSubmitted(true);
  };

  return (
    <div className="flex min-h-screen flex-col bg-app-bg">
      <header className="border-b border-border bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 lg:px-8">
          <BrandMark showText={false} />
          <button
            type="button"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-navy/70 hover:text-navy"
          >
            <HelpCircle className="h-4 w-4" aria-hidden />
            ¿Necesitas ayuda?
          </button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 lg:px-8 lg:py-10">
        <div className="mb-6 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-navy">Carga tus documentos</h1>
          {submitted ? <Badge tone="success">Documentos enviados</Badge> : null}
        </div>
        <p className="max-w-2xl text-sm text-muted">
          Sube una foto clara de cada documento requerido. Nosotros nos encargamos del resto.
        </p>

        <div className="mt-8 rounded-2xl border border-border bg-white p-5">
          <Stepper steps={steps} currentStep={2} />
        </div>

        <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-[1.6fr_1fr]">
          <div className="flex flex-col gap-5">
            {documentsToUpload.map((doc) => (
              <DocumentUploadCard
                key={doc.id}
                doc={doc}
                allowErrorDemo={doc.id === "domicilio"}
              />
            ))}

            <div className="rounded-2xl border border-border bg-white p-5">
              <Button size="lg" className="w-full" onClick={handleSubmit} disabled={submitted}>
                <Send className="h-4 w-4" aria-hidden />
                {submitted ? "Documentos enviados" : "Enviar documentos"}
              </Button>
            </div>
          </div>

          <div className="lg:sticky lg:top-6 lg:self-start">
            <DocumentValidationPanel />
          </div>
        </div>
      </main>

      <Modal
        open={submitModalOpen}
        onClose={() => setSubmitModalOpen(false)}
        footer={<Button onClick={() => setSubmitModalOpen(false)}>Cerrar</Button>}
      >
        <div className="flex flex-col items-center py-4 text-center">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-success-bg">
            <CheckCircle2 className="h-7 w-7 text-success-text" aria-hidden />
          </div>
          <h3 className="text-lg font-semibold text-navy">Documentos enviados</h3>
          <p className="mt-2 text-sm text-muted">
            La información fue enviada correctamente a Century 21.
          </p>
          <p className="mt-3 text-sm text-muted">
            El personal de la inmobiliaria revisará la documentación y continuará con el
            proceso.
          </p>
        </div>
      </Modal>
    </div>
  );
}
