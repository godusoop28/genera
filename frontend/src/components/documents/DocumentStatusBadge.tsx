import { Badge } from "@/components/ui/Badge";
import { documentStatusLabels, type DocumentStatus } from "@/types/expediente";

type Tone = "neutral" | "warning" | "gold" | "obsessed" | "success" | "danger" | "info";

const tones: Record<DocumentStatus, Tone> = {
  pending: "neutral",
  uploaded: "info",
  processing: "gold",
  ready_for_review: "gold",
  accepted: "success",
  returned: "warning",
  rejected: "danger",
  replaced: "info",
};

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  return <Badge tone={tones[status]}>{documentStatusLabels[status]}</Badge>;
}
