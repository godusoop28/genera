import { cn } from "@/lib/utils";
import { AlertTriangle, Inbox, Loader2, Lock, SearchX } from "lucide-react";
import type { ReactNode } from "react";

type Kind = "loading" | "empty" | "no-results" | "error" | "restricted";

const icons = {
  loading: Loader2,
  empty: Inbox,
  "no-results": SearchX,
  error: AlertTriangle,
  restricted: Lock,
} as const;

const iconTone: Record<Kind, string> = {
  loading: "bg-app-bg text-muted",
  empty: "bg-gold/15 text-dark-gold",
  "no-results": "bg-app-bg text-muted",
  error: "bg-danger-bg text-danger-text",
  restricted: "bg-warning-bg text-warning-text",
};

interface StateMessageProps {
  kind: Kind;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}

/** Estados de pantalla consistentes: carga, vacío, sin resultados, error y acceso restringido. */
export function StateMessage({ kind, title, description, action, className }: StateMessageProps) {
  const Icon = icons[kind];
  return (
    <div
      role={kind === "error" ? "alert" : "status"}
      aria-live="polite"
      className={cn("flex flex-col items-center rounded-xl border border-dashed border-border bg-card px-6 py-12 text-center", className)}
    >
      <span className={cn("flex h-12 w-12 items-center justify-center rounded-full", iconTone[kind])}>
        <Icon className={cn("h-5 w-5", kind === "loading" && "animate-spin")} aria-hidden />
      </span>
      <p className="mt-4 text-base font-semibold text-obsessed">{title}</p>
      {description ? <p className="mt-1 max-w-md text-sm text-muted">{description}</p> : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}
