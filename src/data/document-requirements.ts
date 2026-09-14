import type { DocumentRequirement, ExpedienteConfig } from "@/types/expediente";

export const baseRequiredDocuments: DocumentRequirement[] = [
  {
    id: "ine-1",
    name: "INE del propietario 1",
    description: "Sube frente y reverso de tu credencial para votar.",
    required: true,
  },
  {
    id: "fiscal",
    name: "Constancia de situación fiscal",
    description: "Documento emitido por el SAT.",
    required: true,
  },
  {
    id: "escritura",
    name: "Escritura completa",
    description: "Sube todas las páginas de la escritura del inmueble.",
    required: true,
    multiPage: true,
  },
  {
    id: "domicilio",
    name: "Comprobante de domicilio",
    description: "No mayor a 3 meses de antigüedad.",
    required: true,
  },
  {
    id: "gravamen",
    name: "Certificado de gravamen",
    description: "Emitido por el Registro Público de la Propiedad.",
    required: true,
  },
  {
    id: "predial",
    name: "Predial vigente",
    description: "Comprobante de pago del impuesto predial del año en curso.",
    required: true,
  },
];

export const conditionalDocuments: DocumentRequirement[] = [
  {
    id: "ine-2",
    name: "INE del propietario 2",
    description: "Sube frente y reverso de tu credencial para votar.",
    required: false,
    conditional: true,
  },
  {
    id: "poder",
    name: "Poder notarial",
    description: "Documento que acredita las facultades del representante o apoderado.",
    required: false,
    conditional: true,
  },
  {
    id: "condominio",
    name: "Régimen de condominio",
    description: "Reglamento del condominio y constancia de adeudo.",
    required: false,
    conditional: true,
  },
];

export function getRequiredDocuments(config: ExpedienteConfig): DocumentRequirement[] {
  return conditionalDocuments.map((doc) => {
    if (doc.id === "ine-2") {
      return { ...doc, required: config.ownerCount >= 2 };
    }
    if (doc.id === "poder") {
      return { ...doc, required: config.signedByRepresentative };
    }
    if (doc.id === "condominio") {
      return { ...doc, required: config.condominiumRegime };
    }
    return doc;
  });
}

export function countRequiredDocuments(config: ExpedienteConfig): number {
  const conditional = getRequiredDocuments(config).filter((d) => d.required).length;
  return baseRequiredDocuments.length + conditional;
}
