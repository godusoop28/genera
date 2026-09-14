import { Badge } from "@/components/ui/Badge";
import type { ExpedienteStatus } from "@/types/expediente";

const statusTone: Record<ExpedienteStatus, "neutral" | "warning" | "teal" | "navy" | "success"> = {
  Borrador: "neutral",
  "Esperando documentos": "warning",
  "Documentos recibidos": "teal",
  "En revisión": "navy",
  "Listo para contrato": "success",
};

export function StatusBadge({ status }: { status: ExpedienteStatus }) {
  return <Badge tone={statusTone[status]}>{status}</Badge>;
}
