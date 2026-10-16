/**
 * Control de Cambios #22: servicios con los que cuenta el inmueble, como casillas agrupadas. Se
 * guardan como texto legible ("Agua potable, Gas natural. Otros servicios: cisterna") en el mismo
 * campo de siempre, así el contrato (Anexo A) y el resumen los muestran sin cambios. Lo que no
 * corresponde a una casilla (capturas anteriores en texto libre) pasa a "Otros servicios".
 */
export const PROPERTY_SERVICE_GROUPS: { title: string; items: string[] }[] = [
  { title: "Servicios básicos", items: ["Agua potable", "Luz / Energía eléctrica", "Gas natural", "Gas LP (tanque / cilindro)"] },
  { title: "Comunicaciones", items: ["Telefonía fija", "Telefonía móvil (cobertura)", "Internet / Fibra óptica", "Cable / TV de paga"] },
  {
    title: "Servicios urbanos / Infraestructura",
    items: ["Drenaje / Alcantarillado", "Recolección de basura", "Seguridad / Vigilancia privada", "Estacionamiento", "Áreas verdes / Jardín"],
  },
  {
    title: "Amenidades (condominios / fraccionamientos)",
    items: [
      "Alberca / Piscina",
      "Gimnasio",
      "Acceso controlado / Portón eléctrico",
      "Salón de usos múltiples",
      "Intercomunicador",
      "CCTV / Cámaras de seguridad",
      "Planta de emergencia / Generador",
      "Paneles solares",
    ],
  },
];

export const PROPERTY_SERVICES = PROPERTY_SERVICE_GROUPS.flatMap((g) => g.items);

const OTHERS_PREFIX = "Otros servicios:";

const normalize = (v: string) =>
  v
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();

export interface ServicesSelection {
  selected: string[];
  others: string;
}

export function parseServices(value: string | null | undefined): ServicesSelection {
  if (!value || !value.trim()) return { selected: [], others: "" };
  let text = value.trim();
  let others = "";
  const idx = text.indexOf(OTHERS_PREFIX);
  if (idx >= 0) {
    others = text.slice(idx + OTHERS_PREFIX.length).trim().replace(/\.$/, "");
    text = text.slice(0, idx).trim().replace(/\.$/, "");
  }
  const byKey = new Map(PROPERTY_SERVICES.map((s) => [normalize(s), s]));
  const selected: string[] = [];
  const leftovers: string[] = [];
  for (const part of text.split(",").map((p) => p.trim()).filter(Boolean)) {
    const match = byKey.get(normalize(part));
    if (match) {
      if (!selected.includes(match)) selected.push(match);
    } else {
      leftovers.push(part);
    }
  }
  const allOthers = [...leftovers, ...(others ? [others] : [])].join(", ");
  return { selected: PROPERTY_SERVICES.filter((s) => selected.includes(s)), others: allOthers };
}

export function formatServices({ selected, others }: ServicesSelection): string {
  const ordered = PROPERTY_SERVICES.filter((s) => selected.includes(s));
  const parts: string[] = [];
  if (ordered.length > 0) parts.push(ordered.join(", "));
  if (others.trim()) parts.push(`${OTHERS_PREFIX} ${others.trim()}`);
  return parts.join(". ");
}
