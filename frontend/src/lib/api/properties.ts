import { apiClient } from "@/lib/api/client";

// Propiedades del CRM vinculadas con 21Online. Contrato del backend:
// leads.web.InternalPropertyController. El inventario se consulta en vivo en
// 21Online; lo que se da de alta aquí se publica allá (y se reintenta si falla).

/** Códigos tal cual los usa la API de 21Online. */
export type Operation = "venta" | "renta";
export type PropertyType = "habitacional" | "comercial";
export type ListingStatus = "enPromocion" | "suspendida" | "cancelada" | "conIntencion" | "conContratoFirmado" | "cerrada";
export type SyncStatus = "PENDING" | "DONE" | "FAILED" | "NOT_REQUIRED";

export interface Person {
  id: string;
  name: string;
}

export interface Listing {
  id: number;
  key: string;
  internalKey: string | null;
  status: ListingStatus | null;
  operation: Operation | null;
  propertyType: string | null;
  subtype: string | null;
  subtypeLabel: string;
  contractType: string | null;
  title: string;
  salePrice: number | null;
  rentPrice: number | null;
  currency: string;
  street: string | null;
  number: string | null;
  neighborhood: string | null;
  municipality: string | null;
  state: string | null;
  postalCode: string | null;
  bedrooms: number | null;
  bathrooms: number | null;
  halfBathrooms: number | null;
  parking: number | null;
  landM2: number | null;
  builtM2: number | null;
  description: string | null;
  publishedOnline: boolean | null;
  advisorOnline21Id: number | null;
  photoUrl: string | null;
  photoCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface InventoryItem {
  listing: Listing;
  /** Asesor de Genera vinculado al usuario de 21Online responsable. */
  advisor: Person | null;
  /** Id en Genera si la propiedad se capturó aquí. */
  generaId: string | null;
}

export interface InventoryPage {
  configured: boolean;
  error: string | null;
  items: InventoryItem[];
  page: number;
  totalItems: number | null;
  hasNext: boolean;
}

export interface InventoryQuery {
  q?: string;
  status?: string;
  operation?: string;
  subtype?: string;
  municipality?: string;
  page?: number;
}

export interface PropertyData {
  operation: Operation;
  propertyType: PropertyType;
  subtype: string;
  contractType: string | null;
  status: ListingStatus;
  headline: string;
  description: string | null;
  salePrice: number | null;
  rentPrice: number | null;
  currency: "MXN" | "USD";
  hidePrice: boolean;
  street: string | null;
  extNumber: string | null;
  neighborhood: string;
  municipality: string;
  state: string;
  postalCode: string | null;
  hideStreet: boolean;
  hideNumber: boolean;
  bedrooms: number | null;
  bathrooms: number | null;
  halfBathrooms: number | null;
  parking: number | null;
  landM2: number | null;
  builtM2: number | null;
  levels: number | null;
  age: number | null;
  maintenanceFee: number | null;
  commission: number | null;
  publishOnline: boolean;
  advisorUserId: string | null;
  /** Control de Cambios #26: subcategoría del catálogo (de ella salen propertyType/subtype de 21Online). */
  catalogSubtypeId: string | null;
  propertyTypeDetail: string | null;
}

export interface CapturedProperty {
  id: string;
  online21Id: number | null;
  syncStatus: SyncStatus;
  syncError: string | null;
  /** 21Online no respondió: pudo haberse guardado allá; revisar antes de reintentar. */
  syncUncertain: boolean;
  syncedAt: string | null;
  data: PropertyData;
  advisor: Person | null;
  /** El asesor tiene vinculado su usuario de 21Online (si no, 21Online la asigna al dueño de la llave). */
  advisorLinked: boolean;
  createdBy: Person;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  /** "Habitacional → Casa en condominio" (null si se capturó antes del catálogo). */
  propertyTypeLabel: string | null;
}

export interface CapturedOverview {
  configured: boolean;
  captured: CapturedProperty[];
}

function query(params: object): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export function listInventory(params: InventoryQuery) {
  return apiClient.get<InventoryPage>(`/internal/properties/inventory${query(params)}`);
}

export function getInventoryItem(online21Id: number | string) {
  return apiClient.get<InventoryItem>(`/internal/properties/inventory/${online21Id}`);
}

export function listCaptured() {
  return apiClient.get<CapturedOverview>("/internal/properties");
}

export function getCaptured(id: string) {
  return apiClient.get<CapturedProperty>(`/internal/properties/${id}`);
}

export function createProperty(data: PropertyData) {
  return apiClient.post<CapturedProperty>("/internal/properties", data);
}

export function updateProperty(id: string, data: PropertyData) {
  return apiClient.put<CapturedProperty>(`/internal/properties/${id}`, data);
}

export function retryPropertySync(id: string) {
  return apiClient.post<CapturedProperty>(`/internal/properties/${id}/sync`);
}

// --- Detalle de 21Online (como su pantalla "Ver propiedad") ------------------

export interface DossierField {
  section: string;
  label: string;
  value: string;
}

export interface DossierAdvisor {
  online21Id: number | null;
  name: string | null;
  email: string | null;
  mobile: string | null;
  officePhone: string | null;
  photoUrl: string | null;
}

export interface PropertyDetails {
  details: {
    listing: Listing;
    fields: DossierField[];
    photos: string[];
    floorPlans: string[];
    videoUrl: string | null;
    headlineEnglish: string | null;
    descriptionEnglish: string | null;
    latitude: number | null;
    longitude: number | null;
    /** Ficha pública en el sitio de CENTURY 21. */
    publicUrl: string | null;
    advisor: DossierAdvisor | null;
  };
  generaAdvisor: Person | null;
}

export interface PortalListing {
  portal: string;
  active: boolean;
  status: string | null;
  /** Liga a la publicación en el portal, si 21Online la trae. */
  url: string | null;
  reference: string | null;
  withError: boolean;
  error: string | null;
  updatedAt: string | null;
}

export interface RelatedClient {
  id: number | null;
  /** Nombre, teléfono, correo y mensaje solo vienen si contactsVisible. */
  name: string | null;
  phone: string | null;
  email: string | null;
  source: string | null;
  status: string | null;
  type: string | null;
  message: string | null;
  createdAt: string | null;
}

export interface RelatedClients {
  contactsVisible: boolean;
  clients: RelatedClient[];
}

export interface FollowUp {
  id: number | null;
  type: string | null;
  subtype: string | null;
  advisor: string | null;
  client: string | null;
  createdAt: string | null;
}

export type SheetVariant = "ADVISOR" | "NO_CONTACT" | "NO_LOGO";

export function getPropertyDetails(online21Id: number | string) {
  return apiClient.get<PropertyDetails>(`/internal/properties/inventory/${online21Id}/details`);
}

export function getPropertyPortals(online21Id: number | string) {
  return apiClient.get<PortalListing[]>(`/internal/properties/inventory/${online21Id}/portals`);
}

export function getPropertyClients(online21Id: number | string) {
  return apiClient.get<RelatedClients>(`/internal/properties/inventory/${online21Id}/clients`);
}

export function getPropertyFollowUps(online21Id: number | string) {
  return apiClient.get<FollowUp[]>(`/internal/properties/inventory/${online21Id}/follow-ups`);
}

/** Ruta (relativa a la API) de la ficha técnica en PDF; se descarga con getBlob. */
export function propertySheetPath(online21Id: number | string, variant: SheetVariant) {
  return `/internal/properties/inventory/${online21Id}/sheet?variant=${variant}`;
}
