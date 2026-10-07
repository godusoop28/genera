import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

interface PageContainerProps {
  title: string;
  subtitle?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function PageContainer({ title, subtitle, action, children, className }: PageContainerProps) {
  return (
    <div className={cn("mx-auto w-full max-w-[1440px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8", className)}>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight text-obsessed sm:text-[1.75rem]">{title}</h1>
          {subtitle ? <p className="mt-1 text-sm text-muted sm:text-[0.95rem]">{subtitle}</p> : null}
        </div>
        {action ? <div className="flex shrink-0 flex-wrap items-center gap-2">{action}</div> : null}
      </div>
      {children}
    </div>
  );
}
