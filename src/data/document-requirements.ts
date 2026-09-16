import type { DocumentRequirement, ExpedienteConfig, Owner } from "@/types/expediente";

export function buildOwners(count: number): Owner[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `owner-${i + 1}`,
    label: `Propietario ${i + 1}`,
  }));
}

// Genera los requisitos documentales del expediente según su configuración.
// Regla del prompt maestro §17: el INE debe generarse dinámicamente por cada
// propietario (antes el sistema solo soportaba 2 — corregido aquí).
export function buildDocumentRequirements(
  config: ExpedienteConfig,
  owners: Owner[],
): DocumentRequirement[] {
  const requirements: DocumentRequirement[] = [];

  for (const owner of owners) {
    requirements.push({
      id: `ine-${owner.id}`,
      name: `INE de ${owner.label.toLowerCase()}`,
      description: "Sube frente y reverso de la credencial para votar.",
      category: "identidad",
      required: true,
      ownerId: owner.id,
    });
  }

  requirements.push(
    {
      id: "fiscal",
      name: "Constancia de Situación Fiscal",
      description: "Documento emitido por el SAT.",
      category: "fiscal",
      required: true,
    },
    {
      id: "escritura",
      name: "Escritura completa",
      description: "Todas las páginas de la escritura del inmueble.",
      category: "propiedad",
      required: true,
    },
    {
      id: "domicilio",
      name: "Comprobante de domicilio",
      description: "No mayor a 3 meses de antigüedad.",
      category: "propiedad",
      required: true,
    },
    {
      id: "gravamen",
      name: "Certificado actualizado de libertad o existencia de gravamen",
      description: "Emitido por el Registro Público de la Propiedad.",
      category: "propiedad",
      required: true,
    },
    {
      id: "predial",
      name: "Predial vigente",
      description: "Comprobante de pago del impuesto predial del año en curso.",
      category: "propiedad",
      required: true,
    },
  );

  requirements.push({
    id: "poder",
    name: "Poder notarial",
    description: "Acredita las facultades del representante o apoderado.",
    category: "identidad",
    required: config.signerCharacter === "apoderado",
    conditional: true,
  });

  requirements.push({
    id: "condominio",
    name: "Régimen de condominio",
    description: "Reglamento del condominio y constancia de adeudo.",
    category: "propiedad",
    required: config.condominiumRegime,
    conditional: true,
  });

  return requirements;
}

export function countRequiredDocuments(requirements: DocumentRequirement[]): number {
  return requirements.filter((d) => d.required).length;
}
