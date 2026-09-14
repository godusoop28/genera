import { cn } from "@/lib/utils";

interface BrandMarkProps {
  className?: string;
  showText?: boolean;
}

export function BrandMark({ className, showText = true }: BrandMarkProps) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <svg
        viewBox="0 0 32 32"
        className="h-8 w-8 shrink-0"
        fill="none"
        aria-hidden
      >
        <rect width="32" height="32" rx="9" fill="#0D1F3C" />
        <path
          d="M16 8L23 14.2V24H19.5V17.5H12.5V24H9V14.2L16 8Z"
          fill="#0B9488"
        />
      </svg>
      {showText ? (
        <div className="leading-tight">
          <p className="text-sm font-semibold text-navy">Century 21</p>
          <p className="text-[11px] text-muted">Jorge Jurado</p>
        </div>
      ) : null}
    </div>
  );
}
