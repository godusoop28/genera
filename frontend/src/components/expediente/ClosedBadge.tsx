import { Badge } from "@/components/ui/Badge";
import { Flag } from "lucide-react";

/** "2026-10-29" → "29 oct 2026" sin desfase de zona horaria (es una fecha, no un instante). */
export function formatClosingDate(isoDate: string, style: "medium" | "long" = "medium"): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Intl.DateTimeFormat("es-MX", { dateStyle: style, timeZone: "UTC" }).format(new Date(Date.UTC(y, m - 1, d)));
}

/**
 * CC#32: la operación ya tiene el seguimiento de cierre completado. Es una marca aparte del estatus
 * del expediente (que no cambia), con la fecha de cierre cuando está registrada.
 */
export function ClosedBadge({ closedOn, withDate = true }: { closedOn: string; withDate?: boolean }) {
  return (
    <Badge tone="success">
      <Flag aria-hidden />
      Cerrada{withDate ? ` · ${formatClosingDate(closedOn)}` : ""}
    </Badge>
  );
}
