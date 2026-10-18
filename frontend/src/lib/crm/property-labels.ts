import type { ListingStatus, Operation, PropertyType, SyncStatus } from "@/lib/api/properties";

type Tone = "gold" | "obsessed" | "success" | "warning" | "danger" | "info" | "manual" | "neutral";

export const operationLabels: Record<Operation, string> = { venta: "Venta", renta: "Renta" };

export const typeLabels: Record<PropertyType, string> = { habitacional: "Habitacional", comercial: "Comercial" };

/** Subtipos que 21Online acepta en México por tipo (igual que PropertyService.SUBTYPES). */
export const subtypesByType: Record<PropertyType, string[]> = {
  habitacional: ["casa", "casa-duplex", "casa-en-condominio", "departamento", "penthouse", "cabana", "terreno", "huerta", "quinta", "rancho", "hacienda", "edificio"],
  comercial: ["local", "oficinas", "bodega", "nave", "fabrica", "terreno", "edificio", "fraccionamiento", "escuela", "inmueble-productivo"],
};

export const subtypeLabels: Record<string, string> = {
  casa: "Casa",
  "casa-duplex": "Casa dúplex",
  "casa-en-condominio": "Casa en condominio",
  departamento: "Departamento",
  penthouse: "Penthouse",
  cabana: "Cabaña",
  terreno: "Terreno",
  huerta: "Huerta",
  quinta: "Quinta",
  rancho: "Rancho",
  hacienda: "Hacienda",
  edificio: "Edificio",
  local: "Local",
  oficinas: "Oficinas",
  oficina: "Oficina",
  bodega: "Bodega",
  nave: "Nave industrial",
  fabrica: "Fábrica",
  fraccionamiento: "Fraccionamiento",
  escuela: "Escuela",
  "inmueble-productivo": "Inmueble productivo",
};

export function subtypeLabel(code: string | null | undefined): string {
  if (!code) return "Propiedad";
  return subtypeLabels[code] ?? code.charAt(0).toUpperCase() + code.slice(1).replace(/-/g, " ");
}

export const listingStatusLabels: Record<ListingStatus, string> = {
  enPromocion: "En promoción",
  suspendida: "Suspendida",
  cancelada: "Cancelada",
  conIntencion: "Con intención",
  conContratoFirmado: "Con contrato firmado",
  cerrada: "Cerrada",
};

export const listingStatusTones: Record<ListingStatus, Tone> = {
  enPromocion: "success",
  suspendida: "warning",
  cancelada: "danger",
  conIntencion: "info",
  conContratoFirmado: "gold",
  cerrada: "obsessed",
};

/** Estatus que se pueden poner desde Genera (el cierre se registra en 21Online). */
export const editableStatuses: ListingStatus[] = ["enPromocion", "suspendida", "conIntencion", "conContratoFirmado", "cancelada"];

export const syncLabels: Record<SyncStatus, string> = {
  PENDING: "Pendiente de enviar",
  DONE: "En 21Online",
  FAILED: "No se envió",
  NOT_REQUIRED: "Sin envío",
};

export const syncTones: Record<SyncStatus, Tone> = {
  PENDING: "warning",
  DONE: "success",
  FAILED: "danger",
  NOT_REQUIRED: "neutral",
};

export const contractLabels: Record<string, string> = { exclusiva: "Exclusiva", opcion: "Opción" };

/** Estados de la República para el formulario (21Online los guarda como texto). */
export const mexicanStates = [
  "Aguascalientes", "Baja California", "Baja California Sur", "Campeche", "Chiapas", "Chihuahua", "Ciudad de México", "Coahuila", "Colima", "Durango",
  "Estado de México", "Guanajuato", "Guerrero", "Hidalgo", "Jalisco", "Michoacán", "Morelos", "Nayarit", "Nuevo León", "Oaxaca", "Puebla", "Querétaro",
  "Quintana Roo", "San Luis Potosí", "Sinaloa", "Sonora", "Tabasco", "Tamaulipas", "Tlaxcala", "Veracruz", "Yucatán", "Zacatecas",
];

export function formatAmount(amount: number | null | undefined, currency = "MXN"): string {
  if (amount === null || amount === undefined) return "Precio no capturado";
  return new Intl.NumberFormat("es-MX", { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);
}

export function priceOf(p: { operation: string | null; salePrice: number | null; rentPrice: number | null; currency: string }): string {
  const amount = p.operation === "renta" ? p.rentPrice : p.salePrice;
  const text = formatAmount(amount, p.currency || "MXN");
  return p.operation === "renta" && amount !== null ? `${text} /mes` : text;
}
