import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

type BadgeTone = "gold" | "obsessed" | "success" | "warning" | "danger" | "info" | "manual" | "neutral";

interface BadgeProps {
  children: ReactNode;
  tone?: BadgeTone;
  className?: string;
}

const toneClasses: Record<BadgeTone, string> = {
  gold: "bg-gold/15 text-dark-gold",
  obsessed: "bg-obsessed/5 text-obsessed",
  success: "bg-success-bg text-success-text",
  warning: "bg-warning-bg text-warning-text",
  danger: "bg-danger-bg text-danger-text",
  info: "bg-info-bg text-info-text",
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
