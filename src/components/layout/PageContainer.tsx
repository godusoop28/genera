import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

interface PageContainerProps {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function PageContainer({ title, subtitle, action, children, className }: PageContainerProps) {
  return (
    <div className={cn("mx-auto max-w-7xl px-4 py-8 lg:px-8 lg:py-10", className)}>
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-obsessed">{title}</h1>
          {subtitle ? <p className="mt-1.5 text-sm text-muted">{subtitle}</p> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      {children}
    </div>
  );
}
