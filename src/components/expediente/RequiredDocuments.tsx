import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { baseRequiredDocuments, countRequiredDocuments } from "@/data/document-requirements";
import type { DocumentRequirement, ExpedienteConfig } from "@/types/expediente";
import { CheckCircle2, Circle, Info } from "lucide-react";

interface RequiredDocumentsProps {
  config: ExpedienteConfig;
  conditionalDocuments: DocumentRequirement[];
}

function DocumentRow({ doc }: { doc: DocumentRequirement }) {
  return (
    <li className="flex items-center justify-between gap-3 rounded-xl border border-border px-4 py-3">
      <div className="flex items-center gap-2.5">
        {doc.required ? (
          <CheckCircle2 className="h-4 w-4 shrink-0 text-teal" aria-hidden />
        ) : (
          <Circle className="h-4 w-4 shrink-0 text-border" aria-hidden />
        )}
        <span className={doc.required ? "text-sm text-navy" : "text-sm text-muted"}>
          {doc.name}
        </span>
      </div>
      <Badge tone={doc.required ? "teal" : "neutral"}>
        {doc.required ? "Obligatorio" : "No requerido"}
      </Badge>
    </li>
  );
}

export function RequiredDocuments({ config, conditionalDocuments }: RequiredDocumentsProps) {
  const requiredCount = countRequiredDocuments(config);

  return (
    <Card>
      <CardHeader
        title="Documentos requeridos"
        action={<Badge tone="teal">{requiredCount} obligatorios</Badge>}
      />

      <ul className="space-y-2.5">
        {baseRequiredDocuments.map((doc) => (
          <DocumentRow key={doc.id} doc={doc} />
        ))}
      </ul>

      <div className="mt-6">
        <p className="mb-2.5 text-xs font-medium uppercase tracking-wide text-muted">
          Documentos según configuración
        </p>
        <ul className="space-y-2.5">
          {conditionalDocuments.map((doc) => (
            <DocumentRow key={doc.id} doc={doc} />
          ))}
        </ul>
      </div>

      <div className="mt-6 flex gap-3 rounded-xl bg-teal-light/70 p-4">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-teal-dark" aria-hidden />
        <div>
          <p className="text-sm font-medium text-navy">
            La lista de documentos se actualiza automáticamente
          </p>
          <p className="mt-0.5 text-xs text-muted">
            Los documentos requeridos cambian según las opciones seleccionadas en la
            configuración del expediente.
          </p>
        </div>
      </div>
    </Card>
  );
}
