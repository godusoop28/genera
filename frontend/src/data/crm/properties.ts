import type { CrmAdvisor, CrmProperty } from "@/types/crm";

// Muestra del inventario "En Promoción" de 21Online (plus.21onlinemx.com,
// oficina 649) consultado el 11/10/2026. El repositorio es público: aquí solo
// van calle sin número, colonia y datos del anuncio; nunca dueños, números
// exteriores ni claves catastrales. Cuando exista la integración, esta lista
// vendrá del backend, que la leerá de la API REST de 21Online
// (/api/propiedades, autenticación JWT; documentación en /api/docs.html).

export const ONLINE21_BASE_URL = "https://plus.21onlinemx.com";

export const crmAdvisors: CrmAdvisor[] = [
  { id: "adv-aivy", name: "Aivy Genera", active: true, weight: 10, dailyLimit: null, shift: "09:00–19:00" },
  { id: "adv-gcruz", name: "Guadalupe Cruz Molina", active: true, weight: 8, dailyLimit: 6, shift: "09:00–18:00" },
  { id: "adv-joropeza", name: "Jannet Oropeza Garcia", active: true, weight: 6, dailyLimit: 5, shift: "10:00–19:00" },
  { id: "adv-ealanis", name: "Eva Alanis", active: true, weight: 6, dailyLimit: 5, shift: "09:00–17:00" },
  { id: "adv-garanda", name: "Gabriela Aranda Vazquez", active: true, weight: 8, dailyLimit: 6, shift: "09:00–19:00" },
  { id: "adv-opineda", name: "Orquidea Pineda", active: true, weight: 5, dailyLimit: 4, shift: "11:00–19:00" },
  { id: "adv-pmancilla", name: "Paulina Mancilla Hernandez", active: true, weight: 5, dailyLimit: 4, shift: "09:00–15:00" },
  { id: "adv-mcastilleja", name: "Mariana Castilleja Flores", active: true, weight: 5, dailyLimit: 4, shift: "15:00–21:00" },
  { id: "adv-cmarin", name: "Carmen Marín", active: true, weight: 7, dailyLimit: 5, shift: "09:00–19:00" },
  { id: "adv-mmagana", name: "Mar Magaña Gómez", active: false, weight: 5, dailyLimit: 4 },
  { id: "adv-iherrera", name: "María Isabel Herrera Morales", active: true, weight: 4, dailyLimit: 3, shift: "10:00–16:00" },
  { id: "adv-egarcia", name: "Eliut García Román", active: true, weight: 4, dailyLimit: 3, shift: "12:00–20:00" },
];

