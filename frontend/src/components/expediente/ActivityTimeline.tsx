import { Card, CardHeader } from "@/components/ui/Card";
import { formatDateEs } from "@/lib/calculations";
import type { ActivityItem } from "@/types/expediente";
import { Clock } from "lucide-react";

export function ActivityTimeline({ activity }: { activity: ActivityItem[] }) {
  if (activity.length === 0) {
    return (
      <Card>
        <CardHeader title="Actividad" />
        <p className="text-sm text-muted">Sin actividad registrada todavía.</p>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="Actividad" />
      <ol className="space-y-4">
        {activity.map((item, index) => (
          <li key={item.id} className="flex gap-3">
            <div className="flex flex-col items-center">
              <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-gold/15 text-dark-gold">
                <Clock className="h-3.5 w-3.5" aria-hidden />
              </span>
              {index !== activity.length - 1 ? <span className="mt-1 w-px flex-1 bg-border" /> : null}
            </div>
            <div className="pb-4">
              <p className="text-sm text-obsessed">{item.message}</p>
              <p className="text-xs text-muted">{formatDateEs(item.at)}</p>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}
