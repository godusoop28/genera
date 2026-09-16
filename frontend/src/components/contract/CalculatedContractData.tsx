import { Card, CardHeader } from "@/components/ui/Card";
import { formatCurrency } from "@/lib/calculations";
import type { ContractCalculations } from "@/types/expediente";

export function CalculatedContractData({ calculations }: { calculations: ContractCalculations }) {
  const rows: [string, string][] = [
    ["Precio autorizado", calculations.priceNumber ? formatCurrency(calculations.priceNumber) : "Sin definir"],
    ["Precio con letra", calculations.priceWritten || "—"],
    ["Comisión (5%)", formatCurrency(calculations.commission)],
    ["IVA de comisión", formatCurrency(calculations.vat)],
    ["Total comisión + IVA", formatCurrency(calculations.totalCommissionVat)],
    ["Pena convencional (100% de comisión)", formatCurrency(calculations.penalty)],
    ["Vigencia de exclusividad", `${calculations.exclusivityDays} días naturales`],
    ["Fecha de terminación", calculations.exclusivityEndDate],
  ];

  return (
    <Card>
      <CardHeader title="Datos calculados" description="Calculado automáticamente a partir del precio autorizado." />
      <dl className="space-y-3">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between gap-3 text-sm">
            <dt className="text-muted">{label}</dt>
            <dd className="text-right font-medium text-obsessed">{value}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}
