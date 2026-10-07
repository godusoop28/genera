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

/** Estatus real del expediente: texto + icono (no depende solo del color). */
export function ExpedienteStatusBadge({ status }: { status: BackendExpedienteStatus }) {
  const Icon = icons[status] ?? CheckCircle2;
  return (
    <Badge tone={backendStatusTone[status]}>
      <Icon aria-hidden />
      {backendStatusLabels[status]}
    </Badge>
  );
}
