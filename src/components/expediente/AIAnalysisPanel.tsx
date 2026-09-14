"use client";

import { Card } from "@/components/ui/Card";
import { BrainCircuit, Check, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";

interface AIAnalysisPanelProps {
  onComplete: () => void;
}

const steps = [
  { label: "Clasificando documentos recibidos", delay: 900 },
  { label: "Extrayendo datos con inteligencia artificial", delay: 1900 },
  { label: "Verificando consistencia de la información", delay: 2800 },
];

export function AIAnalysisPanel({ onComplete }: AIAnalysisPanelProps) {
  const [completedSteps, setCompletedSteps] = useState(0);

  useEffect(() => {
    const timers = steps.map((step, index) =>
      window.setTimeout(() => {
        setCompletedSteps(index + 1);
        if (index === steps.length - 1) {
          window.setTimeout(onComplete, 400);
        }
      }, step.delay),
    );
    return () => timers.forEach((timer) => window.clearTimeout(timer));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Card className="border-teal/20 bg-gradient-to-br from-teal-light/40 via-white to-white">
      <div className="flex items-start gap-4">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-teal-light text-teal-dark">
          <BrainCircuit className="h-6 w-6 animate-pulse" aria-hidden />
        </div>
        <div className="flex-1">
          <h2 className="text-lg font-semibold text-navy">Analizando documentos con IA</h2>
          <p className="mt-1 text-sm text-muted">
            Estamos extrayendo automáticamente la información de los documentos recibidos para
            prellenar los campos del contrato.
          </p>

          <ul className="mt-5 space-y-3">
            {steps.map((step, index) => {
              const isDone = index < completedSteps;
              const isActive = index === completedSteps;
              return (
                <li key={step.label} className="flex items-center gap-3 text-sm">
                  <span
                    className={
                      isDone
                        ? "flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-teal text-white"
                        : isActive
                          ? "flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 border-teal text-teal"
                          : "flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 border-border text-transparent"
                    }
                  >
                    {isDone ? (
                      <Check className="h-3 w-3" aria-hidden />
                    ) : isActive ? (
                      <Loader2 className="h-3 w-3 animate-spin" aria-hidden />
                    ) : null}
                  </span>
                  <span className={isDone || isActive ? "text-navy" : "text-muted"}>
                    {step.label}
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      </div>
    </Card>
  );
}
