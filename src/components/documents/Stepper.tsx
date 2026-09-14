import { cn } from "@/lib/utils";
import { Check } from "lucide-react";

interface StepperProps {
  steps: string[];
  currentStep: number;
}

export function Stepper({ steps, currentStep }: StepperProps) {
  return (
    <ol className="flex flex-col gap-3 sm:flex-row sm:items-center sm:gap-0">
      {steps.map((step, index) => {
        const stepNumber = index + 1;
        const isComplete = stepNumber < currentStep;
        const isActive = stepNumber === currentStep;

        return (
          <li key={step} className="flex flex-1 items-center gap-3">
            <div className="flex items-center gap-3">
              <span
                className={cn(
                  "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold",
                  isComplete && "bg-teal text-white",
                  isActive && "bg-navy text-white",
                  !isComplete && !isActive && "bg-app-bg text-muted border border-border",
                )}
              >
                {isComplete ? <Check className="h-4 w-4" aria-hidden /> : stepNumber}
              </span>
              <span
                className={cn(
                  "text-sm font-medium",
                  isActive || isComplete ? "text-navy" : "text-muted",
                )}
              >
                {step}
              </span>
            </div>
            {stepNumber !== steps.length ? (
              <div className="mx-3 hidden h-px flex-1 bg-border sm:block" />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
