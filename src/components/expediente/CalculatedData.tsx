import { Card, CardHeader } from "@/components/ui/Card";
import { calculatedData } from "@/data/mock-expediente";

export function CalculatedData() {
  return (
    <Card>
      <CardHeader title="Datos calculados" />
      <dl className="space-y-3">
        {calculatedData.map((item) => (
          <div key={item.label} className="flex items-center justify-between gap-3 text-sm">
            <dt className="text-muted">{item.label}</dt>
            <dd className="font-medium text-navy">{item.value}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}
