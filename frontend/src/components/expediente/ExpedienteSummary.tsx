import { ActivityTimeline } from "@/components/expediente/ActivityTimeline";
import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import type { Expediente } from "@/types/expediente";
import { propertyTypeLabels } from "@/types/expediente";

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-border py-2.5 text-sm last:border-0">
      <span className="text-muted">{label}</span>
      <span className="font-medium text-obsessed">{value}</span>
    </div>
  );
}

export function ExpedienteSummary({ expediente }: { expediente: Expediente }) {
  const requiredDocs = expediente.documentRequirements.filter((r) => r.required);
  const acceptedDocs = requiredDocs.filter((r) => expediente.documents[r.id]?.status === "accepted");

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.4fr_1fr]">
      <Card>
        <CardHeader title="Resumen del expediente" />
        <Row label="Propietario(s)" value={`${expediente.owners.length} — ${expediente.ownerName}`} />
        <Row label="Tipo de persona" value={expediente.config.personType === "fisica" ? "Persona física" : "Persona moral"} />
        <Row label="Carácter" value={expediente.config.signerCharacter} />
        <Row label="Tipo de inmueble" value={propertyTypeLabels[expediente.config.propertyType]} />
        <Row label="Privacidad" value={expediente.privacyConsent.mainConsent ? "Aceptada" : "Pendiente"} />
        <Row label="Documentos" value={`${acceptedDocs.length}/${requiredDocs.length} aceptados`} />
        <Row
          label="Decisión del inmueble"
          value={
            expediente.propertyDecision.decision === "pending"
              ? "Pendiente"
              : expediente.propertyDecision.decision === "accepted"
                ? "Aceptada"
                : "Rechazada"
          }
        />
        <div className="mt-4 flex flex-wrap gap-2">
          {expediente.config.signerCharacter === "apoderado" ? <Badge tone="gold">Apoderado</Badge> : null}
          {expediente.config.condominiumRegime ? <Badge tone="gold">Régimen de condominio</Badge> : null}
          {expediente.owners.length > 1 ? <Badge tone="gold">{expediente.owners.length} propietarios</Badge> : null}
        </div>
      </Card>

      <ActivityTimeline activity={expediente.activity} />
    </div>
  );
}
