import { describe, expect, it } from "vitest";
import { uploadLinkIdentifier, uploadLinkMessage, whatsappShareUrl } from "./share-links";

const base = { propertyReference: null, propertyTypeLabel: null, propertyKey: null, folio: "EXP-2026-000123", ownerDisplayName: "María Pérez" };

describe("liga del cliente para WhatsApp (CC#35)", () => {
  it("usa el nombre del inmueble y del propietario", () => {
    expect(uploadLinkIdentifier({ ...base, propertyReference: "Casa Las Flores" })).toBe("Casa Las Flores — María Pérez");
  });

  it("sin nombre del inmueble usa el tipo con su clave o el folio", () => {
    expect(uploadLinkIdentifier({ ...base, propertyTypeLabel: "Habitacional → Casa", propertyKey: "CVEA03" })).toBe("Habitacional → Casa CVEA03 — María Pérez");
    expect(uploadLinkIdentifier(base)).toBe("EXP-2026-000123 — María Pérez");
  });

  it("una persona moral se identifica con su razón social", () => {
    expect(uploadLinkIdentifier({ ...base, propertyReference: "Bodega Norte", ownerDisplayName: "Inmobiliaria del Valle SA de CV" })).toBe(
      "Bodega Norte — Inmobiliaria del Valle SA de CV",
    );
  });

  it("el mensaje lleva el identificador, el texto de acceso y la liga intacta", () => {
    const url = "https://genera.example/carga/abc123";
    const message = uploadLinkMessage("Casa Las Flores — María Pérez", url);
    expect(message.startsWith("Casa Las Flores — María Pérez\n")).toBe(true);
    expect(message).toContain("No necesitas crear una cuenta");
    expect(message.endsWith(url)).toBe(true);
  });

  it("abre el chat del cliente si hay teléfono", () => {
    expect(whatsappShareUrl("hola", "777 123 4567")).toBe("https://wa.me/527771234567?text=hola");
    expect(whatsappShareUrl("hola", null)).toBe("https://wa.me/?text=hola");
  });
});
