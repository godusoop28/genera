import { describe, expect, it } from "vitest";
import { formatServices, parseServices } from "./property-services";

describe("servicios del inmueble (Control de Cambios #22)", () => {
  it("guarda las casillas y los otros servicios como texto legible y los recupera igual", () => {
    const text = formatServices({ selected: ["Gas natural", "Agua potable"], others: "Cisterna" });
    expect(text).toBe("Agua potable, Gas natural. Otros servicios: Cisterna");
    expect(parseServices(text)).toEqual({ selected: ["Agua potable", "Gas natural"], others: "Cisterna" });
  });

  it("lo capturado antes en texto libre que no es una casilla pasa a otros servicios", () => {
    expect(parseServices("Agua potable, cisterna, Drenaje / alcantarillado")).toEqual({
      selected: ["Agua potable", "Drenaje / Alcantarillado"],
      others: "cisterna",
    });
  });
});
