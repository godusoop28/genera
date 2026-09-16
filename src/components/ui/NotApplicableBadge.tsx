import { Badge } from "@/components/ui/Badge";

export function NotApplicableBadge({ label = "No aplica" }: { label?: string }) {
  return <Badge tone="neutral">{label}</Badge>;
}
