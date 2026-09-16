import { cn } from "@/lib/utils";
import { Check } from "lucide-react";

interface StepperProps {
  steps: string[];
  currentStep: number;
  maxReachedStep?: number;
  onStepClick?: (step: number) => void;
}

export function Stepper({ steps, currentStep, maxReachedStep, onStepClick }: StepperProps) {
  return (
    <ol className="flex flex-col gap-3 sm:flex-row sm:items-center sm:gap-0">
      {steps.map((step, index) => {
        const stepNumber = index + 1;
        const isComplete = stepNumber < currentStep;
        const isActive = stepNumber === currentStep;
        const isClickable = Boolean(onStepClick) && stepNumber <= (maxReachedStep ?? currentStep);
        const Wrapper = isClickable ? "button" : "div";

        return (
          <li key={step} className="flex flex-1 items-center gap-3">
            <Wrapper
              type={isClickable ? "button" : undefined}
              onClick={isClickable ? () => onStepClick?.(stepNumber) : undefined}
              className={cn("flex items-center gap-3 text-left", isClickable && "cursor-pointer")}
            >
              <span
                className={cn(
                  "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold",
                  isComplete && "bg-dark-gold text-white",
                  isActive && "bg-obsessed text-white",
                  !isComplete && !isActive && "bg-app-bg text-muted border border-border",
                )}
              >
                {isComplete ? <Check className="h-4 w-4" aria-hidden /> : stepNumber}
              </span>
              <span
                className={cn(
                  "text-sm font-medium",
                  isActive || isComplete ? "text-obsessed" : "text-muted",
                )}
              >
                {step}
              </span>
            </Wrapper>
            {stepNumber !== steps.length ? (
              <div className="mx-3 hidden h-px flex-1 bg-border sm:block" />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
