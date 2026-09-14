import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

type BadgeTone = "teal" | "navy" | "success" | "warning" | "danger" | "manual" | "neutral";

interface BadgeProps {
  children: ReactNode;
  tone?: BadgeTone;
  className?: string;
}

const toneClasses: Record<BadgeTone, string> = {
  teal: "bg-teal-light text-teal-dark",
  navy: "bg-navy/5 text-navy",
  success: "bg-success-bg text-success-text",
  warning: "bg-warning-bg text-warning-text",
  danger: "bg-danger-bg text-danger-text",
  manual: "bg-manual-bg text-manual-text",
  neutral: "bg-app-bg text-muted border border-border",
};

export function Badge({ children, tone = "neutral", className }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium whitespace-nowrap",
        toneClasses[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
