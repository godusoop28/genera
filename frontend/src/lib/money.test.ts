import { describe, expect, it } from "vitest";
import {
  digitsBefore,
  formatMoneyInput,
  formatMoneyWithCurrency,
  groupThousands,
  moneyToRaw,
  normalizeMoneyInput,
  positionAfterDigits,
} from "./money";

describe("normalizeMoneyInput (CC#38)", () => {
  it("quita el formato al escribir o pegar", () => {
    expect(normalizeMoneyInput("5000000")).toBe("5000000");
    expect(normalizeMoneyInput("$5,000,000")).toBe("5000000");
    expect(normalizeMoneyInput("$5,000,000.00 MXN")).toBe("5000000.00");
    expect(normalizeMoneyInput("5 000 000")).toBe("5000000");
    expect(normalizeMoneyInput("5.000.000")).toBe("5000000");
  });

  it("conserva hasta dos decimales y el punto mientras se escribe", () => {
    expect(normalizeMoneyInput("1250.5")).toBe("1250.5");
    expect(normalizeMoneyInput("1250.")).toBe("1250.");
    expect(normalizeMoneyInput("1250.567")).toBe("1250.56");
    expect(normalizeMoneyInput(".5")).toBe("0.5");
  });

  it("un campo vacío sigue vacío (nunca cero)", () => {
    expect(normalizeMoneyInput("")).toBe("");
    expect(normalizeMoneyInput("$")).toBe("");
    expect(normalizeMoneyInput("MXN")).toBe("");
  });

  it("quita ceros a la izquierda sin borrar el cero solo", () => {
    expect(normalizeMoneyInput("007")).toBe("7");
    expect(normalizeMoneyInput("0")).toBe("0");
  });
});

describe("formato visual", () => {
  it("agrupa miles sin cambiar el importe", () => {
    expect(groupThousands("5000000")).toBe("5,000,000");
    expect(groupThousands("5000000.5")).toBe("5,000,000.5");
    expect(formatMoneyInput("5000000")).toBe("$5,000,000");
    expect(formatMoneyInput("")).toBe("");
    expect(normalizeMoneyInput(formatMoneyInput("1234567.89"))).toBe("1234567.89");
  });

  it("muestra la moneda", () => {
    expect(formatMoneyWithCurrency(5000000)).toBe("$5,000,000 MXN");
    expect(formatMoneyWithCurrency("5000000.50")).toBe("$5,000,000.50 MXN");
    expect(formatMoneyWithCurrency(null)).toBe("—");
    expect(formatMoneyWithCurrency(250000, "USD")).toBe("$250,000 USD");
  });

  it("convierte el valor del servidor sin perder centavos", () => {
    expect(moneyToRaw("5000000.00")).toBe("5000000");
    expect(moneyToRaw("5000000.50")).toBe("5000000.5");
    expect(moneyToRaw(null)).toBe("");
    expect(moneyToRaw(0)).toBe("0");
  });
});

describe("cursor al reformatear", () => {
  it("deja el cursor después del mismo dígito", () => {
    // "$5,0|00" (3 dígitos antes) → en "$50,000" queda después del tercer dígito.
    expect(digitsBefore("$5,0|00".replace("|", ""), 4)).toBe(2);
    expect(positionAfterDigits("$50,000", 3)).toBe(5);
    expect(positionAfterDigits("$50,000", 0)).toBe(1);
  });
});
