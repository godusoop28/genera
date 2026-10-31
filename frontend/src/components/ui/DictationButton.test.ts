import { describe, expect, it } from "vitest";
import { appendDictation } from "./DictationButton";

describe("dictado de reportes (CC#40)", () => {
  it("agrega lo dictado al final sin borrar lo escrito", () => {
    expect(appendDictation("Le llamé.", "quedó de confirmar el jueves")).toBe("Le llamé. quedó de confirmar el jueves");
    expect(appendDictation("Le llamé. ", "quedó")).toBe("Le llamé. quedó");
  });
  it("en un campo vacío empieza con mayúscula", () => {
    expect(appendDictation("", "visitó la casa")).toBe("Visitó la casa");
  });
  it("ignora resultados vacíos", () => {
    expect(appendDictation("Hola", "   ")).toBe("Hola");
  });
});
