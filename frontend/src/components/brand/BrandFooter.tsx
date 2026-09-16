import { INDEPENDENT_OFFICE_NOTICE } from "@/data/organization";
import { cn } from "@/lib/utils";

export function BrandFooter({ className }: { className?: string }) {
  return (
    <p className={cn("text-xs text-muted", className)}>{INDEPENDENT_OFFICE_NOTICE}</p>
  );
}
