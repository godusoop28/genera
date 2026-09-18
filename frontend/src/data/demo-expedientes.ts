import { createExpediente, defaultExtractedFieldsFor, makeActivity } from "@/lib/expediente-factory";
import type { Expediente } from "@/types/expediente";

function heroExpediente(): Expediente {
  const exp = createExpediente({
    folio: "EXP-2026-001",
    ownerName: "Juan Pérez López",
    propertyAddress: "Calle Roble 456, Col. Nápoles, Benito Juárez, CDMX",
    config: {
      ownerCount: 1,
      personType: "fisica",
      signerCharacter: "propietario",
      accreditation: "escritura_publica",
      condominiumRegime: false,
      propertyType: "casa",
      legalStatus: "libre_gravamen",
      civilStatus: "casado",
      maritalPropertyRegime: "bienes_mancomunados",
    },
  });

  exp.id = "demo";
  exp.linkId = "demo-expediente";
  exp.status = "under_review";
  exp.privacyConsent = {
    mainConsent: true,
    secondaryConsent: false,
    signedAt: "2026-09-14T10:05:00.000Z",
  };
  exp.manualData = {
    civilStatus: "Casado(a)",
    authorizedPrice: 6_000_000,
    email: "juan.perez@email.com",
    phone: "55 1234 5678",
    marketingDataAuthorized: true,
    receiveAdsAuthorized: false,
    bedrooms: 3,
    bathrooms: 2,
    parkingSpots: 2,
    conservationStatus: "Bueno",
    availableServices: "Agua, luz, drenaje, gas natural",
    relevantFeatures: "Casa de dos niveles, jardín trasero, próxima a avenida principal.",
    contractSignatureDate: "2026-09-14",
  };

  const fields = defaultExtractedFieldsFor(exp);
  const overrides: Record<string, string> = {
    "folio-id": "PELJ800312H12",
    "fecha-nacimiento": "12/03/1980",
    edad: "46 años",
    "domicilio-cliente": "Av. Insurgentes Sur 1234, Col. Del Valle, Benito Juárez, CDMX",
    rfc: "PELJ800312ABC",
    "num-escritura": "12,345",
    "fecha-escritura": "15/06/2020",
    notario: "Lic. María González Ruiz",
    "num-notaria": "98",
    demarcacion: "Benito Juárez",
    estado: "Ciudad de México",
    "folio-real": "09012345",
    "superficie-terreno": "240 m²",
    "superficie-construccion": "212 m²",
    gravamenes: "Libre de gravamen",
    "situacion-predial": "Al corriente",
  };
  exp.extractedFields = fields.map((f) => (overrides[f.id] ? { ...f, value: overrides[f.id] } : f));

  for (const req of exp.documentRequirements.filter((r) => r.required)) {
    exp.documents[req.id] = {
      requirementId: req.id,
      status: "accepted",
      pages: [],
      uploadedAt: "2026-09-14T09:00:00.000Z",
      review: {
        decision: "accepted",
        reviewedAt: "2026-09-14T11:30:00.000Z",
        reviewedBy: "Jorge Ricardo Jurado Espinal",
      },
    };
  }

  exp.receptionSignedAt = "2026-09-14T11:45:00.000Z";
  exp.receptionSignedBy = "Jorge Ricardo Jurado Espinal";

  exp.activity = [
    makeActivity("expediente_creado", "Expediente EXP-2026-001 creado.", "2026-09-13T15:00:00.000Z"),
    makeActivity("liga_generada", "Liga de recepción documental generada.", "2026-09-13T15:02:00.000Z"),
    makeActivity("aviso_aceptado", "Juan Pérez López aceptó el aviso de privacidad.", "2026-09-14T10:05:00.000Z"),
    makeActivity("documentos_enviados", "El cliente envió su documentación.", "2026-09-14T10:40:00.000Z"),
    makeActivity("ia_procesada", "Validación automática completada para 6 documentos.", "2026-09-14T10:42:00.000Z"),
    makeActivity("documento_aceptado", "Todos los documentos requeridos fueron aceptados.", "2026-09-14T11:30:00.000Z"),
    makeActivity("recepcion_firmada", "Jorge Ricardo Jurado Espinal firmó la recepción documental.", "2026-09-14T11:45:00.000Z"),
  ];

  return exp;
}

function secondaryExpediente(
  folio: string,
  ownerName: string,
  status: Expediente["status"],
  ownerCount: 1 | 2 | 3,
  updatedAt: string,
): Expediente {
  const exp = createExpediente({
    folio,
    ownerName,
    config: { ownerCount },
  });
  exp.status = status;
  exp.updatedAt = updatedAt;
  if (status !== "draft") {
    exp.privacyConsent = { mainConsent: true, secondaryConsent: false, signedAt: updatedAt };
  }
  return exp;
}

function correctionsExpediente(): Expediente {
  const exp = secondaryExpediente("EXP-2026-003", "Carlos Ramírez", "corrections_requested", 2, "2026-09-12T09:00:00.000Z");
  exp.documents["recibo-cfe"] = {
    requirementId: "recibo-cfe",
    status: "returned",
    pages: [],
    uploadedAt: "2026-09-11T09:00:00.000Z",
    review: {
      decision: "returned",
      reason: "informacion_ilegible",
      comment: "La imagen no permite leer claramente la fecha de expedición.",
      reviewedAt: "2026-09-12T09:00:00.000Z",
      reviewedBy: "Carlos Mendoza",
    },
  };
  exp.activity = [
    makeActivity("documento_devuelto", "Documento devuelto para corrección: Recibo de CFE.", "2026-09-12T09:00:00.000Z"),
    makeActivity("documentos_enviados", "El cliente envió su documentación.", "2026-09-11T09:05:00.000Z"),
    makeActivity("expediente_creado", "Expediente EXP-2026-003 creado.", "2026-09-10T09:00:00.000Z"),
  ];
  return exp;
}

export const demoExpedientes: Expediente[] = [
  heroExpediente(),
  secondaryExpediente("EXP-2026-002", "María Fernanda Ruiz", "waiting_documents", 1, "2026-09-15T09:00:00.000Z"),
  correctionsExpediente(),
  secondaryExpediente("EXP-2026-004", "Lucía Hernández Soto", "draft", 1, "2026-09-10T09:00:00.000Z"),
  secondaryExpediente("EXP-2026-005", "Roberto Álvarez Mena", "ready_for_signature", 3, "2026-09-08T09:00:00.000Z"),
];
