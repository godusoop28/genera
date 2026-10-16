import { Badge } from "@/components/ui/Badge";
import { backendStatusLabels, backendStatusTone } from "@/lib/api/status-labels";
import type { BackendExpedienteStatus } from "@/lib/api/types";
import { Ban, CheckCircle2, Circle, Clock, FileSignature, RefreshCw } from "lucide-react";

const icons: Partial<Record<BackendExpedienteStatus, typeof Clock>> = {
  DRAFT: Circle,
  WAITING_PRIVACY: Clock,
  WAITING_DOCUMENTS: Clock,
  CORRECTIONS_REQUESTED: Clock,
  DOCUMENTS_RECEIVED: RefreshCw,
  UNDER_REVIEW: RefreshCw,
  READY_FOR_SIGNATURE: FileSignature,
  PROPERTY_REJECTED: Ban,
};

/**
 * Estatus real del expediente: texto + icono (no depende solo del color). Con received (documentos
 * con archivo) mientras se esperan documentos se distingue si el cliente ya avanzó: "En proceso"
 * cuando guardó parte de su documentación (Control de Cambios #5) o "Incompleto" si no ha subido nada.
 */
export function ExpedienteStatusBadge({ status, received }: { status: BackendExpedienteStatus; received?: number }) {
  const Icon = icons[status] ?? CheckCircle2;
  const progress = status === "WAITING_DOCUMENTS" && received !== undefined ? (received > 0 ? "En proceso" : "Incompleto") : null;
  return (
    <Badge tone={backendStatusTone[status]}>
      <Icon aria-hidden />
      {backendStatusLabels[status]}
      {progress ? ` · ${progress}` : ""}
    </Badge>
  );
}
