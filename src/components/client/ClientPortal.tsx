"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { ClientDataStep } from "@/components/client/ClientDataStep";
import { ConfirmationStep } from "@/components/client/ConfirmationStep";
import { DocumentsStep } from "@/components/client/DocumentsStep";
import { PrivacyConsentStep } from "@/components/client/PrivacyConsentStep";
import { Stepper } from "@/components/documents/Stepper";
import { Badge } from "@/components/ui/Badge";
import type { Expediente } from "@/types/expediente";
import { HelpCircle } from "lucide-react";
import { useMemo, useState } from "react";

const steps = ["Aviso de privacidad", "Datos complementarios", "Documentos", "Confirmación"];

function initialStepFor(exp: Expediente): number {
  if (!exp.privacyConsent.mainConsent) return 1;
  if (!exp.manualData.email) return 2;
  if (exp.status === "waiting_documents") return 3;
  return 4;
}

export function ClientPortal({ expediente }: { expediente: Expediente }) {
  const [step, setStep] = useState(() => initialStepFor(expediente));
  const maxReached = useMemo(() => Math.max(step, initialStepFor(expediente)), [step, expediente]);

  return (
    <div className="flex min-h-screen flex-col bg-app-bg">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-4 lg:px-8">
          <BrandLogo tone="light" size="sm" />
          <button
            type="button"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-obsessed/70 hover:text-obsessed"
          >
            <HelpCircle className="h-4 w-4" aria-hidden />
            ¿Necesitas ayuda?
          </button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 lg:px-8 lg:py-10">
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-obsessed">{expediente.folio}</h1>
          <Badge tone="neutral">Proceso de recepción documental</Badge>
        </div>
        <p className="max-w-2xl text-sm text-muted">
          No necesitas crear una cuenta. Esta liga es tu acceso directo a este expediente.
        </p>

        <div className="mt-8 rounded-2xl border border-border bg-card p-5">
          <Stepper steps={steps} currentStep={step} maxReachedStep={maxReached} onStepClick={setStep} />
        </div>

        <div className="mt-8">
          {step === 1 ? <PrivacyConsentStep expediente={expediente} onContinue={() => setStep(2)} /> : null}
          {step === 2 ? <ClientDataStep expediente={expediente} onContinue={() => setStep(3)} /> : null}
          {step === 3 ? <DocumentsStep expediente={expediente} onContinue={() => setStep(4)} /> : null}
          {step === 4 ? <ConfirmationStep expediente={expediente} /> : null}
        </div>
      </main>

      <footer className="border-t border-border bg-card px-4 py-4 text-center lg:px-8">
        <BrandFooter className="mx-auto" />
      </footer>
    </div>
  );
}
