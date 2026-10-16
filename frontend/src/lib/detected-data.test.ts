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

  it("un pasaporte extranjero en el espacio de la INE no se toma como mexicano", () => {
    const d = buildDetectedData(
      [
        obs("INE", "identificationType", "PASAPORTE"),
        obs("INE", "documentNumber", "A12345678"),
        obs("INE", "issuingCountry", "Estados Unidos de América"),
      ],
      "p1",
    );
    expect(d.participants.p1.idDocumentType?.value).toBe("PASAPORTE");
    expect(d.participants.p1.idDocumentNumber?.value).toBe("A12345678");
    expect(d.participants.p1.nationality).toBeUndefined();
  });

  it("la nacionalidad impresa en otra identificación se prellena", () => {
    const d = buildDetectedData(
      [obs("INE", "identificationType", "Tarjeta de residente"), obs("INE", "documentNumber", "R1"), obs("INE", "nationality", "Colombiana")],
      "p1",
    );
    expect(d.participants.p1.idDocumentType?.value).toBe("FM2_RESIDENTE");
    expect(d.participants.p1.nationality?.value).toBe("Colombiana");
  });

  it("una credencial del INE/IFE leída como tal implica nacionalidad mexicana", () => {
    const d = buildDetectedData([obs("INE", "identificationType", "IFE"), obs("INE", "electorKey", "X")], "p1");
    expect(d.participants.p1.idDocumentType?.value).toBe("INE");
    expect(d.participants.p1.nationality?.value).toBe("Mexicana");
  });

  it("el folio real de la boleta del RPP llega al contrato", () => {
    const d = buildDetectedData([obs("RPP_REGISTRATION_SLIP", "publicRegistryFolio", "FR-889120", null), obs("DEED", "publicRegistryFolio", "otro", null)], "p1");
    expect(d.legal["deed.registryData"]?.value).toBe("FR-889120");
    expect(d.legal["privateContract.realFolio"]?.value).toBe("FR-889120");
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
