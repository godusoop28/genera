import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import {
  CONTRACT_INTERMEDIARY_ADDRESS,
  CONTRACT_INTERMEDIARY_COMMERCIAL_NAME,
  CONTRACT_INTERMEDIARY_EMAIL,
  CONTRACT_INTERMEDIARY_PHONE,
  CONTRACT_INTERMEDIARY_RFC,
  CONTRACT_LEGAL_REPRESENTATIVE,
  CONTRACT_MODALITY,
  CONTRACT_PROFECO_NUMBER,
  CONTRACT_PROFECO_REGISTRATION_DATE,
  CONTRACT_TITLE,
} from "@/data/contract-reference";

export function ContractInfo() {
  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-obsessed">{CONTRACT_TITLE}</h2>
          <p className="mt-1 text-sm text-muted">
            {CONTRACT_INTERMEDIARY_COMMERCIAL_NAME} · Representante legal: {CONTRACT_LEGAL_REPRESENTATIVE}
          </p>
        </div>
        <Badge tone="gold">PROFECO {CONTRACT_PROFECO_NUMBER}</Badge>
      </div>

      <div className="mt-4 grid gap-x-6 gap-y-1 border-t border-border pt-4 text-sm text-muted sm:grid-cols-2">
        <p>Registrado ante PROFECO el {CONTRACT_PROFECO_REGISTRATION_DATE}</p>
        <p>Modalidad: {CONTRACT_MODALITY} · 180 días naturales</p>
        <p>Domicilio: {CONTRACT_INTERMEDIARY_ADDRESS}</p>
        <p>
          RFC {CONTRACT_INTERMEDIARY_RFC} · {CONTRACT_INTERMEDIARY_EMAIL} · {CONTRACT_INTERMEDIARY_PHONE}
        </p>
      </div>
    </Card>
  );
}
