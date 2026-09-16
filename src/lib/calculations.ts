import {
  CONTRACT_COMMISSION_RATE,
  CONTRACT_EXCLUSIVITY_DAYS,
  CONTRACT_PENALTY_RATE,
} from "@/data/contract-reference";
import type { ContractCalculations } from "@/types/expediente";

const UNITS = [
  "",
  "UN",
  "DOS",
  "TRES",
  "CUATRO",
  "CINCO",
  "SEIS",
  "SIETE",
  "OCHO",
  "NUEVE",
  "DIEZ",
  "ONCE",
  "DOCE",
  "TRECE",
  "CATORCE",
  "QUINCE",
];
const TENS = ["", "", "VEINTE", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"];
const HUNDREDS = [
  "",
  "CIENTO",
  "DOSCIENTOS",
  "TRESCIENTOS",
  "CUATROCIENTOS",
  "QUINIENTOS",
  "SEISCIENTOS",
  "SETECIENTOS",
  "OCHOCIENTOS",
  "NOVECIENTOS",
];

function chunkToWords(n: number): string {
  if (n === 0) return "";
  if (n === 100) return "CIEN";
  let words = "";
  const h = Math.floor(n / 100);
  const rest = n % 100;
  if (h > 0) words += HUNDREDS[h] + " ";
  if (rest <= 15) {
    words += UNITS[rest];
  } else if (rest < 20) {
    words += "DIECI" + UNITS[rest - 10];
  } else if (rest < 30) {
    words += rest === 20 ? "VEINTE" : "VEINTI" + UNITS[rest - 20];
  } else {
    const t = Math.floor(rest / 10);
    const u = rest % 10;
    words += TENS[t] + (u > 0 ? " Y " + UNITS[u] : "");
  }
  return words.trim();
}

function integerToWords(n: number): string {
  if (n === 0) return "CERO";
  if (n === 1) return "UN";

  const millions = Math.floor(n / 1_000_000);
  const thousands = Math.floor((n % 1_000_000) / 1000);
  const units = n % 1000;

  let words = "";
  if (millions > 0) {
    words += (millions === 1 ? "UN MILLÓN" : chunkToWords(millions) + " MILLONES") + " ";
  }
  if (thousands > 0) {
    words += (thousands === 1 ? "MIL" : chunkToWords(thousands) + " MIL") + " ";
  }
  if (units > 0) {
    words += chunkToWords(units);
  }
  return words.trim();
}

export function numberToSpanishCurrency(amount: number): string {
  if (!Number.isFinite(amount) || amount <= 0) return "";
  const pesos = Math.floor(amount);
  const cents = Math.round((amount - pesos) * 100);
  const centsStr = cents.toString().padStart(2, "0");
  return `${integerToWords(pesos)} PESOS ${centsStr}/100 M.N.`;
}

export function formatCurrency(amount: number): string {
  return amount.toLocaleString("es-MX", {
    style: "currency",
    currency: "MXN",
    maximumFractionDigits: 2,
  });
}

export function calculateCommission(price: number): number {
  return price * CONTRACT_COMMISSION_RATE;
}

export function calculateVat(commission: number): number {
  return commission * 0.16;
}

export function calculatePenalty(commission: number): number {
  return commission * CONTRACT_PENALTY_RATE;
}

export function calculateExclusivityEndDate(signatureDate: string | Date): Date {
  const start = typeof signatureDate === "string" ? new Date(signatureDate) : signatureDate;
  const end = new Date(start);
  end.setDate(end.getDate() + CONTRACT_EXCLUSIVITY_DAYS);
  return end;
}

export function formatDateEs(date: string | Date): string {
  const d = typeof date === "string" ? new Date(date) : date;
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleDateString("es-MX", { day: "2-digit", month: "long", year: "numeric" });
}

export function buildContractCalculations(
  priceNumber: number,
  signatureDate?: string,
): ContractCalculations {
  const commission = calculateCommission(priceNumber || 0);
  const vat = calculateVat(commission);
  const penalty = calculatePenalty(commission);
  const endDate = calculateExclusivityEndDate(signatureDate ?? new Date());

  return {
    priceNumber: priceNumber || 0,
    priceWritten: priceNumber ? numberToSpanishCurrency(priceNumber) : "",
    commission,
    vat,
    totalCommissionVat: commission + vat,
    penalty,
    exclusivityDays: CONTRACT_EXCLUSIVITY_DAYS,
    exclusivityEndDate: formatDateEs(endDate),
  };
}
