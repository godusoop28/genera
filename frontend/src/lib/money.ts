/**
 * CC#38: captura y presentación de importes. El valor "crudo" que se guarda y se manda al servidor
 * es siempre texto con solo dígitos y, si acaso, un punto decimal ("5000000" o "5000000.5"); el
 * formato con "$" y separadores de miles es solo visual. Vacío sigue siendo vacío (nunca 0).
 */

/**
 * Convierte lo que el usuario escribe o pega ("$5,000,000.00 MXN", "5 000 000", "5.000.000") en el
 * valor crudo. Las comas siempre son separadores de miles (es-MX); si hay más de un punto, también
 * se toman como separadores de miles.
 */
export function normalizeMoneyInput(text: string, maxDecimals = 2): string {
  let s = text.replace(/[^\d.]/g, "");
  if ((s.match(/\./g) ?? []).length > 1) {
    s = s.replace(/\./g, "");
  }
  const [intPart, decPart] = s.split(".");
  const integer = intPart.replace(/^0+(?=\d)/, "");
  if (decPart === undefined || maxDecimals === 0) return integer;
  return `${integer || "0"}.${decPart.slice(0, maxDecimals)}`;
}

/** "5000000.5" → "5,000,000.5". Conserva un punto o ceros finales mientras se escribe ("5,000."). */
export function groupThousands(raw: string): string {
  if (!raw) return "";
  const [intPart, decPart] = raw.split(".");
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return decPart === undefined ? grouped : `${grouped}.${decPart}`;
}

/** Texto que se ve en el campo: "$5,000,000". */
export function formatMoneyInput(raw: string): string {
  return raw ? `$${groupThousands(raw)}` : "";
}

/** Valor del servidor (número o texto decimal) → valor crudo del campo; null/vacío → "". */
export function moneyToRaw(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return "";
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n)) return "";
  // Sin ceros decimales de más ("5000000.00" → "5000000"; "5000000.50" → "5000000.5").
  return normalizeMoneyInput(String(Number(n.toFixed(2))));
}

/** "5000000" → "$5,000,000 MXN"; con centavos, dos decimales. Vacío → fallback. */
export function formatMoneyWithCurrency(value: string | number | null | undefined, currency = "MXN", fallback = "—"): string {
  if (value === null || value === undefined || value === "") return fallback;
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n)) return fallback;
  const cents = Math.round(n * 100) % 100 !== 0;
  const amount = n.toLocaleString("es-MX", { minimumFractionDigits: cents ? 2 : 0, maximumFractionDigits: 2 });
  return `$${amount} ${currency}`;
}

/** Cuántos dígitos hay antes de la posición indicada (para conservar el cursor al reformatear). */
export function digitsBefore(text: string, position: number): number {
  return (text.slice(0, position).match(/[\d.]/g) ?? []).length;
}

/** Posición en el texto formateado que deja el cursor después de n dígitos (o del punto decimal). */
export function positionAfterDigits(formatted: string, digits: number): number {
  if (digits <= 0) return formatted.startsWith("$") ? 1 : 0;
  let seen = 0;
  for (let i = 0; i < formatted.length; i++) {
    if (/[\d.]/.test(formatted[i])) {
      seen++;
      if (seen === digits) return i + 1;
    }
  }
  return formatted.length;
}