export const crmProperties: CrmProperty[] = [
  {
    id: 634294,
    key: "634294--649",
    internalKey: "GN 14",
    street: "Retorno de Fátima",
    neighborhood: "Burgos",
    municipality: "Temixco",
    state: "Morelos",
    subtype: "Casa",
    operation: "venta",
    price: 5_980_000,
    currency: "MXN",
    advisorId: "adv-aivy",
    updatedAt: "2026-10-08",
    details: {
      landM2: 430,
      builtM2: 330,
      bedrooms: 4,
      bathrooms: 4,
      parking: 2,
      acceptsCredit: true,
      omniMlsId: "MX261251954",
      description:
        "Casa nueva al sur de Cuernavaca, en privada con acceso controlado y vigilancia 24 h. Terreno de 430 m² y 330 m² de construcción: cuatro recámaras (una en planta baja), cuatro baños completos, cuarto de servicio, bodega y estacionamiento para dos autos. Alberca privada y cisterna de unos 8,000 litros.",
      features: ["Alberca", "Jardín", "Cuarto de servicio", "Terraza", "Cisterna", "Vigilancia privada", "Construcción nueva"],
    },
  },
  { id: 634037, key: "634037--649", internalKey: "GM 33", street: "Tezontepec", neighborhood: "Centro Jiutepec", municipality: "Jiutepec", state: "Morelos", subtype: "Casa", operation: "renta", price: 12_500, currency: "MXN", advisorId: "adv-gcruz", updatedAt: "2026-10-09" },
  { id: 633844, key: "633844--649", internalKey: "GM 32", street: "Nueva Cataluña y Nueva Italia", neighborhood: "Del Empleado", municipality: "Cuernavaca", state: "Morelos", subtype: "Local", operation: "venta", price: 7_500_000, currency: "MXN", advisorId: "adv-gcruz", updatedAt: "2026-10-05" },
  { id: 633748, key: "633748--649", internalKey: "GM 18", street: "Pino Ciprés", neighborhood: "Paraje Alarcón", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "venta", price: 3_000_000, currency: "MXN", advisorId: "adv-aivy", updatedAt: "2026-10-02" },
  { id: 633123, key: "633123--649", internalKey: "GN 10", street: "Cerezo", neighborhood: "Xalpa", municipality: "Yecapixtla", state: "Morelos", subtype: "Casa en condominio", operation: "venta", price: 2_000_000, currency: "MXN", advisorId: "adv-joropeza", updatedAt: "2026-10-03" },
  { id: 634390, key: "634390--649", internalKey: "EA 14", street: "Francisco I. Madero", neighborhood: "Lomas de Cortés", municipality: "Cuernavaca", state: "Morelos", subtype: "Terreno", operation: "venta", price: 1_530_000, currency: "MXN", advisorId: "adv-ealanis", updatedAt: "2026-10-10" },
  { id: 634365, key: "634365--649", internalKey: "EA 07", street: "Cerrada Galeana Primer C", neighborhood: "Acapantzingo", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "venta", price: 4_700_000, currency: "MXN", advisorId: "adv-ealanis", updatedAt: "2026-10-09" },
  { id: 634274, key: "634274--649", internalKey: "GA 12", street: "Humboldt", neighborhood: "Miguel Hidalgo", municipality: "Cuernavaca", state: "Morelos", subtype: "Departamento", operation: "renta", price: 7_500, currency: "MXN", advisorId: "adv-garanda", updatedAt: "2026-10-09" },
  { id: 634331, key: "634331--649", internalKey: "GA 08", street: "Palma Livistona", neighborhood: "Residencial La Palma", municipality: "Jiutepec", state: "Morelos", subtype: "Casa", operation: "venta", price: 3_290_000, currency: "MXN", advisorId: "adv-garanda", updatedAt: "2026-10-08" },
  { id: 631618, key: "631618--649", internalKey: "OP 02", street: "Olimpo", neighborhood: "Burgos Bugambilias", municipality: "Temixco", state: "Morelos", subtype: "Casa", operation: "venta", price: 7_885_500, currency: "MXN", advisorId: "adv-opineda", updatedAt: "2026-09-21" },
  { id: 631603, key: "631603--649", internalKey: "PM 05", street: "16 de Septiembre", neighborhood: "Poblado Acapatzingo", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa en condominio", operation: "venta", price: 5_550_000, currency: "MXN", advisorId: "adv-pmancilla", updatedAt: "2026-09-12" },
  { id: 631379, key: "631379--649", internalKey: "JO 05R", street: "Bugambilia", neighborhood: "Delicias", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "renta", price: 37_000, currency: "MXN", advisorId: "adv-joropeza", updatedAt: "2026-09-19" },
  { id: 632450, key: "632450--649", internalKey: "EA 02", street: "Priv. Marte", neighborhood: "Jardines de Cuernavaca", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "venta", price: 13_500_000, currency: "MXN", advisorId: "adv-ealanis", updatedAt: "2026-10-09" },
  { id: 632014, key: "632014--649", internalKey: "GA 02", street: "Libramiento al Diez", neighborhood: "Paraíso Country Club", municipality: "Emiliano Zapata", state: "Morelos", subtype: "Departamento", operation: "renta", price: 26_000, currency: "MXN", advisorId: "adv-garanda", updatedAt: "2026-09-17" },
  { id: 630981, key: "630981--649", internalKey: "GN 04", street: "Acacias", neighborhood: "Tlaltenango", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "venta", price: 12_500_000, currency: "MXN", advisorId: "adv-aivy", updatedAt: "2026-09-29" },
  { id: 626033, key: "626033--649", internalKey: "MC 01", street: "Eucalipto", neighborhood: "Fracc. San Jerónimo de Ahuatepec", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "venta", price: 7_450_000, currency: "MXN", advisorId: "adv-mcastilleja", updatedAt: "2026-08-01" },
  { id: 614064, key: "614064--649", internalKey: "CW 214", street: "El Vergel", neighborhood: "Chipitlán", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "venta", price: 13_900_000, currency: "MXN", advisorId: "adv-cmarin", updatedAt: "2026-08-01" },
  { id: 609011, key: "609011--649", internalKey: "CW 01", street: "Circuito del Lago", neighborhood: "Sumiya", municipality: "Jiutepec", state: "Morelos", subtype: "Casa", operation: "venta", price: 46_500_000, currency: "MXN", advisorId: "adv-cmarin", updatedAt: "2026-08-01" },
  { id: 626153, key: "626153--649", internalKey: "IH 05", street: "Retorno Lerdo de Tejada", neighborhood: "Altavista", municipality: "Cuernavaca", state: "Morelos", subtype: "Casa", operation: "venta", price: 3_900_000, currency: "MXN", advisorId: "adv-iherrera", updatedAt: "2026-08-05" },
  { id: 623767, key: "623767--649", internalKey: "EG 05", street: "Oporto", neighborhood: "Tetecalita", municipality: "Emiliano Zapata", state: "Morelos", subtype: "Casa en condominio", operation: "venta", price: 1_250_000, currency: "MXN", advisorId: "adv-egarcia", updatedAt: "2026-08-11" },
];
