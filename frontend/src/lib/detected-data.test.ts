import { describe, expect, it } from "vitest";
import type { ExpedienteObservationResponse } from "@/lib/api/types";
import { buildDetectedData } from "./detected-data";

const obs = (documentType: string, fieldName: string, value: string, participantId: string | null = "p1"): ExpedienteObservationResponse => ({
  id: `${documentType}-${fieldName}`,
  documentId: documentType,
  documentType,
  participantId: participantId ?? (null as unknown as string),
  documentStatus: "ACCEPTED",
  fieldName,
  value,
  confirmed: false,
  confidence: 0.99,
});

describe("buildDetectedData (prellenado desde documentos)", () => {
  it("lleva los datos de la INE y la CSF a la persona correcta, con la nacionalidad que implica la INE", () => {
    const d = buildDetectedData(
      [
        obs("INE", "electorKey", "RDCACL85031417H900"),
        obs("INE", "birthDate", "14/03/1985"),
        obs("TAX_STATUS_CERTIFICATE", "rfc", "ROCC850314TQ7"),
        obs("INE", "electorKey", "MRSOLA87092209M800", "p2"),
        obs("TAX_STATUS_CERTIFICATE", "rfc", "MASL870922K31", "p2"),
      ],
      "p1",
    );
    expect(d.participants.p1).toMatchObject({
      idDocumentNumber: { value: "RDCACL85031417H900" },
      idDocumentType: { value: "INE" },
      birthDate: { value: "1985-03-14" },
      rfc: { value: "ROCC850314TQ7" },
      nationality: { value: "Mexicana" },
    });
    // No se mezclan los copropietarios.
    expect(d.participants.p2.rfc?.value).toBe("MASL870922K31");
    expect(d.participants.p2.idDocumentNumber?.value).toBe("MRSOLA87092209M800");
  });

  it("si hay pasaporte, su nacionalidad gana sobre la deducida de la INE", () => {
    const d = buildDetectedData([obs("INE", "electorKey", "X"), obs("PASSPORT", "nationality", "MEXICANA")], "p1");
    expect(d.participants.p1.nationality?.value).toBe("MEXICANA");
  });

  it("los datos de la escritura llegan al contrato con fecha y superficies normalizadas", () => {
    const d = buildDetectedData(
      [obs("DEED", "deedNumber", "12,345", null), obs("DEED", "deedDate", "2022-04-18", null), obs("DEED", "landArea", "240.00 m2", null)],
      "p1",
    );
    expect(d.legal["deed.number"]?.value).toBe("12,345");
    expect(d.legal["deed.date"]?.value).toBe("2022-04-18");
    expect(d.landAreaM2?.value).toBe("240.00");
  });
});
