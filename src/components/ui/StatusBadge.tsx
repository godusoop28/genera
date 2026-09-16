import { Badge } from "@/components/ui/Badge";
import { expedienteStatusLabels, type ExpedienteStatus } from "@/types/expediente";

type Tone = "neutral" | "warning" | "gold" | "obsessed" | "success" | "danger" | "info";

const statusTone: Record<ExpedienteStatus, Tone> = {
  draft: "neutral",
  waiting_privacy: "warning",
  waiting_documents: "warning",
  documents_received: "info",
  under_review: "gold",
  corrections_requested: "warning",
  documents_approved: "success",
  contract_preparation: "info",
  ready_for_signature: "gold",
  property_accepted: "success",
  property_rejected: "danger",
  closed: "obsessed",
};

export function StatusBadge({ status }: { status: ExpedienteStatus }) {
  return <Badge tone={statusTone[status]}>{expedienteStatusLabels[status]}</Badge>;
}
