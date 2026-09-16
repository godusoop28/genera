import type { ComplianceItem, Expediente } from "@/types/expediente";

// Checklist de control documental de cumplimiento. Se calcula a partir del
// estado real del expediente (no es un módulo jurídico automatizado).
export function computeComplianceItems(exp: Expediente): ComplianceItem[] {
  const requiredDocs = exp.documentRequirements.filter((r) => r.required);
  const acceptedDocs = requiredDocs.filter((r) => exp.documents[r.id]?.status === "accepted");
  const anexoAComplete = Boolean(
    exp.manualData.bedrooms && exp.manualData.bathrooms && exp.manualData.conservationStatus,
  );

  return [
    {
      id: "modelo-contractual",
      label: "Modelo contractual correcto (PROFECO 7/002193-2026)",
      done: true,
    },
    {
      id: "aviso-presentado",
      label: "Aviso de privacidad presentado al cliente",
      done: Boolean(exp.privacyConsent.signedAt),
    },
    {
      id: "consentimiento-principal",
      label: "Consentimiento principal registrado",
      done: exp.privacyConsent.mainConsent,
    },
    {
      id: "finalidades-secundarias",
      label: "Preferencia de finalidades secundarias registrada",
      done: exp.privacyConsent.signedAt !== undefined,
    },
    {
      id: "carta-derechos",
      label: "Carta de derechos puesta a disposición (Anexo C)",
      done: Boolean(exp.privacyConsent.signedAt),
    },
    { id: "anexo-a", label: "Anexo A — Características del inmueble completo", done: anexoAComplete },
    {
      id: "anexo-b",
      label: "Anexo B — Información puesta a disposición del cliente",
      done: Boolean(exp.privacyConsent.signedAt),
    },
    { id: "anexo-c", label: "Anexo C — Carta de derechos disponible", done: true },
    {
      id: "anexo-d",
      label: "Servicios adicionales / Anexo D revisados",
      done: exp.additionalServices.length === 0 || exp.additionalServices.every((s) => s.acceptedByClient),
    },
    { id: "anexo-e", label: "Aviso de privacidad / Anexo E entregado", done: Boolean(exp.privacyConsent.signedAt) },
    {
      id: "autorizaciones-publicitarias",
      label: "Autorizaciones publicitarias registradas",
      done:
        exp.manualData.marketingDataAuthorized !== undefined &&
        exp.manualData.receiveAdsAuthorized !== undefined,
    },
    { id: "firmas", label: "Firmas requeridas", done: Boolean(exp.receptionSignedAt) },
    {
      id: "contrato-entregado",
      label: "Contrato entregado al cliente",
      done: exp.closing.copyDelivered,
    },
    {
      id: "documentos-revisados",
      label: "Documentos recibidos revisados",
      done: requiredDocs.length > 0 && acceptedDocs.length === requiredDocs.length,
    },
  ];
}
