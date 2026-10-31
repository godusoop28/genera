import { Badge } from "@/components/ui/Badge";
import { displayTone, docStatusLabels, type OperationItem } from "@/lib/api/operations";

const dateFmt = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
const dateTimeFmt = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "America/Mexico_City" });

/** Fecha "AAAA-MM-DD" sin desfase por zona horaria. */
export const formatDay = (iso: string | null | undefined) => (iso ? dateFmt.format(new Date(`${iso}T00:00:00Z`)) : "—");
export const formatMoment = (iso: string | null | undefined) => (iso ? dateTimeFmt.format(new Date(iso)) : "—");

/**
 * CC#32: el estado de la operación y el de la documentación van separados; una operación cerrada
 * con faltantes se identifica como "Cerrada — Documentación pendiente".
 */
export function OperationStatusBadge({ item }: { item: Pick<OperationItem, "status" | "docStatus" | "displayStatus"> }) {
  return (
    <div className="flex flex-col items-start gap-1">
      <Badge tone={displayTone(item)}>{item.displayStatus}</Badge>
      {item.status !== "CANCELLED" ? (
        <span className="text-xs text-muted">Documentación: {docStatusLabels[item.docStatus]}</span>
      ) : null}
    </div>
  );
}
