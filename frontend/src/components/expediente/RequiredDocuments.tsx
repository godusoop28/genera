import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { NotApplicableBadge } from "@/components/ui/NotApplicableBadge";
import type { DocumentRequirement } from "@/types/expediente";
import { CheckCircle2, Circle, Info } from "lucide-react";

interface RequiredDocumentsProps {
  requirements: DocumentRequirement[];
}

function DocumentRow({ doc }: { doc: DocumentRequirement }) {
  return (
    <li className="flex items-center justify-between gap-3 rounded-xl border border-border px-4 py-3">
      <div className="flex items-center gap-2.5">
        {doc.required ? (
          <CheckCircle2 className="h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
        ) : (
          <Circle className="h-4 w-4 shrink-0 text-border" aria-hidden />
        )}
        <span className={doc.required ? "text-sm text-obsessed" : "text-sm text-muted"}>{doc.name}</span>
      </div>
      {doc.required ? (
        <Badge tone="gold">Obligatorio</Badge>
      ) : doc.conditional ? (
        <NotApplicableBadge />
      ) : (
        <Badge>Opcional</Badge>
      )}
    </li>
  );
}

export function RequiredDocuments({ requirements }: RequiredDocumentsProps) {
  const requiredCount = requirements.filter((d) => d.required).length;

  return (
    <Card>
      <CardHeader title="Documentos requeridos" action={<Badge tone="gold">{requiredCount} obligatorios</Badge>} />

      <ul className="space-y-2.5">
        {requirements.map((doc) => (
          <DocumentRow key={doc.id} doc={doc} />
        ))}
      </ul>

      <div className="mt-6 flex gap-3 rounded-xl bg-gold/10 p-4">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
        <div>
          <p className="text-sm font-medium text-obsessed">
            La lista de documentos se actualiza automáticamente
          </p>
          <p className="mt-0.5 text-xs text-muted">
            Cambia según el número de propietarios, el carácter de quien firma y si el inmueble
            está en régimen de condominio.
          </p>
        </div>
      </div>
    </Card>
  );
}
