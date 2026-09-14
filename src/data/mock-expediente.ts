import type {
  CalculatedDataItem,
  Expediente,
  ExpedienteConfig,
  ExtractedField,
  ReceivedDocument,
} from "@/types/expediente";

export const defaultExpedienteConfig: ExpedienteConfig = {
  contractType: "Compraventa",
  ownerCount: 1,
  accreditation: "Escritura pública",
  personType: "Persona física",
  legalStatus: "Libre de gravamen",
  condominiumRegime: false,
  signedByRepresentative: false,
};

export const mockExpedientes: Expediente[] = [
  {
    id: "exp-001",
    folio: "EXP-2026-001",
    ownerName: "Juan Pérez López",
    contractType: "Compraventa",
    status: "Documentos recibidos",
    updatedAt: "Actualizado hoy",
  },
  {
    id: "exp-002",
    folio: "EXP-2026-002",
    ownerName: "María Fernanda Ruiz",
    contractType: "Compraventa",
    status: "Esperando documentos",
    updatedAt: "Actualizado ayer",
  },
  {
    id: "exp-003",
    folio: "EXP-2026-003",
    ownerName: "Carlos Ramírez",
    contractType: "Compraventa",
    status: "En revisión",
    updatedAt: "12 sep 2026",
  },
  {
    id: "exp-004",
    folio: "EXP-2026-004",
    ownerName: "Lucía Hernández Soto",
    contractType: "Arrendamiento",
    status: "Borrador",
    updatedAt: "10 sep 2026",
  },
  {
    id: "exp-005",
    folio: "EXP-2026-005",
    ownerName: "Roberto Álvarez Mena",
    contractType: "Compraventa",
    status: "Listo para contrato",
    updatedAt: "08 sep 2026",
  },
];

export const receivedDocuments: ReceivedDocument[] = [
  { id: "ine1", name: "INE del propietario 1", receivedAt: "14 sep 2026", status: "Validado" },
  {
    id: "fiscal",
    name: "Constancia de situación fiscal",
    receivedAt: "14 sep 2026",
    status: "Convertido a PDF",
  },
  { id: "escritura", name: "Escritura completa", receivedAt: "14 sep 2026", status: "Validado" },
  {
    id: "domicilio",
    name: "Comprobante de domicilio",
    receivedAt: "14 sep 2026",
    status: "Convertido a PDF",
  },
  {
    id: "gravamen",
    name: "Certificado de gravamen",
    receivedAt: "14 sep 2026",
    status: "Validado",
  },
  { id: "predial", name: "Predial vigente", receivedAt: "14 sep 2026", status: "Validado" },
];

export const ownerFields: ExtractedField[] = [
  { label: "Nombre completo", value: "Juan Pérez López", source: "auto" },
  { label: "Fecha de nacimiento", value: "12/03/1980", source: "auto" },
  { label: "Estado civil", value: "Casado", source: "auto" },
  { label: "Nacionalidad", value: "Mexicana", source: "auto" },
  { label: "RFC", value: "PELJ800312ABC", source: "auto" },
  {
    label: "Domicilio",
    value: "Av. Insurgentes Sur 1234, Col. Del Valle, Benito Juárez, CDMX",
    source: "auto",
  },
];

export const ownerManualFields: ExtractedField[] = [
  { label: "Email", value: "juan.perez@email.com", source: "manual" },
  { label: "Teléfono", value: "55 1234 5678", source: "manual" },
  { label: "Porcentaje", value: "100", source: "manual", suffix: "%" },
];

export const propertyFields: ExtractedField[] = [
  {
    label: "Domicilio del inmueble",
    value: "Calle Roble 456, Col. Nápoles, Benito Juárez, CDMX",
    source: "auto",
  },
  { label: "Tipo de inmueble", value: "Casa habitación", source: "auto" },
  { label: "Superficie de terreno", value: "240 m²", source: "auto" },
  { label: "Superficie de construcción", value: "212 m²", source: "auto" },
  { label: "Número de escritura", value: "12,345", source: "auto" },
  { label: "Fecha de escritura", value: "15/06/2020", source: "auto" },
  { label: "Notario", value: "Lic. María González Ruiz", source: "auto" },
  { label: "Número de notaría", value: "98", source: "auto" },
  { label: "Folio real / Registro Público", value: "09012345", source: "auto" },
  { label: "Estatus jurídico", value: "Libre de gravamen", source: "auto" },
];

export const calculatedData: CalculatedDataItem[] = [
  { label: "Precio autorizado", value: "$6,000,000 MXN" },
  { label: "Comisión 5%", value: "$300,000 MXN" },
  { label: "IVA de comisión", value: "$48,000 MXN" },
  { label: "Total comisión + IVA", value: "$348,000 MXN" },
  { label: "Vigencia de exclusividad", value: "180 días" },
  { label: "Fecha de terminación", value: "13 marzo 2027" },
];

export const contractMockText = `En la Ciudad de México, a 14 de septiembre de 2026, comparecen por una parte el C. Juan Pérez López, a quien en lo sucesivo se le denominará "EL PROPIETARIO", y por la otra Century 21, a quien en lo sucesivo se le denominará "LA INMOBILIARIA", al tenor de las siguientes declaraciones y cláusulas.

DECLARACIONES

I. Declara "EL PROPIETARIO" que es legítimo dueño del inmueble ubicado en Calle Roble 456, Col. Nápoles, Benito Juárez, CDMX, según consta en la escritura pública número 12,345 de fecha 15 de junio de 2020, otorgada ante la fe del Lic. María González Ruiz, Notario Público número 98.

II. Declara "EL PROPIETARIO" que el inmueble se encuentra libre de todo gravamen, limitación de dominio o afectación registral, según certificado de gravamen vigente.

III. Declara "LA INMOBILIARIA" contar con la capacidad y autorización necesarias para fungir como intermediaria en la operación de compraventa referida en el presente instrumento.

CLÁUSULAS

PRIMERA. Objeto. "EL PROPIETARIO" otorga a "LA INMOBILIARIA" la exclusividad para la promoción y venta del inmueble descrito en las declaraciones, por un precio autorizado de $6,000,000.00 (seis millones de pesos 00/100 M.N.).

SEGUNDA. Comisión. En caso de concretarse la operación, "EL PROPIETARIO" cubrirá a "LA INMOBILIARIA" una comisión equivalente al 5% sobre el valor de venta, más el Impuesto al Valor Agregado correspondiente.

TERCERA. Vigencia. El presente contrato tendrá una vigencia de 180 días naturales contados a partir de la fecha de firma, pudiendo darse por terminado de manera anticipada por mutuo acuerdo de las partes.

CUARTA. Obligaciones del propietario. "EL PROPIETARIO" se obliga a proporcionar la documentación necesaria y a facilitar el acceso al inmueble para efectos de promoción, visitas y trámites relacionados con la operación.

QUINTA. Confidencialidad. Ambas partes se obligan a mantener confidencialidad respecto de la información intercambiada con motivo del presente contrato.

Leídas que fueron las cláusulas del presente contrato y enteradas las partes de su contenido y alcance legal, lo firman de conformidad en la Ciudad de México, en la fecha señalada al inicio de este documento.`;
