import { Card, CardHeader } from "@/components/ui/Card";
import { CheckCircle2 } from "lucide-react";

export function ProcessingStatus() {
  return (
    <Card className="bg-teal-light/50 border-teal/20">
      <CardHeader
        title="Documentos listos"
        description="Los documentos están organizados y la extracción de información se completó correctamente."
      />
      <ul className="space-y-2.5">
        <li className="flex items-center gap-2.5 text-sm font-medium text-navy">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-teal-dark" aria-hidden />
          6 de 6 documentos procesados
        </li>
        <li className="flex items-center gap-2.5 text-sm font-medium text-navy">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-teal-dark" aria-hidden />
          Información extraída
        </li>
      </ul>
    </Card>
  );
}
