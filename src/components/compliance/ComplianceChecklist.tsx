import { Card, CardHeader } from "@/components/ui/Card";
import { CONTRACT_REVOCATION_BUSINESS_DAYS } from "@/data/contract-reference";
import { computeComplianceItems } from "@/lib/compliance";
import type { Expediente } from "@/types/expediente";
import { CheckCircle2, Circle } from "lucide-react";

export function ComplianceChecklist({ expediente }: { expediente: Expediente }) {
  const items = computeComplianceItems(expediente);
  const doneCount = items.filter((i) => i.done).length;

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Control documental de cumplimiento"
          description={`${doneCount}/${items.length} elementos completados`}
        />
        <p className="mb-4 text-xs text-muted">
          El sistema facilita el seguimiento documental. La revisión jurídica corresponde al
          personal autorizado.
        </p>
        <ul className="space-y-2.5">
          {items.map((item) => (
            <li key={item.id} className="flex items-center gap-2.5 text-sm">
              {item.done ? (
                <CheckCircle2 className="h-4 w-4 shrink-0 text-success-text" aria-hidden />
              ) : (
                <Circle className="h-4 w-4 shrink-0 text-muted" aria-hidden />
              )}
              <span className={item.done ? "text-obsessed" : "text-muted"}>{item.label}</span>
            </li>
          ))}
        </ul>
      </Card>

      <Card className="bg-info-bg/40">
        <p className="text-sm font-medium text-obsessed">Derecho de revocación</p>
        <p className="mt-1 text-sm text-muted">
          El cliente cuenta con {CONTRACT_REVOCATION_BUSINESS_DAYS} días hábiles posteriores a la
          firma para revocar su consentimiento sin responsabilidad, conforme a la cláusula
          séptima del contrato.
        </p>
      </Card>
    </div>
  );
}
